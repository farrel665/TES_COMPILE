// worker_main.c
#include <stdio.h>
#include <stdbool.h>
#include <stdint.h>
#include <unistd.h>
#include <signal.h>
#include <string.h>
#include <time.h>
#include <dirent.h>
#include <ctype.h>
#include <stdlib.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <linux/input.h>

#include "touch.h"
#include "process_watcher.h"
#include "touch_io.h"
#include "reaper.h"

#define ANCORE_POLL_INTERVAL_MS  500
#define ANCORE_WARMUP_SECONDS    0
#define ANCORE_FRAME_DT          (1.0f / 120.0f)
#define ANCORE_WATCHDOG_SECONDS  8

static void ancore_panic(int sig) { (void)sig; _exit(0); }

static void ancore_install_signals(void) {
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = ancore_panic;
    sigaction(SIGINT, &sa, NULL);
    sigaction(SIGTERM, &sa, NULL);
    sigaction(SIGHUP, &sa, NULL);
    sigaction(SIGQUIT, &sa, NULL);
    sigaction(SIGALRM, &sa, NULL);
    signal(SIGPIPE, SIG_IGN);
}

static void force_release_grabs(void) {
    char path[64];
    for (int i = 0; i < 32; i++) {
        snprintf(path, sizeof(path), "/dev/input/event%d", i);
        int fd = open(path, O_RDONLY | O_NONBLOCK);
        if (fd < 0) continue;
        ioctl(fd, EVIOCGRAB, 0);
        close(fd);
    }
}

static void ancore_kill_stale(void) {
    DIR *proc = opendir("/proc");
    if (!proc) return;
    pid_t self = getpid();
    pid_t parent = getppid();
    struct dirent *entry;
    char path[64], buf[256];
    int killed = 0;

    while ((entry = readdir(proc)) != NULL) {
        if (!isdigit((unsigned char)entry->d_name[0])) continue;
        pid_t pid = (pid_t)atoi(entry->d_name);
        if (pid <= 1 || pid == self || pid == parent) continue;

        snprintf(path, sizeof(path), "/proc/%d/cmdline", pid);
        FILE *f = fopen(path, "r");
        if (!f) continue;
        size_t n = fread(buf, 1, sizeof(buf) - 1, f);
        fclose(f);
        if (n == 0) continue;
        buf[n] = '\0';

        if (strstr(buf, "ancore_engine") != NULL || strstr(buf, "input_ancore") != NULL) {
            fprintf(stderr, "[ancore] kill stale pid=%d\n", (int)pid);
            kill(pid, SIGKILL);
            killed++;
            usleep(10000);
        }
    }
    closedir(proc);
    if (killed) {
        usleep(30000);
        fprintf(stderr, "[ancore] purged %d stale\n", killed);
    }
}

static void disengage(TouchCapture *c, TouchInject *inj, TouchEngine *s) {
    for (int slot = 0; slot < TS_MAX_SLOTS; slot++) {
        if (inj->virtual_active[slot] || inj->last_active[slot])
            ancore_inject_slot_release(inj, slot);
        touch_force_release(s, slot);
        c->slots[slot].active = 0;
        c->slots[slot].tracking_id = -1;
    }
    inj->dirty = 1;
    ancore_inject_flush(inj);
    ancore_capture_ungrab(c);
    c->cur_slot = 0;
}

