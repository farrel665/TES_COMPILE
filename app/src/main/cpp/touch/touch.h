#ifndef TOUCH_H
#define TOUCH_H

#include <math.h>
#include <stddef.h>

#define TS_MAX_SLOTS            10
#define TS_DEFAULT_FACTOR       0.18f
#define TS_DEFAULT_SENSITIVITY  2.2f

#define TS_FACTOR_MIN           0.01f
#define TS_FACTOR_MAX           1.0f
#define TS_SENSITIVITY_MIN      1.0f
#define TS_SENSITIVITY_MAX      5.0f

#define TS_AREA_LEFT            0
#define TS_AREA_ALL             1
#define TS_AREA_RIGHT           2

typedef struct {
    float x;
    float y;
    float last_raw_x;
    float last_raw_y;
    int   active;
    int   tracking_id;
    int   in_region;
    float filtered_dx;
    float filtered_dy;
} TS_Slot;

typedef struct {
    TS_Slot slots[TS_MAX_SLOTS];
    float   factor;
    float   dpi_scale;
    float   sensitivity;
    float   sens_x;
    float   sens_y;
    float   strength;
    float   responsiveness;

    int     area;           
    float   screen_w;
    float   screen_h;
    float   abs_x_min;
    float   abs_y_min;

    
    int     tactix;
    float   deadzone;       
    float   flick_speed;    
    float   flick_boost;    

    int     initialized;
} TouchEngine;

static float ts_clamp_float(float value, float min, float max) {
    if (value < min) return min;
    if (value > max) return max;
    return value;
}

static void touch_init(TouchEngine *s, float factor, float dpi_scale) {
    if (s == NULL) return;
    for (int i = 0; i < TS_MAX_SLOTS; i++) {
        s->slots[i].x = 0.0f;
        s->slots[i].y = 0.0f;
        s->slots[i].last_raw_x = 0.0f;
        s->slots[i].last_raw_y = 0.0f;
        s->slots[i].active = 0;
        s->slots[i].tracking_id = -1;
        s->slots[i].in_region = 0;
        s->slots[i].filtered_dx = 0.0f;
        s->slots[i].filtered_dy = 0.0f;
    }
    s->factor = ts_clamp_float(factor, TS_FACTOR_MIN, TS_FACTOR_MAX);
    s->dpi_scale = (dpi_scale > 0.0f) ? dpi_scale : 1.0f;
    s->sensitivity = TS_DEFAULT_SENSITIVITY;
    s->sens_x = TS_DEFAULT_SENSITIVITY;
    s->sens_y = TS_DEFAULT_SENSITIVITY;
    s->strength = 0.0f;
    s->responsiveness = 0.0f;
    s->area = TS_AREA_ALL;
    s->screen_w = 1080.0f;
    s->screen_h = 1920.0f;
    s->abs_x_min = 0.0f;
    s->abs_y_min = 0.0f;
    s->tactix = 0;
    s->deadzone = 0.8f;
    s->flick_speed = 0.0f;
    s->flick_boost = 1.0f;
    s->initialized = 0;
}

static void touch_reset(TouchEngine *s) {
    if (s == NULL) return;
    for (int i = 0; i < TS_MAX_SLOTS; i++) {
        s->slots[i].active = 0;
        s->slots[i].tracking_id = -1;
        s->slots[i].in_region = 0;
    }
    s->initialized = 0;
}

static void touch_set_factor(TouchEngine *s, float factor) {
    if (s == NULL) return;
    s->factor = ts_clamp_float(factor, TS_FACTOR_MIN, TS_FACTOR_MAX);
}

static void touch_set_sensitivity(TouchEngine *s, float sensitivity) {
    if (s == NULL) return;
    s->sensitivity = ts_clamp_float(sensitivity, TS_SENSITIVITY_MIN, TS_SENSITIVITY_MAX);
    s->sens_x = s->sensitivity;
    s->sens_y = s->sensitivity;
}

static void touch_set_sensitivity_xy(TouchEngine *s, float sx, float sy) {
    if (s == NULL) return;
    s->sens_x = ts_clamp_float(sx, TS_SENSITIVITY_MIN, TS_SENSITIVITY_MAX);
    s->sens_y = ts_clamp_float(sy, TS_SENSITIVITY_MIN, TS_SENSITIVITY_MAX);
    s->sensitivity = (s->sens_x + s->sens_y) * 0.5f;
}

static void touch_set_filter_controls(TouchEngine *s, float strength, float responsiveness) {
    if (s == NULL) return;
    s->strength = ts_clamp_float(strength, 0.0f, 100.0f);
    s->responsiveness = ts_clamp_float(responsiveness, 0.0f, 100.0f);
}

static void touch_set_dpi_scale(TouchEngine *s, float dpi_scale) {
    if (s == NULL) return;
    s->dpi_scale = (dpi_scale > 0.0f) ? dpi_scale : 1.0f;
}

static void touch_set_region(TouchEngine *s, int area,
                                      float abs_x_min, float abs_x_max,
                                      float abs_y_min, float abs_y_max) {
    if (s == NULL) return;
    s->area = area;
    s->abs_x_min = abs_x_min;
    s->abs_y_min = abs_y_min;
    s->screen_w = (abs_x_max > abs_x_min) ? (abs_x_max - abs_x_min) : 1080.0f;
    s->screen_h = (abs_y_max > abs_y_min) ? (abs_y_max - abs_y_min) : 1920.0f;
}

