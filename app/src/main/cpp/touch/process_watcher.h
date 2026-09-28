// process_watcher.h
#ifndef PROCESS_WATCHER_H
#define PROCESS_WATCHER_H

#include <sys/types.h>
#include <stdbool.h>

#define ANCORE_FF_PACKAGE_GLOBAL "com.dts.freefireth"
#define ANCORE_FF_PACKAGE_MAX    "com.dts.freefiremax"

pid_t ancore_pw_find_pid(const char *process_name);
pid_t ancore_pw_find_freefire(const char **out_name);
pid_t ancore_pw_wait_for_freefire(int interval_ms, const char **out_name);
pid_t ancore_pw_wait_for_process(const char *process_name, int interval_ms);
bool  ancore_pw_is_alive(pid_t pid);

#endif
