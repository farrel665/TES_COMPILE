#include "touch_io.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/ioctl.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <time.h>
#include <math.h>

#ifndef UI_SET_ABSBIT
#define UI_SET_ABSBIT _IOW(UINPUT_IOCTL_BASE, 103, int)
#endif

#ifndef SYN_DROPPED
#define SYN_DROPPED 3
#endif

static char g_capture_path[64];

static int ancore_is_skip_name(const char *name) {
    if (!name) return 1;
    if (strstr(name, "ancore") != NULL) return 1;
    if (strstr(name, "uinput") != NULL) return 1;
    if (strstr(name, "Virtual") != NULL) return 1;
    return 0;
}

static int ancore_device_is_touch(int fd, int require_slot) {
    unsigned char absbits[(ABS_MAX + 7) / 8];
    memset(absbits, 0, sizeof(absbits));
    if (ioctl(fd, EVIOCGBIT(EV_ABS, sizeof(absbits)), absbits) < 0)
        return 0;
    int has_x = absbits[ABS_MT_POSITION_X / 8] & (1 << (ABS_MT_POSITION_X % 8));
    int has_y = absbits[ABS_MT_POSITION_Y / 8] & (1 << (ABS_MT_POSITION_Y % 8));
    if (!(has_x && has_y)) return 0;
    if (require_slot) {
        int has_slot = absbits[ABS_MT_SLOT / 8] & (1 << (ABS_MT_SLOT % 8));
        if (!has_slot) return 0;
    }
    return 1;
}

static int ancore_score_name(const char *name) {
    if (!name) return 0;
    int s = 0;
    if (strstr(name, "touch") || strstr(name, "ts") ||
        strstr(name, "goodix") || strstr(name, "fts") ||
        strstr(name, "synaptics") || strstr(name, "focaltech") ||
        strstr(name, "nvt") || strstr(name, "himax"))
        s += 10;
    if (strstr(name, "gpio") || strstr(name, "key"))
        s -= 5;
    return s;
}

/* Try RDWR first (needed for EVIOCGRAB); fall back to RDONLY for read-only shell access */
static int ancore_open_event(const char *path) {
    int fd = open(path, O_RDWR | O_NONBLOCK);
    if (fd >= 0) return fd;
    fd = open(path, O_RDONLY | O_NONBLOCK);
    return fd;
}

static int ancore_find_touch_capture(void) {
    char path[64];
    char name[256];
    int best_fd = -1;
    int best_score = -999;
    char best_path[64] = {};
    int any_visible = 0;
    int open_errno = 0;

    for (int pass = 0; pass < 2; pass++) {
        int require_slot = (pass == 0);
        for (int i = 0; i < 32; i++) {
            snprintf(path, sizeof(path), "/dev/input/event%d", i);
            int fd = ancore_open_event(path);
            if (fd < 0) {
                if (errno == EACCES || errno == EPERM) open_errno = errno;
                continue;
            }
            any_visible = 1;

            memset(name, 0, sizeof(name));
            ioctl(fd, EVIOCGNAME(sizeof(name) - 1), name);
            if (ancore_is_skip_name(name)) {
                close(fd);
                continue;
            }
            if (!ancore_device_is_touch(fd, require_slot)) {
                close(fd);
                continue;
            }

            int score = ancore_score_name(name) + (require_slot ? 20 : 0);
            if (score > best_score) {
                if (best_fd >= 0) close(best_fd);
                best_fd = fd;
                best_score = score;
                snprintf(best_path, sizeof(best_path), "%s", path);
            } else {
                close(fd);
            }
        }
        if (best_fd >= 0) break;
    }

    if (best_fd >= 0) {
        snprintf(g_capture_path, sizeof(g_capture_path), "%s", best_path);
        fprintf(stderr, "[ancore] capture device %s\n", g_capture_path);
    } else {
        fprintf(stderr, "[ancore] no touch event node opened (visible=%d errno=%d %s)\n",
                any_visible, open_errno, open_errno ? strerror(open_errno) : "none");
        fprintf(stderr, "[ancore] hint: shell needs read on /dev/input/event* — "
                "check ls -l /dev/input and Shizuku uid\n");
    }
    return best_fd;
}

