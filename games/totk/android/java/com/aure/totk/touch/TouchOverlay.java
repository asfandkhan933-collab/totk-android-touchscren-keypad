package com.aure.totk.touch;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/** The on-screen gamepad, in a window of its own on top of the game. The game is a NativeActivity: its window hands every
 * input event straight to native code, so a view inside it would never see a touch. A separate, non-focusable window has
 * its own input, and leaves the game window focused (so a real controller and the keyboard keep working). */
public final class TouchOverlay {
    /** Receives the pad state each time it changes: buttons (bit n = Switch button n), then both sticks, up positive. */
    public interface Sink { void send(long buttons, int lx, int ly, int rx, int ry); }

    private final Activity activity;
    private final Sink sink;
    private final TouchStore store;
    private TouchPadView view;
    private boolean shown, full = true;
    private int attempts;

    public TouchOverlay(Activity activity, Sink sink) {
        this.activity = activity; this.sink = sink; this.store = new TouchStore(activity);
    }

    /** Call from onResume. */
    public void show() {
        if (shown) return;
        View decor = activity.getWindow().getDecorView();
        IBinder token = decor.getWindowToken();
        if (token == null) {                       // the game window is not attached yet: try again shortly
            if (++attempts < 40) decor.postDelayed(this::show, 100);
            return;
        }
        attempts = 0;
        try {
            if (view == null) view = new TouchPadView(activity, store, sink, this);
            full = true;
            activity.getWindowManager().addView(view, params(token, true));
            shown = true;
            view.setScreen(screenWidth(), screenHeight());
            if (!view.padOn()) setFull(false);   // controls off: only the small handle takes touches
        } catch (RuntimeException failed) {         // never let the pad stop the game from starting
            Log.w("TotkTouch", "could not show the touch controls", failed);
        }
    }
    /** Call from onPause: lifts every finger and removes the window. */
    public void hide() {
        if (!shown) return;
        shown = false;
        try { if (view != null) { view.release(); activity.getWindowManager().removeViewImmediate(view); } }
        catch (RuntimeException failed) { Log.w("TotkTouch", "could not remove the touch controls", failed); }
    }
    public void close() { hide(); view = null; }
    /** Call from onConfigurationChanged: the window may now be the folded or the unfolded screen. */
    public void onConfigurationChanged() {
        if (view != null && shown) view.setScreen(screenWidth(), screenHeight());
    }

    /** The whole pad when the controls are on, or only the small handle when they are off (so the game stays touchable). */
    void setFull(boolean wantFull) {
        if (!shown || view == null || full == wantFull) return;
        full = wantFull;
        try { activity.getWindowManager().updateViewLayout(view, params(view.getWindowToken(), wantFull)); }
        catch (RuntimeException failed) { Log.w("TotkTouch", "could not resize the touch controls", failed); }
    }

    private int screenWidth() { return bounds().width(); }
    private int screenHeight() { return bounds().height(); }
    private Rect bounds() {
        if (Build.VERSION.SDK_INT >= 30) return activity.getWindowManager().getCurrentWindowMetrics().getBounds();
        android.util.DisplayMetrics m = new android.util.DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getRealMetrics(m);
        return new Rect(0, 0, m.widthPixels, m.heightPixels);
    }

    private WindowManager.LayoutParams params(IBinder token, boolean wantFull) {
        float density = activity.getResources().getDisplayMetrics().density;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                wantFull ? WindowManager.LayoutParams.MATCH_PARENT : Math.round(TouchPadView.HANDLE_W_DP * density),
                wantFull ? WindowManager.LayoutParams.MATCH_PARENT : Math.round(TouchPadView.HANDLE_H_DP * density),
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        p.token = token;
        p.gravity = wantFull ? Gravity.TOP | Gravity.START : Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        p.setTitle("TotkTouchPad");
        if (Build.VERSION.SDK_INT >= 28) p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        return p;
    }
}
