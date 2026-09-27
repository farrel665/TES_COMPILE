#ifndef TOUCH_IO_H
#define TOUCH_IO_H

#include <stdbool.h>
#include "touch.h"

typedef struct {
    int tracking_id;
    int x, y;
    int active;
} RizxbyteCaptureSlot;

typedef struct {
    int  fd;
    int  grabbed;
    int  abs_x_min, abs_x_max;
    int  abs_y_min, abs_y_max;
    int  cur_slot;   
    RizxbyteCaptureSlot slots[TS_MAX_SLOTS];
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

int  rizxbyte_capture_open(TouchCapture *tc);
void rizxbyte_capture_close(TouchCapture *tc);
int  rizxbyte_capture_grab(TouchCapture *tc);
void rizxbyte_capture_ungrab(TouchCapture *tc);
bool rizxbyte_capture_poll(TouchCapture *tc);

int  rizxbyte_inject_open(TouchInject *ti, int abs_x_min, int abs_x_max,
                          int abs_y_min, int abs_y_max);
void rizxbyte_inject_close(TouchInject *ti);

void rizxbyte_inject_slot(TouchInject *ti, int slot, int x, int y, int down);
void rizxbyte_inject_slot_release(TouchInject *ti, int slot);
void rizxbyte_inject_flush(TouchInject *ti);

#endif