int main(int argc, char **argv) {
    int preset_idx = 0; 
    float cli_sens_x = -1.f;
    float cli_sens_y = -1.f;
    int cli_area = 1;       
    int cli_tactix = 0;
    float cli_deadzone = 1.5f;
    float cli_flick_speed = 18.0f;
    float cli_flick_boost = 1.55f;
    for (int i = 1; i < argc; i++) {
        if (!strcmp(argv[i], "--preset") && i + 1 < argc)
            preset_idx = atoi(argv[++i]);
        else if (!strcmp(argv[i], "--sens-x") && i + 1 < argc)
            cli_sens_x = (float)atof(argv[++i]);
        else if (!strcmp(argv[i], "--sens-y") && i + 1 < argc)
            cli_sens_y = (float)atof(argv[++i]);
        else if (!strcmp(argv[i], "--area") && i + 1 < argc)
            cli_area = atoi(argv[++i]);
        else if (!strcmp(argv[i], "--tactix") && i + 1 < argc)
            cli_tactix = atoi(argv[++i]);
        else if (!strcmp(argv[i], "--deadzone") && i + 1 < argc)
            cli_deadzone = (float)atof(argv[++i]);
        else if (!strcmp(argv[i], "--flick-speed") && i + 1 < argc)
            cli_flick_speed = (float)atof(argv[++i]);
        else if (!strcmp(argv[i], "--flick-boost") && i + 1 < argc)
            cli_flick_boost = (float)atof(argv[++i]);
    }
    if (preset_idx < 0) preset_idx = 0;
    if (preset_idx > 2) preset_idx = 2;
    if (cli_area < 0) cli_area = 0;
    if (cli_area > 2) cli_area = 2;

    static const char *k_area[3]   = { "LEFT", "ALL", "RIGHT" };
    float factor = 0.90f;
    float sens_x = (cli_sens_x > 0.f) ? cli_sens_x : 2.0f;
    float sens_y = (cli_sens_y > 0.f) ? cli_sens_y : 2.0f;
    const char *name = "FLAT";

    setvbuf(stderr, NULL, _IONBF, 0);
    setvbuf(stdout, NULL, _IONBF, 0);

    fprintf(stderr, "[ancore] boot pid=%d ppid=%d curve=%s\n",
            (int)getpid(), (int)getppid(), name);
    fprintf(stderr, "[ancore] argv:");
    for (int a = 0; a < argc; a++) fprintf(stderr, " %s", argv[a]);
    fprintf(stderr, "\n");
    fprintf(stderr, "[ancore] applied sensX=%.3f sensY=%.3f factor=%.3f\n",
            sens_x, sens_y, factor);

    ancore_kill_stale();
    force_release_grabs();
    ancore_install_signals();

    TouchEngine engine;
    TouchCapture capture;
    TouchInject inject;
    AncoreReaper reaper;

    fprintf(stderr, "[ancore] worker %s factor=%.2f sensX=%.2f sensY=%.2f area=%s tactix=%d\n",
            name, factor, sens_x, sens_y, k_area[cli_area], cli_tactix);

    if (ancore_capture_open(&capture) < 0) {
        fprintf(stderr, "[ancore] capture open failed (need root /dev/input)\n");
        return 1;
    }
    fprintf(stderr, "[ancore] capture ready x=[%d..%d] y=[%d..%d]\n",
            capture.abs_x_min, capture.abs_x_max,
            capture.abs_y_min, capture.abs_y_max);

    if (ancore_inject_open(&inject, capture.abs_x_min, capture.abs_x_max,
                             capture.abs_y_min, capture.abs_y_max) < 0) {
        fprintf(stderr, "[ancore] uinput open failed\n");
        ancore_capture_close(&capture);
        return 1;
    }
    fprintf(stderr, "[ancore] uinput ready\n");

    if (ancore_reaper_start(&reaper, &capture, &inject, &engine) < 0) {
        fprintf(stderr, "[ancore] reaper failed\n");
        ancore_inject_close(&inject);
        ancore_capture_close(&capture);
        return 1;
    }

    for (;;) {
        alarm(0);
        fprintf(stderr, "[ancore] waiting Free Fire...\n");
        const char *ff_name = NULL;
        pid_t ff_pid = ancore_pw_wait_for_freefire(ANCORE_POLL_INTERVAL_MS, &ff_name);
        fprintf(stderr, "[ancore] got %s pid=%d\n",
                ff_name ? ff_name : "ff", (int)ff_pid);

        int died = 0;
        for (int e = 0; e < ANCORE_WARMUP_SECONDS; e++) {
            alarm(ANCORE_WATCHDOG_SECONDS);
            if (!ancore_pw_is_alive(ff_pid)) { died = 1; break; }
            sleep(1);
        }
        if (died) {
            fprintf(stderr, "[ancore] died in warmup, retry\n");
            continue;
        }

        if (ancore_capture_grab(&capture) < 0) {
            fprintf(stderr, "[ancore] grab busy — stop other modules\n");
            force_release_grabs();
            if (ancore_capture_grab(&capture) < 0) {
                continue;
            }
        }
        while (ancore_capture_poll(&capture)) {}
        for (int i = 0; i < TS_MAX_SLOTS; i++) {
            capture.slots[i].active = 0;
            capture.slots[i].tracking_id = -1;
        }
        capture.cur_slot = 0;

        touch_init(&engine, factor, 1.0f);
        touch_set_sensitivity_xy(&engine, sens_x, sens_y);
        touch_set_region(&engine, cli_area,
            (float)capture.abs_x_min, (float)capture.abs_x_max,
            (float)capture.abs_y_min, (float)capture.abs_y_max);
        touch_set_tactix(&engine, cli_tactix,
            cli_deadzone, cli_flick_speed, cli_flick_boost);
        fprintf(stderr, "[ancore] ENGAGED %s sensX=%.2f sensY=%.2f area=%s tactix=%d dz=%.1f\n",
                name, sens_x, sens_y, k_area[cli_area], cli_tactix, cli_deadzone);

        while (ancore_pw_is_alive(ff_pid)) {
            alarm(ANCORE_WATCHDOG_SECONDS);
            ancore_reaper_lock(&reaper);
            ancore_capture_poll(&capture);
            for (int slot = 0; slot < TS_MAX_SLOTS; slot++) {
                float ox = 0, oy = 0;
                int od = 0;
                int hw = capture.slots[slot].active;
                touch_slot(&engine, slot,
                    (float)capture.slots[slot].x, (float)capture.slots[slot].y,
                    hw, ANCORE_FRAME_DT, &ox, &oy, &od);
                if (hw) ancore_inject_slot(&inject, slot, (int)ox, (int)oy, 1);
                else if (inject.virtual_active[slot])
                    ancore_inject_slot_release(&inject, slot);
            }
            ancore_inject_flush(&inject);
            ancore_reaper_unlock(&reaper);
            usleep((useconds_t)(ANCORE_FRAME_DT * 1000000.0f));
        }

        fprintf(stderr, "[ancore] FF closed, disengage\n");
        ancore_reaper_lock(&reaper);
        disengage(&capture, &inject, &engine);
        ancore_reaper_unlock(&reaper);
    }
    return 0;
}