int ancore_capture_open(TouchCapture *tc) {
    if (tc == NULL) return -1;
    memset(tc, 0, sizeof(*tc));
    tc->fd = -1;
    g_capture_path[0] = '\0';

    tc->fd = ancore_find_touch_capture();
    if (tc->fd < 0) return -1;
    tc->grabbed = 0;
    tc->cur_slot = 0;

    struct input_absinfo info;
    memset(&info, 0, sizeof(info));

    if (ioctl(tc->fd, EVIOCGABS(ABS_MT_POSITION_X), &info) < 0) {
        close(tc->fd); tc->fd = -1; return -1;
    }
    tc->abs_x_min = info.minimum;
    tc->abs_x_max = info.maximum;

    if (ioctl(tc->fd, EVIOCGABS(ABS_MT_POSITION_Y), &info) < 0) {
        close(tc->fd); tc->fd = -1; return -1;
    }
    tc->abs_y_min = info.minimum;
    tc->abs_y_max = info.maximum;

    for (int i = 0; i < TS_MAX_SLOTS; i++) {
        tc->slots[i].tracking_id = -1;
        tc->slots[i].x = 0;
        tc->slots[i].y = 0;
        tc->slots[i].active = 0;
    }

    return 0;
}

static int ancore_try_grab_fd(int fd) {
    if (fd < 0) return -1;
    ioctl(fd, EVIOCGRAB, 0);
    usleep(20 * 1000);
    if (ioctl(fd, EVIOCGRAB, 1) == 0) return 0;
    return -1;
}

int ancore_capture_grab(TouchCapture *tc) {
    if (tc == NULL || tc->fd < 0) return -1;
    if (tc->grabbed) return 0;

    for (int attempt = 0; attempt < 6; attempt++) {
        if (ancore_try_grab_fd(tc->fd) == 0) {
            tc->grabbed = 1;
            return 0;
        }

        
        if (g_capture_path[0]) {
            int nfd = ancore_open_event(g_capture_path);
            if (nfd >= 0) {
                close(tc->fd);
                tc->fd = nfd;
                if (ancore_try_grab_fd(tc->fd) == 0) {
                    tc->grabbed = 1;
                    return 0;
                }
            }
        }

        
        char path[64], name[256];
        for (int i = 0; i < 32; i++) {
            snprintf(path, sizeof(path), "/dev/input/event%d", i);
            if (g_capture_path[0] && strcmp(path, g_capture_path) == 0)
                continue;
            int fd = ancore_open_event(path);
            if (fd < 0) continue;
            memset(name, 0, sizeof(name));
            ioctl(fd, EVIOCGNAME(sizeof(name) - 1), name);
            if (ancore_is_skip_name(name) || !ancore_device_is_touch(fd, 0)) {
                close(fd);
                continue;
            }
            if (ancore_try_grab_fd(fd) == 0) {
                
                if (tc->fd >= 0) close(tc->fd);
                tc->fd = fd;
                snprintf(g_capture_path, sizeof(g_capture_path), "%s", path);
                struct input_absinfo info;
                memset(&info, 0, sizeof(info));
                if (ioctl(tc->fd, EVIOCGABS(ABS_MT_POSITION_X), &info) == 0) {
                    tc->abs_x_min = info.minimum;
                    tc->abs_x_max = info.maximum;
                }
                if (ioctl(tc->fd, EVIOCGABS(ABS_MT_POSITION_Y), &info) == 0) {
                    tc->abs_y_min = info.minimum;
                    tc->abs_y_max = info.maximum;
                }
                tc->grabbed = 1;
                fprintf(stderr, "[ancore] grab on %s\n", path);
                return 0;
            }
            close(fd);
        }
        usleep(150 * 1000);
    }

    fprintf(stderr, "[ancore] grab busy — close other touch modules\n");
    return -1;
}

void ancore_capture_ungrab(TouchCapture *tc) {
    if (tc == NULL || tc->fd < 0 || !tc->grabbed) return;
    ioctl(tc->fd, EVIOCGRAB, 0);
    tc->grabbed = 0;
}

void ancore_capture_close(TouchCapture *tc) {
    if (tc == NULL) return;
    if (tc->fd >= 0) {
        if (tc->grabbed) {
            ioctl(tc->fd, EVIOCGRAB, 0);
        }
        close(tc->fd);
    }
    tc->fd = -1;
    tc->grabbed = 0;
}

