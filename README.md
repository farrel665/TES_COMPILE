# Magic Manager — Sensitivity Manager

This build contains only the **Sensitivity Manager**. Macro/trigger features have been removed.

## What changed
- Global touch mode: the native worker no longer waits for or checks Free Fire package/PID.
- X/Y mapping is end-to-end correct: X = horizontal, Y = vertical.
- Linear response only; no acceleration/flick boost curve.
- Sensitivity uses a mild linear gain so 2.00x does not become an aggressive raw 2x multiplier.
- TactiX only filters tiny unintentional movement; it does not add flick acceleration.
- Strength + Responsiveness use a mild low-pass filter.
- Stop path explicitly releases the touchscreen grab, releases active virtual slots, and closes the virtual input device.
- The startup path no longer force-releases unrelated `/dev/input` devices.
- Hide keeps the small `M` bubble; the hidden panel window becomes `FLAG_NOT_TOUCHABLE` so the area underneath remains touch-through.

## Shizuku / non-root limitation
The Java side is Shizuku-only and does not request root. However, raw global touchscreen processing in this architecture requires the Shizuku service to be allowed to open and grab `/dev/input/event*` and create `/dev/uinput` on the device ROM. Stock Android/SELinux may deny those nodes to the shell UID even when Shizuku is authorized. If the ROM denies access, the worker exits without changing system touch input.
