#!/bin/sh
# Compiles the pure-Java touch pad logic (no Android classes) and runs its checks.
set -e
here=$(cd "$(dirname "$0")" && pwd)
src="$here/../../android/java/com/aure/totk/touch"
out=$(mktemp -d)
javac --release 17 -d "$out" "$src/Sw.java" "$src/Control.java" "$src/PadLayout.java" "$src/ScreenKey.java" "$src/PadEngine.java" "$here/TouchPadProbe.java"
java -cp "$out" com.aure.totk.touch.TouchPadProbe
rm -rf "$out"