bool ancore_capture_poll(TouchCapture *tc) {
    if (tc == NULL || tc->fd < 0) return false;

    int have_update = 0;
    struct input_event ev;

    while (read(tc->fd, &ev, sizeof(ev)) == sizeof(ev)) {
        if (ev.type == EV_ABS) {
            switch (ev.code) {
            case ABS_MT_SLOT:
                if (ev.value >= 0 && ev.value < TS_MAX_SLOTS) {
                    tc->cur_slot = ev.value;
                }
                break;
            case ABS_MT_TRACKING_ID:
                if (ev.value >= 0) {
                    tc->slots[tc->cur_slot].tracking_id = ev.value;
                    tc->slots[tc->cur_slot].active = 1;
                } else {
                    tc->slots[tc->cur_slot].active = 0;
                    tc->slots[tc->cur_slot].tracking_id = -1;
                }
                have_update = 1;
                break;
            case ABS_MT_POSITION_X:
                tc->slots[tc->cur_slot].x = ev.value;
                have_update = 1;
                break;
            case ABS_MT_POSITION_Y:
                tc->slots[tc->cur_slot].y = ev.value;
                have_update = 1;
                break;
            default:
                break;
            }
        } else if (ev.type == EV_KEY && ev.code == BTN_TOUCH) {
            if (ev.value == 0) {
                for (int i = 0; i < TS_MAX_SLOTS; i++) {
                    tc->slots[i].active = 0;
                    tc->slots[i].tracking_id = -1;
                }
            }
            have_update = 1;
        } else if (ev.type == EV_SYN) {
            if (ev.code == SYN_REPORT) {
                if (have_update) return true;
            } else if (ev.code == SYN_DROPPED) {
                
                for (int i = 0; i < TS_MAX_SLOTS; i++) {
                    tc->slots[i].active = 0;
                    tc->slots[i].tracking_id = -1;
                }
                have_update = 1;
            }
        }
    }
    return have_update != 0;
}

static void ancore_emit(int fd, int type, int code, int val) {
    struct input_event ev;
    memset(&ev, 0, sizeof(ev));
    ev.type  = type;
    ev.code  = code;
    ev.value = val;
    write(fd, &ev, sizeof(ev));
}

int ancore_inject_open(TouchInject *ti, int abs_x_min, int abs_x_max,
                         int abs_y_min, int abs_y_max) {
    if (ti == NULL) return -1;

    ti->fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (ti->fd < 0) return -1;

    ioctl(ti->fd, UI_SET_EVBIT, EV_ABS);
    ioctl(ti->fd, UI_SET_EVBIT, EV_KEY);
    ioctl(ti->fd, UI_SET_EVBIT, EV_SYN);

    ioctl(ti->fd, UI_SET_KEYBIT, BTN_TOUCH);
    ioctl(ti->fd, UI_SET_KEYBIT, BTN_TOOL_FINGER);

    ioctl(ti->fd, UI_SET_ABSBIT, ABS_MT_SLOT);
    ioctl(ti->fd, UI_SET_ABSBIT, ABS_MT_TRACKING_ID);
    ioctl(ti->fd, UI_SET_ABSBIT, ABS_MT_POSITION_X);
    ioctl(ti->fd, UI_SET_ABSBIT, ABS_MT_POSITION_Y);

    ioctl(ti->fd, UI_SET_PROPBIT, INPUT_PROP_DIRECT);

    struct uinput_user_dev udev;
    memset(&udev, 0, sizeof(udev));
    snprintf(udev.name, UINPUT_MAX_NAME_SIZE, "ancore_touch");
    udev.id.bustype = BUS_VIRTUAL;
    udev.id.vendor  = 0x1;
    udev.id.product = 0x1;
    udev.id.version = 1;

    udev.absmin[ABS_MT_POSITION_X] = abs_x_min;
    udev.absmax[ABS_MT_POSITION_X] = abs_x_max;
    udev.absmin[ABS_MT_POSITION_Y] = abs_y_min;
    udev.absmax[ABS_MT_POSITION_Y] = abs_y_max;

    udev.absmin[ABS_MT_SLOT] = 0;
    udev.absmax[ABS_MT_SLOT] = TS_MAX_SLOTS - 1;

    udev.absmin[ABS_MT_TRACKING_ID] = 0;
    udev.absmax[ABS_MT_TRACKING_ID] = 65535;

    if (write(ti->fd, &udev, sizeof(udev)) != sizeof(udev)) {
        close(ti->fd); ti->fd = -1; return -1;
    }

    if (ioctl(ti->fd, UI_DEV_CREATE) < 0) {
        close(ti->fd); ti->fd = -1; return -1;
    }

    for (int i = 0; i < TS_MAX_SLOTS; i++) {
        ti->virtual_active[i] = 0;
        ti->virtual_x[i] = 0;
        ti->virtual_y[i] = 0;
        ti->tracking_id[i] = -1;
        ti->last_active[i] = 0;
    }
    ti->last_btn = 0;
    ti->next_tracking_id = 1;
    ti->dirty = 0;
    ti->any_active = 0;

    usleep(50000);
    return 0;
}