static void touch_set_tactix(TouchEngine *s, int enabled,
                                      float deadzone, float flick_speed, float flick_boost) {
    if (s == NULL) return;
    s->tactix = enabled ? 1 : 0;
    if (deadzone > 0.f) s->deadzone = deadzone;
    if (flick_speed > 0.f) s->flick_speed = flick_speed;
    if (flick_boost > 0.f) s->flick_boost = flick_boost;
}

static int touch_in_region(const TouchEngine *s, float x, float y) {
    (void)y;
    if (s == NULL) return 1;
    if (s->area == TS_AREA_ALL) return 1;
    float nx = (x - s->abs_x_min) / s->screen_w; 
    if (s->area == TS_AREA_LEFT)  return nx < 0.50f;
    if (s->area == TS_AREA_RIGHT) return nx >= 0.50f;
    return 1;
}

static void touch_slot(TouchEngine *s, int slot,
                                float in_x, float in_y, int down,
                                float dt_seconds,
                                float *out_x, float *out_y, int *out_down) {
    if (s == NULL || slot < 0 || slot >= TS_MAX_SLOTS) return;
    (void)dt_seconds;

    TS_Slot *sl = &s->slots[slot];

    if (!down) {
        sl->active = 0;
        sl->tracking_id = -1;
        sl->in_region = 0;
        sl->filtered_dx = 0.0f;
        sl->filtered_dy = 0.0f;
        if (out_x) *out_x = sl->x * s->dpi_scale;
        if (out_y) *out_y = sl->y * s->dpi_scale;
        if (out_down) *out_down = 0;
        return;
    }

    float nx = in_x / s->dpi_scale;
    float ny = in_y / s->dpi_scale;

    if (!sl->active) {
        sl->in_region = touch_in_region(s, in_x, in_y);
        sl->x = nx;
        sl->y = ny;
        sl->last_raw_x = nx;
        sl->last_raw_y = ny;
        sl->filtered_dx = 0.0f;
        sl->filtered_dy = 0.0f;
        sl->active = 1;
        if (out_x) *out_x = in_x;
        if (out_y) *out_y = in_y;
        if (out_down) *out_down = 1;
        return;
    }

    if (!sl->in_region) {
        sl->x = nx;
        sl->y = ny;
        sl->last_raw_x = nx;
        sl->last_raw_y = ny;
        sl->filtered_dx = 0.0f;
        sl->filtered_dy = 0.0f;
        if (out_x) *out_x = in_x;
        if (out_y) *out_y = in_y;
        if (out_down) *out_down = 1;
        return;
    }

    float rawDx = nx - sl->last_raw_x;
    float rawDy = ny - sl->last_raw_y;
    float speed = 0.0f;
    if (s->tactix) speed = sqrtf(rawDx * rawDx + rawDy * rawDy);

    /* TactiX is deliberately non-accelerating: it only suppresses tiny
       unintentional movement. No flick multiplier/acceleration is applied. */
    if (s->tactix && speed < s->deadzone) {
        sl->last_raw_x = nx;
        sl->last_raw_y = ny;
        if (out_x) *out_x = sl->x * s->dpi_scale;
        if (out_y) *out_y = sl->y * s->dpi_scale;
        if (out_down) *out_down = 1;
        return;
    }

    /* True linear sensitivity. The UI value is the actual movement multiplier:
       1.00x = neutral, 2.00x = 2x, ... 5.00x = 5x.
       Do not low-pass or attenuate the delta here: doing so makes the screen
       feel heavy even when sensitivity is raised. Strength/Responsiveness are
       kept in the IPC/UI for compatibility, but they must not reduce the
       primary sensitivity path. */
    float gainX = ts_clamp_float(s->sens_x, TS_SENSITIVITY_MIN, TS_SENSITIVITY_MAX);
    float gainY = ts_clamp_float(s->sens_y, TS_SENSITIVITY_MIN, TS_SENSITIVITY_MAX);

    float finalDx = rawDx * gainX;
    float finalDy = rawDy * gainY;

    /* One very light one-pole filter. It is deliberately capped at 8% of the
       delta so high sensitivity stays responsive instead of feeling heavy.
       The single UI Smoothness value is sent as both legacy controls. */
    float smooth01 = ts_clamp_float((s->strength + s->responsiveness) * 0.005f, 0.0f, 1.0f);
    float carry = 0.08f * smooth01;
    float fresh = 1.0f - carry;
    sl->filtered_dx = sl->filtered_dx * carry + finalDx * fresh;
    sl->filtered_dy = sl->filtered_dy * carry + finalDy * fresh;

    sl->last_raw_x = nx;
    sl->last_raw_y = ny;
    sl->x += sl->filtered_dx;
    sl->y += sl->filtered_dy;

    if (out_x) *out_x = sl->x * s->dpi_scale;
    if (out_y) *out_y = sl->y * s->dpi_scale;
    if (out_down) *out_down = 1;
}

static void touch_force_release(TouchEngine *s, int slot) {
    if (s == NULL || slot < 0 || slot >= TS_MAX_SLOTS) return;
    s->slots[slot].active = 0;
    s->slots[slot].tracking_id = -1;
    s->slots[slot].in_region = 0;
    s->slots[slot].filtered_dx = 0.0f;
    s->slots[slot].filtered_dy = 0.0f;
}

static int touch_slot_active(const TouchEngine *s, int slot) {
    if (s == NULL || slot < 0 || slot >= TS_MAX_SLOTS) return 0;
    return s->slots[slot].active;
}

#endif 
