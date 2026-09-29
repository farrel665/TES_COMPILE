#ifndef REAPER_H
#define REAPER_H

#include <pthread.h>
#include "touch_io.h"

typedef struct {
    TouchCapture   *capture;
    TouchInject    *inject;
    TouchEngine  *engine;
    pthread_mutex_t lock;
    pthread_t       thread;
    volatile int    running;
} RizxbyteReaper;

int  ancore_reaper_start(RizxbyteReaper *rp,
                           TouchCapture *tc,
                           TouchInject *ti,
                           TouchEngine *ts);
void ancore_reaper_stop(RizxbyteReaper *rp);
void ancore_reaper_lock(RizxbyteReaper *rp);
void ancore_reaper_unlock(RizxbyteReaper *rp);

#endif