package com.aure.totk.touch;

/** The Switch buttons as the native pad state numbers them (runtime/frontend_android.c SW_*, runtime.h RuntimeGamepadState). */
public final class Sw {
    private Sw() {}
    public static final int A = 0, B = 1, X = 2, Y = 3, LS = 4, RS = 5, L = 6, R = 7, ZL = 8, ZR = 9,
            PLUS = 10, MINUS = 11, LEFT = 12, UP = 13, RIGHT = 14, DOWN = 15;
    /** Names for the remap list, in the order the list shows them. */
    public static final int[] REMAPPABLE = {A, B, X, Y, L, R, ZL, ZR, PLUS, MINUS, LS, RS, UP, DOWN, LEFT, RIGHT};
    public static String name(int bit) {
        switch (bit) {
            case A: return "A"; case B: return "B"; case X: return "X"; case Y: return "Y";
            case LS: return "L3 (left stick click)"; case RS: return "R3 (right stick click)";
            case L: return "L"; case R: return "R"; case ZL: return "ZL"; case ZR: return "ZR";
            case PLUS: return "+"; case MINUS: return "−";
            case LEFT: return "D-pad left"; case UP: return "D-pad up"; case RIGHT: return "D-pad right"; case DOWN: return "D-pad down";
        }
        return "?";
    }
    /** The label printed on a button that sends this bit. */
    public static String label(int bit) {
        switch (bit) {
            case LS: return "L3"; case RS: return "R3"; case PLUS: return "+"; case MINUS: return "−";
            case LEFT: return "◀"; case UP: return "▲"; case RIGHT: return "▶"; case DOWN: return "▼";
        }
        return name(bit);
    }
    /** The A/B swap: every control that sends A sends B instead, and the other way round. */
    public static int swapAB(int bit) { return bit == A ? B : bit == B ? A : bit; }
}
