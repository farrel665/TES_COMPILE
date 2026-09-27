# rizxbyte inject — Shizuku (non-root) edition

Same touch-smoother / uinput injector as the original root version, but **all privileged work is done through Shizuku**.

No `su`, no Magisk, no KernelSU required.  
Shizuku runs as the ADB shell user (uid 2000), which on stock Android already has access to `/dev/input/event*`, `/dev/uinput` and `/data/local/tmp`.

---

## What changed vs root version

| Component              | Root version                     | Shizuku version                          |
|------------------------|----------------------------------|------------------------------------------|
| Privilege check        | `su -c id`                       | `Shizuku.checkSelfPermission()`          |
| Stage + exec worker    | `su -c "cp … && setsid …"`       | `UserService` (runs inside Shizuku)      |
| Kill worker            | `su -c kill`                     | `UserService.stopWorker()`               |
| UI label               | "Root GRANTED / WITHHELD"        | "Shizuku GRANTED / WITHHELD"             |
| Entry Activity         | pure NativeActivity              | `BootstrapActivity` → NativeActivity     |

Native C code (`touch_io.c`, `worker_main.c`, smoother, …) is **unchanged**.  
Only the launcher / exec path was replaced.

---

## Requirements

1. **Shizuku** installed and started  
   - Play Store / GitHub: https://github.com/RikkaApps/Shizuku  
   - Start via **Wireless debugging** (Android 11+) or classic ADB pair.
2. Grant the app permission inside the Shizuku app (or the system dialog that appears on first launch).
3. Device must allow `/dev/uinput` for the shell user (true on almost every stock ROM).

---

## Build (AndroidIDE / Android Studio)

1. Open the `inject_uinput_shizuku` folder.
2. Let Gradle sync (downloads Shizuku AAR + imgui).
3. NDK must be present (same as before).
4. Build → Run.

If you already have a pre-built `librizxbytesmoother.so`, drop it into  
`app/src/main/jniLibs/arm64-v8a/` and the CMake post-build step will still copy the fresh one.

---

## Runtime flow

```
BootstrapActivity
   └─ ShizukuHelper.init()
         ├─ request permission (if needed)
         └─ Shizuku.bindUserService(UserService)
               └─ onServiceConnected → nativeSetShizukuReady(true)

NativeActivity (ImGui)
   └─ START button
         └─ start_worker()  [C++]
               └─ JNI → NativeBridge.startWorkerViaShizuku()
                     └─ ShizukuHelper → UserService.startWorker()
                           ├─ copy binary → /data/local/tmp/rizxbyte_smoother
                           ├─ chmod 755
                           └─ setsid … --preset N &
```

Worker binary still opens `/dev/input/event*` + `/dev/uinput` exactly like the root version; the only difference is **who** launched it (shell uid via Shizuku instead of uid 0).

---

## Troubleshooting

| Symptom                        | Fix                                              |
|--------------------------------|--------------------------------------------------|
| "Shizuku not authorized"       | Open Shizuku app → start service → grant this app |
| "UserService not bound"        | Wait 1-2 s after launch, or restart Shizuku      |
| worker starts then dies        | Check `/data/local/tmp/rizxbyte_smoother.log`    |
| Permission denied on /dev/uinput | Rare; some OEMs restrict uinput even for shell. Try another device or a custom kernel that exposes uinput to shell. |

---

## Notes

- Package name stays `com.rizxbyte.inject` so you can side-grade over the root APK if you want.
- The worker still writes its log to `/data/local/tmp/rizxbyte_smoother.log` (readable without root).
- Volume-key menu and all presets work identically.
