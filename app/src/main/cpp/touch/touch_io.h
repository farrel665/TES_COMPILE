// touch_io.h
#ifndef TOUCH_IO_H
#define TOUCH_IO_H

#include <stdbool.h>
#include "touch.h"

typedef struct {
    int tracking_id;
    int x, y;
    int active;
} AncoreCaptureSlot;

typedef struct {
    int  fd;
    int  grabbed;
    int  abs_x_min, abs_x_max;
    int  abs_y_min, abs_y_max;
    int  cur_slot;   
    AncoreCaptureSlot slots[TS_MAX_SLOTS];
} TouchCapture;

typedef struct {
    int fd;
    int virtual_active[TS_MAX_SLOTS];
    int virtual_x[TS_MAX_SLOTS];
    int virtual_y[TS_MAX_SLOTS];
    int tracking_id[TS_MAX_SLOTS];
    int last_active[TS_MAX_SLOTS]; 
    int last_btn;
    int next_tracking_id;
    int dirty;
    int any_active;
} TouchInject;

int  ancore_capture_open(TouchCapture *tc);
void ancore_capture_close(TouchCapture *tc);
int  ancore_capture_grab(TouchCapture *tc);
void ancore_capture_ungrab(TouchCapture *tc);
bool ancore_capture_poll(TouchCapture *tc);

int  ancore_inject_open(TouchInject *ti, int abs_x_min, int abs_x_max,
                        int abs_y_min, int abs_y_max);
void ancore_inject_close(TouchInject *ti);

void ancore_inject_slot(TouchInject *ti, int slot, int x, int y, int down);
void ancore_inject_slot_release(TouchInject *ti, int slot);
void ancore_inject_flush(TouchInject *ti);

#endif
