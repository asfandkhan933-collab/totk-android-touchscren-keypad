package com.aure.totk.touch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A set of controls plus the see-through level. Stored as plain text (no JSON library, no locale-dependent numbers). */
public final class PadLayout {
    public final List<Control> controls = new ArrayList<>();
    /** 0.15 (very see-through) .. 1.0 (solid). */
    public float alpha = 0.6f;
    public static final float MIN_ALPHA = 0.15f;

    public Control find(String id) {
        for (Control c : controls) if (c.id.equals(id)) return c;
        return null;
    }
    public PadLayout copy() {
        PadLayout l = new PadLayout();
        l.alpha = alpha;
        for (Control c : controls) l.controls.add(c.copy());
        return l;
    }

    // ---- storage ----
    public String toText() {
        StringBuilder s = new StringBuilder("TPL1\nalpha=").append(Math.round(alpha * 1000)).append('\n');
        for (Control c : controls) {
            s.append("c,").append(c.id).append(',').append(Math.round(c.x * 10000)).append(',').append(Math.round(c.y * 10000))
                    .append(',').append(Math.round(c.sizeDp * 10)).append(',').append(c.visible ? 1 : 0).append(',').append(c.target).append('\n');
        }
        return s.toString();
    }
    /** Parses text from toText(); anything missing or damaged falls back to {@code base}'s value for that control. Never throws. */
    public static PadLayout fromText(String text, PadLayout base) {
        PadLayout out = base.copy();
        if (text == null) return out;
        String[] lines = text.split("\n");
        if (lines.length == 0 || !lines[0].trim().equals("TPL1")) return out;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            try {
                if (line.startsWith("alpha=")) {
                    out.alpha = Math.max(MIN_ALPHA, Math.min(1f, Integer.parseInt(line.substring(6)) / 1000f));
                } else if (line.startsWith("c,")) {
                    String[] f = line.split(",");
                    if (f.length != 7) continue;
                    Control c = out.find(f[1]);
                    if (c == null) continue;
                    c.x = Integer.parseInt(f[2]) / 10000f; c.y = Integer.parseInt(f[3]) / 10000f;
                    c.sizeDp = Integer.parseInt(f[4]) / 10f; c.visible = f[5].equals("1");
                    int t = Integer.parseInt(f[6]);
                    if (c.remappable() && t >= 0 && t <= 15) c.target = t;
                    c.clamp();
                }
            } catch (NumberFormatException damaged) { /* keep the default for that line */ }
        }
        return out;
    }

    // ---- the starting layouts ----
    /** A wide (folded) screen is about 2.2:1; a square-ish (unfolded) one about 1.1:1. */
    public static PadLayout defaults(boolean wide) {
        PadLayout l = new PadLayout();
        float sx = wide ? 1f : 1f;
        l.controls.add(new Control("stickL", Control.Kind.STICK, wide ? 0.13f : 0.20f, wide ? 0.58f : 0.62f, 130, 0));
        l.controls.add(new Control("dpad", Control.Kind.DPAD, wide ? 0.13f : 0.20f, wide ? 0.88f : 0.87f, 110, 0));
        l.controls.add(new Control("stickR", Control.Kind.STICK, wide ? 0.72f : 0.62f, wide ? 0.82f : 0.87f, 120, 0));
        float ax = wide ? 0.89f : 0.83f, ay = wide ? 0.50f : 0.55f, d = wide ? 0.065f : 0.095f, dy = wide ? 0.16f : 0.11f;
        l.controls.add(new Control("X", Control.Kind.BUTTON, ax, ay - dy, 62, Sw.X));
        l.controls.add(new Control("Y", Control.Kind.BUTTON, ax - d, ay, 62, Sw.Y));
        l.controls.add(new Control("A", Control.Kind.BUTTON, ax + d, ay, 62, Sw.A));
        l.controls.add(new Control("B", Control.Kind.BUTTON, ax, ay + dy, 62, Sw.B));
        l.controls.add(new Control("L", Control.Kind.SHOULDER, wide ? 0.09f : 0.12f, wide ? 0.13f : 0.07f, 48, Sw.L));
        l.controls.add(new Control("ZL", Control.Kind.SHOULDER, wide ? 0.09f : 0.12f, wide ? 0.30f : 0.16f, 48, Sw.ZL));
        l.controls.add(new Control("R", Control.Kind.SHOULDER, wide ? 0.91f : 0.88f, wide ? 0.13f : 0.07f, 48, Sw.R));
        l.controls.add(new Control("ZR", Control.Kind.SHOULDER, wide ? 0.91f : 0.88f, wide ? 0.30f : 0.16f, 48, Sw.ZR));
        l.controls.add(new Control("minus", Control.Kind.BUTTON, wide ? 0.40f : 0.40f, wide ? 0.90f : 0.93f, 40, Sw.MINUS));
        l.controls.add(new Control("plus", Control.Kind.BUTTON, wide ? 0.60f : 0.60f, wide ? 0.90f : 0.93f, 40, Sw.PLUS));
        l.controls.add(new Control("L3", Control.Kind.BUTTON, wide ? 0.25f : 0.34f, wide ? 0.56f : 0.50f, 40, Sw.LS));
        l.controls.add(new Control("R3", Control.Kind.BUTTON, wide ? 0.80f : 0.72f, wide ? 0.90f : 0.77f, 40, Sw.RS));
        l.find("L3").visible = false;     // a click is rarely needed; the player can show them in the editor
        l.find("R3").visible = false;
        return l;
    }
}
