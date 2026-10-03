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

    TS_Slot *sl = &s->slots[slot];

    if (!down) {
        sl->active = 0;
        sl->tracking_id = -1;
        sl->in_region = 0;
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
        if (out_x) *out_x = in_x;
        if (out_y) *out_y = in_y;
        if (out_down) *out_down = 1;
        return;
    }

    float safe_dt = (dt_seconds > 0.0f) ? dt_seconds : (1.0f / 120.0f);
    if (safe_dt > 0.05f) safe_dt = 0.05f;

    float strength01 = ts_clamp_float(s->strength * 0.01f, 0.0f, 1.0f);
    float response01 = ts_clamp_float(s->responsiveness * 0.01f, 0.0f, 1.0f);

    float rawDx = nx - sl->last_raw_x;
    float rawDy = ny - sl->last_raw_y;
    float speed = sqrtf(rawDx * rawDx + rawDy * rawDy);

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

    /* Linear 1:1 curve with a deliberately mild gain. 1.00x is neutral;
       2.00x becomes 1.10x effective movement instead of a raw 2x jump,
       avoiding the overly slippery response of the previous build. */
    float gainX = 1.0f + (s->sens_x - 1.0f) * 0.10f;
    float gainY = 1.0f + (s->sens_y - 1.0f) * 0.10f;
    gainX = ts_clamp_float(gainX, 1.0f, 1.40f);
    gainY = ts_clamp_float(gainY, 1.0f, 1.40f);

    float finalDx = rawDx * gainX;
    float finalDy = rawDy * gainY;
    sl->last_raw_x = nx;
    sl->last_raw_y = ny;

    float targetX = sl->x + finalDx;
    float targetY = sl->y + finalDy;

    /* Mild, frame-rate-independent low-pass filter. Responsiveness reduces
       the filtering amount, while 0 strength remains completely raw. */
    float smoothing = strength01 * (1.0f - 0.80f * response01);
    float alpha = 1.0f - smoothing * 0.85f;
    alpha = ts_clamp_float(alpha, 0.15f, 1.0f);
    float frameAlpha = 1.0f - powf(1.0f - alpha, safe_dt * 120.0f);
    frameAlpha = ts_clamp_float(frameAlpha, 0.05f, 1.0f);

    sl->x += (targetX - sl->x) * frameAlpha;
    sl->y += (targetY - sl->y) * frameAlpha;

    if (out_x) *out_x = sl->x * s->dpi_scale;
    if (out_y) *out_y = sl->y * s->dpi_scale;
    if (out_down) *out_down = 1;
}

static void touch_force_release(TouchEngine *s, int slot) {
    if (s == NULL || slot < 0 || slot >= TS_MAX_SLOTS) return;
    s->slots[slot].active = 0;
    s->slots[slot].tracking_id = -1;
    s->slots[slot].in_region = 0;
}

static int touch_slot_active(const TouchEngine *s, int slot) {
    if (s == NULL || slot < 0 || slot >= TS_MAX_SLOTS) return 0;
    return s->slots[slot].active;
}

#endif 
