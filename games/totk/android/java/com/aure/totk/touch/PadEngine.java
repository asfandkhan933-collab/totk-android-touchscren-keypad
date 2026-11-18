package com.aure.totk.touch;

import java.util.HashMap;
import java.util.Map;

/** Turns touches into the pad state the game reads. No Android classes, so it is tested on a plain JVM.
 * Sticks are fixed circles: a finger that lands in one stays with it until it lifts. Every other finger presses whatever
 * control it is over, and rolling onto the next control presses that one instead (as on a real pad). */
public final class PadEngine {
    public static final class Out {
        public long buttons; public int lx, ly, rx, ry;   // sticks -32767..32767, up positive (the Switch's convention)
        @Override public boolean equals(Object o) {
            if (!(o instanceof Out)) return false;
            Out p = (Out) o; return buttons == p.buttons && lx == p.lx && ly == p.ly && rx == p.rx && ry == p.ry;
        }
        @Override public int hashCode() { return (int) (buttons * 31 + lx * 17 + ly * 13 + rx * 7 + ry); }
    }

    private static final float STICK_CAPTURE = 1.3f;   // a finger within 1.3 x the stick's radius takes it
    private static final float STICK_DEADZONE = 0.06f; // of full travel; the game adds its own on top
    private static final float DPAD_CENTRE = 0.22f;    // inner part of the D-pad that presses nothing

    private PadLayout layout;
    private float w, h, density = 1f;
    private boolean swapAB;
    private final Map<Integer, float[]> fingers = new HashMap<>();      // id -> x, y
    private final Map<Integer, String> stickOf = new HashMap<>();       // id -> stick control id

    public void setLayout(PadLayout layout, float width, float height, float density, boolean swapAB) {
        this.layout = layout; this.w = width; this.h = height; this.density = density; this.swapAB = swapAB;
        stickOf.keySet().retainAll(fingers.keySet());
    }
    public void down(int id, float x, float y) {
        fingers.put(id, new float[] {x, y});
        if (layout == null) return;
        for (Control c : layout.controls) {
            if (!c.visible || c.kind != Control.Kind.STICK || stickOf.containsValue(c.id)) continue;
            if (dist(x, y, c) <= radius(c) * STICK_CAPTURE) { stickOf.put(id, c.id); break; }
        }
    }
    public void move(int id, float x, float y) { float[] f = fingers.get(id); if (f != null) { f[0] = x; f[1] = y; } }
    public void up(int id) { fingers.remove(id); stickOf.remove(id); }
    public void cancel() { fingers.clear(); stickOf.clear(); }
    public boolean idle() { return fingers.isEmpty(); }

    private float cx(Control c) { return c.x * w; }
    private float cy(Control c) { return c.y * h; }
    private float radius(Control c) { return c.sizeDp * density / 2f; }
    private float dist(float x, float y, Control c) { return (float) Math.hypot(x - cx(c), y - cy(c)); }

    /** Normalised distance of a point from the middle of a button (1 = on its rim); shoulders are wider than tall. */
    private float reach(float x, float y, Control c) {
        float r = radius(c);
        if (c.kind == Control.Kind.SHOULDER) {
            float hx = r * 1.7f, hy = r;   // half width, half height
            return Math.max(Math.abs(x - cx(c)) / hx, Math.abs(y - cy(c)) / hy);
        }
        return dist(x, y, c) / r;
    }

    public Out out() {
        Out o = new Out();
        if (layout == null) return o;
        for (Map.Entry<Integer, float[]> e : fingers.entrySet()) {
            float x = e.getValue()[0], y = e.getValue()[1];
            String stick = stickOf.get(e.getKey());
            if (stick != null) {
                Control c = layout.find(stick);
                if (c != null) { applyStick(o, c, x, y); continue; }
            }
            // The nearest button the finger is on (so two touching buttons never both fire from one finger).
            Control best = null; float bestReach = 1.05f;
            for (Control c : layout.controls) {
                if (!c.visible || c.kind == Control.Kind.STICK) continue;
                if (c.kind == Control.Kind.DPAD) { o.buttons |= dpadBits(x, y, c); continue; }
                float r = reach(x, y, c);
                if (r <= bestReach) { bestReach = r; best = c; }
            }
            if (best != null) {
                int bit = swapAB ? Sw.swapAB(best.target) : best.target;
                o.buttons |= 1L << bit;
            }
        }
        return o;
    }

    private void applyStick(Out o, Control c, float x, float y) {
        float r = radius(c);
        float dx = (x - cx(c)) / r, dy = (y - cy(c)) / r;
        float len = (float) Math.hypot(dx, dy);
        if (len > 1f) { dx /= len; dy /= len; len = 1f; }
        if (len <= STICK_DEADZONE) return;
        float scale = (len - STICK_DEADZONE) / (1f - STICK_DEADZONE) / len;   // dead zone, rescaled so full travel is still full
        int ax = Math.round(dx * scale * 32767f), ay = Math.round(-dy * scale * 32767f);
        if (c.id.equals("stickR")) { o.rx = ax; o.ry = ay; } else { o.lx = ax; o.ly = ay; }
    }

    private long dpadBits(float x, float y, Control c) {
        float r = radius(c), dx = x - cx(c), dy = y - cy(c), len = (float) Math.hypot(dx, dy);
        if (len < r * DPAD_CENTRE || len > r * 1.25f) return 0;
        double a = Math.toDegrees(Math.atan2(-dy, dx));   // 0 = right, 90 = up
        long bits = 0;
        if (Math.abs(angleDiff(a, 0)) < 62) bits |= 1L << Sw.RIGHT;
        if (Math.abs(angleDiff(a, 90)) < 62) bits |= 1L << Sw.UP;
        if (Math.abs(angleDiff(a, 180)) < 62) bits |= 1L << Sw.LEFT;
        if (Math.abs(angleDiff(a, -90)) < 62) bits |= 1L << Sw.DOWN;
        return bits;
    }
    private static double angleDiff(double a, double b) {
        double d = (a - b) % 360; if (d > 180) d -= 360; if (d < -180) d += 360; return d;
    }

    /** The stick's thumb position in 0..1 of travel, for drawing (0,0 when no finger holds it). */
    public float[] thumb(Control c) {
        for (Map.Entry<Integer, String> e : stickOf.entrySet()) {
            if (!e.getValue().equals(c.id)) continue;
            float[] f = fingers.get(e.getKey()); if (f == null) continue;
            float r = radius(c), dx = (f[0] - cx(c)) / r, dy = (f[1] - cy(c)) / r, len = (float) Math.hypot(dx, dy);
            if (len > 1f) { dx /= len; dy /= len; }
            return new float[] {dx, dy};
        }
        return new float[] {0f, 0f};
    }
}
