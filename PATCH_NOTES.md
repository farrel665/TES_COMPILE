# Magic Manager patch

- GamingSwitch defaults to OFF.
- Removed Global Sensitivity UI and checkbox.
- Moved Active switch into Magic Touch.
- Sensitivity Area is above Sensitivity X/Y and uses hexagon containers.
- Added vertical ScrollView while keeping a 360dp x 660dp panel.
- Sensitivity X/Y range is 1.00x..5.00x.
- Fixed OctagonCheckBox API usage (`setChecked(boolean)`).
- Strength and Responsiveness are passed through Shizuku Binder to the native worker.
- Native Strength controls smoothing strength; Responsiveness reduces that smoothing to restore direct response.
- Fixed X/Y sensitivity assignment being swapped in native code.
- Native input timing uses measured frame intervals and avoids redundant identical uinput position events to reduce jitter/ghost-like duplicate events.
- GamingSeekBar +/- buttons use chamfered/hexagonal styling.
- Panel and Sensitivity Area buttons use HexagonLinearLayout styling.
