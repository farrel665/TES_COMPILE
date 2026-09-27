#include "process_watcher.h"
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <dirent.h>
#include <ctype.h>
#include <signal.h>

static int read_cmdline(pid_t pid, char *buf, size_t buflen) {
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/cmdline", pid);
    FILE *f = fopen(path, "r");
    if (!f) return -1;
    size_t n = fread(buf, 1, buflen - 1, f);
    fclose(f);
    buf[n] = '\0';
    return (int)n;
}

pid_t rizxbyte_pw_find_pid(const char *process_name) {
    DIR *proc = opendir("/proc");
    if (!proc) return -1;
    struct dirent *entry;
    pid_t found = -1;
    char cmdline[512];
    while ((entry = readdir(proc)) != NULL) {
        if (!isdigit((unsigned char)entry->d_name[0])) continue;
        pid_t pid = (pid_t)atoi(entry->d_name);
        if (read_cmdline(pid, cmdline, sizeof(cmdline)) <= 0) continue;
        if (strstr(cmdline, process_name) != NULL) {
            found = pid;
            break;
        }
    }
    closedir(proc);
    return found;
}

pid_t rizxbyte_pw_find_freefire(const char **out_name) {
    pid_t pid = rizxbyte_pw_find_pid(RIZXBYTE_FF_PACKAGE_GLOBAL);
    if (pid > 0) {
        if (out_name) *out_name = RIZXBYTE_FF_PACKAGE_GLOBAL;
        return pid;
    }
    pid = rizxbyte_pw_find_pid(RIZXBYTE_FF_PACKAGE_MAX);
    if (pid > 0) {
        if (out_name) *out_name = RIZXBYTE_FF_PACKAGE_MAX;
        return pid;
    }
    if (out_name) *out_name = NULL;
    return -1;
}

pid_t rizxbyte_pw_wait_for_freefire(int interval_ms, const char **out_name) {
    for (;;) {
        pid_t pid = rizxbyte_pw_find_freefire(out_name);
        if (pid > 0) return pid;
        usleep(interval_ms * 1000);
    }
}

pid_t rizxbyte_pw_wait_for_process(const char *process_name, int interval_ms) {
    for (;;) {
        pid_t pid = rizxbyte_pw_find_pid(process_name);
        if (pid > 0) return pid;
        usleep(interval_ms * 1000);
    }
}

bool rizxbyte_pw_is_alive(pid_t pid) {
    if (pid <= 0) return false;
    return kill(pid, 0) == 0;
}
