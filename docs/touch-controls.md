# On-screen touch controls

A Switch-style gamepad drawn over the game for devices without a built-in controller (built with the Honor Magic V5 in
mind). A real controller keeps working at the same time: the two are merged, and each stick takes whichever is pushed further.

## Using it

- The pad appears when the game starts. The small handle at the top centre opens the menu.
- **Menu**: Edit controls, Hide/Show touch controls, See-through level, Swap A and B, Presets, Reset this screen's layout.
  Hiding the pad leaves only the handle, and the rest of the screen stays free of the overlay.
- **Editing** (game is paused while a menu is open): drag a control to move it; pinch it, or use **Smaller / Larger**, to
  resize; **Hide / Show** removes or restores it (hidden controls show as dashed outlines while editing); **Button: …**
  remaps it to any other Switch button. Top bar: **Done**, **See-through** (left = more see-through), **A↔B** (swaps what
  the A and B buttons send; labels follow), **Presets**, **Reset**.
- **Presets** are named copies of the layout you are editing. A preset can be used on either screen.
- **Folded and unfolded**: a window that is much wider than tall (about 1.6:1 or more) uses the *folded* layout, a
  squarer one the *unfolded* layout (ScreenKey.java). Each keeps its own positions, sizes, hidden controls, remaps and
  see-through level. The A/B swap and the on/off switch are shared. The ratio rule is an estimate from the Magic V5's
  two panel shapes (cover about 2.2:1, inner about 1.1:1); a split-screen window may be classed as the other layout.
- L3 and R3 exist but start hidden; show them in the editor if you need stick clicks.

## How it works

| Piece | Where |
|---|---|
| Touch to pad state (sticks, D-pad, roll-over between buttons, remap, A/B swap) | `android/java/com/aure/totk/touch/PadEngine.java` (plain Java) |
| Layouts, defaults, text storage | `PadLayout.java`, `Control.java`, `Sw.java`, `ScreenKey.java` |
| Drawing and the editor | `TouchPadView.java` |
| The window over the game | `TouchOverlay.java` (a non-focusable `TYPE_APPLICATION_PANEL` window) |
| Saved settings | `TouchStore.java` (preferences file `touch_pad`, game process only) |
| Merge into the game's input | `runtime/frontend_android.c` `runtime_android_touch_pad`, registered in `companion_jni.c` as `TotkActivity.nativeTouchPad` |

The game is a NativeActivity whose window sends every input event straight to native code, so a view inside it would never
receive touches; the pad therefore lives in a separate window. Physical-controller routing is untouched.

## Tests

`sh games/totk/tests/touch/run.sh` compiles the Android-free logic and runs its checks (screen choice, layout storage and
damaged-data fallback, sticks and dead zone, D-pad, rolling between buttons, remap, A/B swap, multi-touch).
The drawing, window and dialog code needs a device (or emulator) to try.

## Building the APK with this change

The release build needs the Android SDK, NDK r28 and a signing key of your own; see [building.md](building.md). The GPU
driver is optional: leave out `--turnip` and add a Turnip `.zip` or `.so` in the app afterwards (*Game data → Graphics
driver*). The touch pad adds no files or permissions:

```sh
python3 games/totk/android/build_release.py --ndk <Android NDK r28> \
  --key <your signing key .p12> --key-pass-env <VAR> -o totk-native.apk
adb install -r totk-native.apk
```

An APK signed with a different key than the one already installed cannot replace it: export your saves, uninstall, install,
and import them.