void ancore_inject_close(TouchInject *ti) {
    if (ti == NULL) return;
    if (ti->fd >= 0) {
        for (int i = 0; i < TS_MAX_SLOTS; i++) {
            if (ti->last_active[i]) {
                ancore_emit(ti->fd, EV_ABS, ABS_MT_SLOT, i);
                ancore_emit(ti->fd, EV_ABS, ABS_MT_TRACKING_ID, -1);
            }
        }
        if (ti->last_btn) {
            ancore_emit(ti->fd, EV_KEY, BTN_TOUCH, 0);
            ancore_emit(ti->fd, EV_KEY, BTN_TOOL_FINGER, 0);
        }
        ancore_emit(ti->fd, EV_SYN, SYN_REPORT, 0);

        ioctl(ti->fd, UI_DEV_DESTROY);
        close(ti->fd);
    }
    ti->fd = -1;
}

void ancore_inject_slot(TouchInject *ti, int slot, int x, int y, int down) {
    if (ti == NULL || ti->fd < 0) return;
    if (slot < 0 || slot >= TS_MAX_SLOTS) return;

    if (down) {
        if (!ti->virtual_active[slot]) {
            ti->tracking_id[slot] = ti->next_tracking_id;
            ti->next_tracking_id++;
            if (ti->next_tracking_id > 65535) ti->next_tracking_id = 1;
            ti->virtual_active[slot] = 1;
        }
        if (!ti->virtual_active[slot] || ti->virtual_x[slot] != x || ti->virtual_y[slot] != y) {
            ti->virtual_x[slot] = x;
            ti->virtual_y[slot] = y;
            ti->dirty = 1;
        }
    } else {
        if (ti->virtual_active[slot]) {
            ti->virtual_active[slot] = 0;
            ti->tracking_id[slot] = -1;
            ti->dirty = 1;
        }
    }
}

void ancore_inject_slot_release(TouchInject *ti, int slot) {
    ancore_inject_slot(ti, slot, 0, 0, 0);
}

void ancore_inject_flush(TouchInject *ti) {
    if (ti == NULL || ti->fd < 0 || !ti->dirty) return;

    int count = 0;
    for (int i = 0; i < TS_MAX_SLOTS; i++) {
        if (ti->virtual_active[i]) count++;
    }

    
    for (int slot = 0; slot < TS_MAX_SLOTS; slot++) {
        int was = ti->last_active[slot];
        int now = ti->virtual_active[slot];

        if (now && !was) {
            ancore_emit(ti->fd, EV_ABS, ABS_MT_SLOT, slot);
            ancore_emit(ti->fd, EV_ABS, ABS_MT_TRACKING_ID, ti->tracking_id[slot]);
            ancore_emit(ti->fd, EV_ABS, ABS_MT_POSITION_X, ti->virtual_x[slot]);
            ancore_emit(ti->fd, EV_ABS, ABS_MT_POSITION_Y, ti->virtual_y[slot]);
        } else if (now && was) {
            ancore_emit(ti->fd, EV_ABS, ABS_MT_SLOT, slot);
            ancore_emit(ti->fd, EV_ABS, ABS_MT_POSITION_X, ti->virtual_x[slot]);
            ancore_emit(ti->fd, EV_ABS, ABS_MT_POSITION_Y, ti->virtual_y[slot]);
        } else if (!now && was) {
            ancore_emit(ti->fd, EV_ABS, ABS_MT_SLOT, slot);
            ancore_emit(ti->fd, EV_ABS, ABS_MT_TRACKING_ID, -1);
        }

        ti->last_active[slot] = now;
    }

    int btn = (count > 0) ? 1 : 0;
    if (btn != ti->last_btn) {
        ancore_emit(ti->fd, EV_KEY, BTN_TOUCH, btn);
        ancore_emit(ti->fd, EV_KEY, BTN_TOOL_FINGER, btn);
        ti->last_btn = btn;
    }

    ancore_emit(ti->fd, EV_SYN, SYN_REPORT, 0);
    ti->dirty = 0;
    ti->any_active = count;
}
