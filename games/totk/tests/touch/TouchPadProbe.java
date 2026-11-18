package com.aure.totk.touch;

/** Plain-JVM checks of the touch pad logic (tests/touch/run.sh). Exits non-zero on the first wrong answer. */
public final class TouchPadProbe {
    private static int checks;
    private static void ok(boolean cond, String what) {
        checks++;
        if (!cond) { System.err.println("FAIL: " + what); System.exit(1); }
    }
    private static boolean has(PadEngine.Out o, int bit) { return (o.buttons >> bit & 1) != 0; }

    public static void main(String[] args) {
        // Screen keys: Honor Magic V5 sizes (inner about 2352x2172, cover about 2376x1060), either orientation.
        ok(ScreenKey.of(2376, 1060).equals(ScreenKey.FOLDED), "cover screen -> folded");
        ok(ScreenKey.of(1060, 2376).equals(ScreenKey.FOLDED), "cover screen portrait -> folded");
        ok(ScreenKey.of(2352, 2172).equals(ScreenKey.UNFOLDED), "inner screen -> unfolded");
        ok(ScreenKey.of(2172, 2352).equals(ScreenKey.UNFOLDED), "inner screen portrait -> unfolded");
        ok(ScreenKey.of(0, 0).equals(ScreenKey.FOLDED), "no size -> folded, no crash");

        for (boolean wide : new boolean[] {true, false}) {
            PadLayout base = PadLayout.defaults(wide);
            ok(base.controls.size() == 15, "15 controls");
            for (Control c : base.controls) ok(c.x > 0 && c.x < 1 && c.y > 0 && c.y < 1, c.id + " on screen");
            // Round trip, including a moved, resized, hidden and remapped control.
            PadLayout edit = base.copy();
            edit.alpha = 0.35f;
            Control a = edit.find("A"); a.x = 0.5f; a.y = 0.25f; a.sizeDp = 80; a.visible = false; a.target = Sw.ZR;
            PadLayout back = PadLayout.fromText(edit.toText(), PadLayout.defaults(wide));
            ok(Math.abs(back.alpha - 0.35f) < 0.001f, "alpha round trip");
            Control b = back.find("A");
            ok(Math.abs(b.x - 0.5f) < 0.001f && Math.abs(b.y - 0.25f) < 0.001f && Math.abs(b.sizeDp - 80f) < 0.11f, "position and size round trip");
            ok(!b.visible && b.target == Sw.ZR, "hidden and remapped round trip");
            ok(back.find("stickL").x == base.find("stickL").x, "untouched controls unchanged");
        }
        // Damaged storage never throws and falls back to the defaults.
        PadLayout d = PadLayout.defaults(true);
        ok(PadLayout.fromText(null, d).controls.size() == 15, "null text");
        ok(PadLayout.fromText("garbage", d).find("A").x == d.find("A").x, "wrong header");
        PadLayout half = PadLayout.fromText("TPL1\nalpha=abc\nc,A,notanumber,1,1,1,1\nc,nosuch,1,1,1,1,1\nc,B,5000,5000\n", d);
        ok(half.find("A").x == d.find("A").x && half.alpha == d.alpha, "damaged lines ignored");
        ok(PadLayout.fromText("TPL1\nalpha=5\n", d).alpha == PadLayout.MIN_ALPHA, "alpha clamped low");
        PadLayout wild = PadLayout.fromText("TPL1\nc,A,99999,-5,100000,1,3\n", d);
        ok(wild.find("A").x == 1f && wild.find("A").y == 0f && wild.find("A").sizeDp == Control.MAX_DP, "position and size clamped");

        // Touch handling on a 2376x1060 screen at density 2.75 (about 440 dpi).
        PadLayout l = PadLayout.defaults(true);
        float w = 2376, h = 1060, den = 2.75f;
        PadEngine e = new PadEngine();
        e.setLayout(l, w, h, den, false);
        Control A = l.find("A"), B = l.find("B"), Y = l.find("Y"), sl = l.find("stickL"), sr = l.find("stickR"), dp = l.find("dpad");
        e.down(1, A.x * w, A.y * h);
        PadEngine.Out o = e.out();
        ok(o.buttons == 1L << Sw.A, "tap A presses only A");
        e.move(1, B.x * w, B.y * h);
        ok(e.out().buttons == 1L << Sw.B, "rolling from A to B releases A and presses B");
        e.up(1);
        ok(e.out().buttons == 0 && e.idle(), "release");
        e.setLayout(l, w, h, den, true);
        e.down(1, A.x * w, A.y * h);
        ok(e.out().buttons == 1L << Sw.B, "A/B swap: the A button sends B");
        e.up(1);
        e.down(1, B.x * w, B.y * h);
        ok(e.out().buttons == 1L << Sw.A, "A/B swap: the B button sends A");
        e.up(1);
        e.setLayout(l, w, h, den, false);

        // Hidden controls press nothing; remapped ones send the new button.
        l.find("A").visible = false;
        e.down(1, A.x * w, A.y * h);
        ok(!has(e.out(), Sw.A), "hidden control is not pressed");
        e.up(1);
        l.find("A").visible = true; l.find("Y").target = Sw.ZL;
        e.down(1, Y.x * w, Y.y * h);
        ok(e.out().buttons == 1L << Sw.ZL, "remapped Y sends ZL");
        e.up(1); l.find("Y").target = Sw.Y;

        // Two fingers: stick plus button, held together.
        float r = sl.sizeDp * den / 2;
        e.down(1, sl.x * w, sl.y * h);
        e.move(1, sl.x * w + r, sl.y * h);                  // full right
        e.down(2, A.x * w, A.y * h);
        o = e.out();
        ok(o.lx == 32767 && o.ly == 0 && has(o, Sw.A), "stick full right and A together");
        e.move(1, sl.x * w, sl.y * h - r * 3);              // far past the rim, straight up: clamps to full up
        o = e.out();
        ok(o.ly == 32767 && o.lx == 0, "stick up is positive and clamps at the rim");
        e.move(1, sl.x * w + r * 0.03f, sl.y * h);          // inside the dead zone
        ok(e.out().lx == 0, "dead zone");
        e.move(1, sl.x * w - r * 0.5f, sl.y * h + r * 0.5f);
        o = e.out();
        ok(o.lx < 0 && o.ly < 0, "down-left");
        e.up(1);
        o = e.out();
        ok(o.lx == 0 && o.ly == 0 && has(o, Sw.A), "stick released, A still held");
        e.cancel();
        ok(e.out().buttons == 0, "cancel clears everything");

        // The right stick feeds rx/ry; a finger that starts outside a stick never drags one.
        e.down(1, sr.x * w, sr.y * h); e.move(1, sr.x * w, sr.y * h + r);
        o = e.out();
        ok(o.ry == -32767 && o.lx == 0 && o.rx == 0, "right stick down is negative on ry");
        e.cancel();
        e.down(1, 20, 20); e.move(1, sl.x * w, sl.y * h);
        ok(e.out().lx == 0 && e.out().ly == 0, "a finger that did not start on a stick does not move it");
        e.cancel();

        // D-pad: four directions and a diagonal; the centre presses nothing.
        float dr = dp.sizeDp * den / 2;
        e.down(1, dp.x * w + dr * 0.8f, dp.y * h);
        ok(e.out().buttons == 1L << Sw.RIGHT, "D-pad right");
        e.move(1, dp.x * w, dp.y * h - dr * 0.8f);
        ok(e.out().buttons == 1L << Sw.UP, "D-pad up");
        e.move(1, dp.x * w - dr * 0.6f, dp.y * h + dr * 0.6f);
        ok(e.out().buttons == ((1L << Sw.LEFT) | (1L << Sw.DOWN)), "D-pad down-left diagonal");
        e.move(1, dp.x * w + 1, dp.y * h);
        ok(e.out().buttons == 0, "D-pad centre presses nothing");
        e.cancel();

        // Shoulder buttons are wide: a touch near the end of ZL still counts.
        Control zl = l.find("ZL");
        e.down(1, zl.x * w + zl.sizeDp * den * 1.4f / 1f * 0.5f * 1.2f, zl.y * h);
        ok(has(e.out(), Sw.ZL), "touch near the end of ZL presses ZL");
        e.cancel();

        // Two fingers on two sticks.
        e.down(1, sl.x * w, sl.y * h); e.down(2, sr.x * w, sr.y * h);
        e.move(1, sl.x * w + r, sl.y * h); e.move(2, sr.x * w - r, sr.y * h);
        o = e.out();
        ok(o.lx == 32767 && o.rx == -32767, "both sticks at once");
        System.out.println("touch pad: " + checks + " checks passed");
    }
}
