package com.aure.totk.touch;

/** One on-screen control: where it is (fractions of the screen), how big (dp), whether it shows, what it sends. */
public final class Control {
    public enum Kind { STICK, DPAD, BUTTON, SHOULDER }
    public final String id;
    public final Kind kind;
    public float x, y;          // centre, 0..1 of the screen width / height
    public float sizeDp;        // diameter (stick, D-pad, button) or height (shoulder), in dp
    public boolean visible = true;
    public int target;          // the Switch button a BUTTON or SHOULDER sends; unused for sticks and the D-pad
    public Control(String id, Kind kind, float x, float y, float sizeDp, int target) {
        this.id = id; this.kind = kind; this.x = x; this.y = y; this.sizeDp = sizeDp; this.target = target;
    }
    public Control copy() {
        Control c = new Control(id, kind, x, y, sizeDp, target);
        c.visible = visible;
        return c;
    }
    public boolean remappable() { return kind == Kind.BUTTON || kind == Kind.SHOULDER; }
    public static final float MIN_DP = 28f, MAX_DP = 240f;
    public void clamp() {
        x = Math.max(0f, Math.min(1f, x)); y = Math.max(0f, Math.min(1f, y));
        sizeDp = Math.max(MIN_DP, Math.min(MAX_DP, sizeDp));
    }
}
