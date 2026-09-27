#include "reaper.h"
#include <stdio.h>
#include <unistd.h>

#define RIZXBYTE_REAPER_INTERVAL_MS 250

static void rizxbyte_reaper_sync(RizxbyteReaper *rp) {
    int hw_active[TS_MAX_SLOTS];
    int purged = 0;

    for (int i = 0; i < TS_MAX_SLOTS; i++) {
        hw_active[i] = rp->capture->slots[i].active;
    }

    for (int slot = 0; slot < TS_MAX_SLOTS; slot++) {
        int virtual_alive = rp->inject->virtual_active[slot];

        if (virtual_alive && !hw_active[slot]) {
            rizxbyte_inject_slot_release(rp->inject, slot);
            touch_force_release(rp->engine, slot);
            fprintf(stderr, "[rizxbyte_reaper] ghost purged slot %d\n", slot);
            purged = 1;
        }
    }

    if (purged) {
        rizxbyte_inject_flush(rp->inject);
    }
}

static void *rizxbyte_reaper_thread(void *arg) {
    RizxbyteReaper *rp = (RizxbyteReaper *)arg;

    while (rp->running) {
        usleep(RIZXBYTE_REAPER_INTERVAL_MS * 1000);
        if (!rp->running) break;

        pthread_mutex_lock(&rp->lock);
        rizxbyte_reaper_sync(rp);
        pthread_mutex_unlock(&rp->lock);
    }

    return NULL;
}

int rizxbyte_reaper_start(RizxbyteReaper *rp,
                          TouchCapture *tc,
                          TouchInject *ti,
                          TouchEngine *ts) {
    if (rp == NULL || tc == NULL || ti == NULL || ts == NULL) return -1;

    rp->capture  = tc;
    rp->inject   = ti;
    rp->engine = ts;
    rp->running  = 1;

    if (pthread_mutex_init(&rp->lock, NULL) != 0) return -1;

    if (pthread_create(&rp->thread, NULL, rizxbyte_reaper_thread, rp) != 0) {
        pthread_mutex_destroy(&rp->lock);
        return -1;
    }

    return 0;
}

void rizxbyte_reaper_stop(RizxbyteReaper *rp) {
    if (rp == NULL || !rp->running) return;
    rp->running = 0;
    pthread_join(rp->thread, NULL);
    pthread_mutex_destroy(&rp->lock);
}

void rizxbyte_reaper_lock(RizxbyteReaper *rp) {
    if (rp != NULL) pthread_mutex_lock(&rp->lock);
}

void rizxbyte_reaper_unlock(RizxbyteReaper *rp) {
    if (rp != NULL) pthread_mutex_unlock(&rp->lock);
}
