package com.aure.totk.touch;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The touch pad's settings, in this app's own preferences file (the game process is the only one that writes them):
 * on/off, the A/B swap, one layout per screen ("folded" / "unfolded"), and named presets. */
final class TouchStore {
    private static final String PRESET = "preset_";
    private final SharedPreferences prefs;
    TouchStore(Context context) { prefs = context.getSharedPreferences("touch_pad", Context.MODE_PRIVATE); }

    boolean enabled() { return prefs.getBoolean("enabled", true); }
    void setEnabled(boolean on) { prefs.edit().putBoolean("enabled", on).apply(); }
    boolean swapAB() { return prefs.getBoolean("swap_ab", false); }
    void setSwapAB(boolean on) { prefs.edit().putBoolean("swap_ab", on).apply(); }

    PadLayout load(String screen) {
        return PadLayout.fromText(prefs.getString("layout_" + screen, null), PadLayout.defaults(ScreenKey.wide(screen)));
    }
    void save(String screen, PadLayout layout) { prefs.edit().putString("layout_" + screen, layout.toText()).apply(); }
    void reset(String screen) { prefs.edit().remove("layout_" + screen).apply(); }

    List<String> presets() {
        List<String> names = new ArrayList<>();
        for (String k : prefs.getAll().keySet()) if (k.startsWith(PRESET)) names.add(k.substring(PRESET.length()));
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }
    void savePreset(String name, PadLayout layout) { prefs.edit().putString(PRESET + name, layout.toText()).apply(); }
    /** The preset's layout, laid onto this screen's defaults; null when it no longer exists. */
    PadLayout loadPreset(String name, String screen) {
        String text = prefs.getString(PRESET + name, null);
        return text == null ? null : PadLayout.fromText(text, PadLayout.defaults(ScreenKey.wide(screen)));
    }
    void deletePreset(String name) { prefs.edit().remove(PRESET + name).apply(); }
}
