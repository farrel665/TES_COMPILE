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

#include "touch.h"
#include "process_watcher.h"
#include "touch_io.h"
#include "reaper.h"

#define RIZXBYTE_POLL_INTERVAL_MS  500
#define RIZXBYTE_WARMUP_SECONDS    5
#define RIZXBYTE_FRAME_DT          (1.0f / 120.0f)
#define RIZXBYTE_WATCHDOG_SECONDS  5

static void rizxbyte_panic(int sig) {
    (void)sig;
    _exit(0);
}

static void rizxbyte_install_signals(void) {
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = rizxbyte_panic;
    sigaction(SIGINT,  &sa, NULL);
    sigaction(SIGTERM, &sa, NULL);
    sigaction(SIGHUP,  &sa, NULL);
    sigaction(SIGQUIT, &sa, NULL);
    sigaction(SIGALRM, &sa, NULL);
}

static void rizxbyte_kill_stale(void) {
    DIR *proc = opendir("/proc");
    if (!proc) return;

    pid_t self = getpid();
    struct dirent *entry;
    char path[64];
    char buf[256];

    while ((entry = readdir(proc)) != NULL) {
        if (!isdigit((unsigned char)entry->d_name[0])) continue;

        pid_t pid = (pid_t)atoi(entry->d_name);
        if (pid == self) continue;

        snprintf(path, sizeof(path), "/proc/%d/cmdline", pid);
        FILE *f = fopen(path, "r");
        if (!f) continue;

        size_t n = fread(buf, 1, sizeof(buf) - 1, f);
        fclose(f);
        buf[n] = '\0';

        if (strstr(buf, "rizxbyte_engine") != NULL) {
            fprintf(stderr, "[rizxbyte_engine] killing stale instance pid=%d\n", pid);
            kill(pid, SIGKILL);
            usleep(200000);
        }
    }

    closedir(proc);
}

int main(void) {
    rizxbyte_kill_stale();
    rizxbyte_install_signals();

    TouchEngine engine;
    TouchCapture capture;
    TouchInject  inject;
    RizxbyteReaper reaper;

    int preset_idx = rizxbyte_menu_run();
    if (preset_idx < 0) preset_idx = 0;
    if (preset_idx > 2) preset_idx = 2;
    static const float k_factor[3] = { 1.00f, 0.75f, 2.00f };
    static const float k_sens[3]   = { 2.00f, 4.50f, 7.50f };
    static const char *k_name[3]   = { "LINEAR", "ACCEL", "DECEL" };
    float factor = k_factor[preset_idx];
    float sens   = k_sens[preset_idx];
    const char *name = k_name[preset_idx];

    if (rizxbyte_capture_open(&capture) < 0) {
        fprintf(stderr, "[rizxbyte_engine] capture open failed (need root)\n");
        return 1;
    }
    printf("[rizxbyte_engine] capture ready. x=[%d..%d] y=[%d..%d]\n",
           capture.abs_x_min, capture.abs_x_max,
           capture.abs_y_min, capture.abs_y_max);

    if (rizxbyte_inject_open(&inject,
                             capture.abs_x_min, capture.abs_x_max,
                             capture.abs_y_min, capture.abs_y_max) < 0) {
        fprintf(stderr, "[rizxbyte_engine] uinput open failed\n");
        rizxbyte_capture_close(&capture);
        return 1;
    }
    printf("[rizxbyte_engine] virtual touch created.\n");

    if (rizxbyte_reaper_start(&reaper, &capture, &inject, &engine) < 0) {
        fprintf(stderr, "[rizxbyte_engine] reaper thread failed\n");
        rizxbyte_inject_close(&inject);
        rizxbyte_capture_close(&capture);
        return 1;
    }
    printf("[rizxbyte_engine] reaper thread started (250ms interval).\n");
    printf("[rizxbyte_engine] waiting for Free Fire...\n");

    alarm(RIZXBYTE_WATCHDOG_SECONDS * 4);

    pid_t ff_pid = rizxbyte_pw_wait_for_process(RIZXBYTE_FF_PACKAGE_GLOBAL,
                                                RIZXBYTE_POLL_INTERVAL_MS);
    printf("[rizxbyte_engine] launcher detected (pid=%d).\n", ff_pid);
    printf("[rizxbyte_engine] warming up %d seconds...\n", RIZXBYTE_WARMUP_SECONDS);

    for (int elapsed = 0; elapsed < RIZXBYTE_WARMUP_SECONDS; elapsed++) {
        alarm(RIZXBYTE_WATCHDOG_SECONDS);
        if (!rizxbyte_pw_is_alive(ff_pid)) {
            printf("[rizxbyte_engine] process died during warmup. restarting.\n");
            rizxbyte_reaper_stop(&reaper);
            rizxbyte_inject_close(&inject);
            rizxbyte_capture_close(&capture);
            return main();
        }
        sleep(1);
    }

    printf("[rizxbyte_engine] engaging with preset %s.\n", name);

    if (rizxbyte_capture_grab(&capture) < 0) {
        fprintf(stderr, "[rizxbyte_engine] grab failed, cannot engage\n");
        rizxbyte_reaper_stop(&reaper);
        rizxbyte_inject_close(&inject);
        rizxbyte_capture_close(&capture);
        return 1;
    }
    printf("[rizxbyte_engine] touch grabbed (passthrough off).\n");

    
    while (rizxbyte_capture_poll(&capture)) {  }
    for (int i = 0; i < TS_MAX_SLOTS; i++) {
        capture.slots[i].active = 0;
        capture.slots[i].tracking_id = -1;
    }
    capture.cur_slot = 0;

    touch_init(&engine, factor, 1.0f);
    touch_set_sensitivity(&engine, sens);

    while (rizxbyte_pw_is_alive(ff_pid)) {
        alarm(RIZXBYTE_WATCHDOG_SECONDS);

        rizxbyte_reaper_lock(&reaper);

        rizxbyte_capture_poll(&capture);

        for (int slot = 0; slot < TS_MAX_SLOTS; slot++) {
            float ox = 0.0f, oy = 0.0f;
            int   od = 0;

            int hw_active = capture.slots[slot].active;
            float in_x = (float)capture.slots[slot].x;
            float in_y = (float)capture.slots[slot].y;

            touch_slot(&engine, slot, in_x, in_y, hw_active,
                                RIZXBYTE_FRAME_DT, &ox, &oy, &od);

            if (hw_active) {
                rizxbyte_inject_slot(&inject, slot, (int)ox, (int)oy, 1);
            } else {
                if (inject.virtual_active[slot]) {
                    rizxbyte_inject_slot_release(&inject, slot);
                }
            }
        }

        rizxbyte_inject_flush(&inject);

        rizxbyte_reaper_unlock(&reaper);

        usleep((useconds_t)(RIZXBYTE_FRAME_DT * 1000000));
    }

    printf("[rizxbyte_engine] Free Fire closed. disengaging.\n");
    rizxbyte_reaper_stop(&reaper);
    rizxbyte_inject_close(&inject);
    rizxbyte_capture_close(&capture);
    return 0;
}