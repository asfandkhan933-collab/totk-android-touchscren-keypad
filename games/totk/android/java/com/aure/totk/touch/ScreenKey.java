package com.aure.totk.touch;

/** Which of the two layouts a window gets. A foldable's cover screen is tall and narrow (about 2.2:1 in landscape),
 * its inner screen nearly square (about 1.1:1); the ratio of the window itself is the test, so it works whatever the
 * device calls its displays. */
public final class ScreenKey {
    private ScreenKey() {}
    public static final String FOLDED = "folded", UNFOLDED = "unfolded";
    public static final float WIDE_RATIO = 1.6f;
    public static String of(int widthPx, int heightPx) {
        int lo = Math.min(widthPx, heightPx), hi = Math.max(widthPx, heightPx);
        if (lo <= 0) return FOLDED;
        return (float) hi / lo >= WIDE_RATIO ? FOLDED : UNFOLDED;
    }
    public static boolean wide(String key) { return FOLDED.equals(key); }
}
