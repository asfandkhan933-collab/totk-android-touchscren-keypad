package com.aure.totk.touch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/** Draws the pad and turns touches into game input; in edit mode the same view moves, resizes, hides and remaps controls.
 * Everything that decides what a touch means is in {@link PadEngine} (tested on a plain JVM); this class only draws and
 * collects the touches. */
final class TouchPadView extends View {
    static final float HANDLE_W_DP = 64f, HANDLE_H_DP = 26f;

    private final Activity activity;
    private final TouchStore store;
    private final TouchOverlay.Sink sink;
    private final TouchOverlay host;
    private final PadEngine engine = new PadEngine();
    private final float density;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final ScaleGestureDetector scaler;

    private String screen = ScreenKey.FOLDED;
    private PadLayout layout;
    private boolean enabled, swapAB;
    private PadEngine.Out last = new PadEngine.Out();
    private final RectF handle = new RectF();
    private int handleFinger = -1;

    // edit mode
    private boolean edit;
    private Control selected;
    private float grabDx, grabDy;
    private boolean dragging;
    private final List<Bar> bars = new ArrayList<>();
    private int barFinger = -1;
    private Bar barDown;

    private static final class Bar {
        final String label; final Runnable action; final RectF area = new RectF();
        Bar(String label, Runnable action) { this.label = label; this.action = action; }
    }

    TouchPadView(Activity activity, TouchStore store, TouchOverlay.Sink sink, TouchOverlay host) {
        super(activity);
        this.activity = activity; this.store = store; this.sink = sink; this.host = host;
        density = activity.getResources().getDisplayMetrics().density;
        enabled = store.enabled(); swapAB = store.swapAB();
        layout = store.load(screen);
        stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(2f * density);
        text.setTextAlign(Paint.Align.CENTER); text.setFakeBoldText(true);
        scaler = new ScaleGestureDetector(activity, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                if (edit && selected != null) { selected.sizeDp *= d.getScaleFactor(); selected.clamp(); invalidate(); }
                return true;
            }
        });
        pushLayout();
    }
    boolean isEnabled() { return enabled; }

    // ---- state ----
    /** The window is now this size: pick the folded or unfolded layout for it. */
    void setScreen(int widthPx, int heightPx) {
        String key = ScreenKey.of(widthPx, heightPx);
        if (key.equals(screen) && layout != null) { pushLayout(); return; }
        if (edit) saveNow();
        screen = key;
        layout = store.load(screen);
        selected = null;
        pushLayout();
        invalidate();
    }
    private void pushLayout() { engine.setLayout(layout, getWidth(), getHeight(), density, swapAB); }
    private void saveNow() { store.save(screen, layout); }
    void release() { engine.cancel(); handleFinger = -1; barFinger = -1; publish(true); }

    private void publish(boolean force) {
        PadEngine.Out o = engine.out();
        if (force || !o.equals(last)) {
            last = o;
            sink.send(o.buttons, o.lx, o.ly, o.rx, o.ry);
        }
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        pushLayout();
        if (enabled && w > 200 * density && h > 200 * density) setScreen(w, h);
        updateExclusion();
    }
    private void updateExclusion() {   // keep the system's edge swipes from stealing a stick at the screen edge
        if (android.os.Build.VERSION.SDK_INT < 29 || !enabled) return;
        List<Rect> rects = new ArrayList<>();
        for (Control c : layout.controls) {
            if (!c.visible) continue;
            float r = c.sizeDp * density / 2f * (c.kind == Control.Kind.SHOULDER ? 1.7f : 1.1f);
            rects.add(new Rect(Math.round(c.x * getWidth() - r), Math.round(c.y * getHeight() - r),
                    Math.round(c.x * getWidth() + r), Math.round(c.y * getHeight() + r)));
        }
        try { setSystemGestureExclusionRects(rects); } catch (RuntimeException ignored) { /* the limit is a hint only */ }
    }

    // ---- drawing ----
    private int shade(float alpha, int rgb) { return Color.argb(Math.round(255 * Math.max(0f, Math.min(1f, alpha))), Color.red(rgb), Color.green(rgb), Color.blue(rgb)); }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (!enabled) { drawHandle(c, 0, 0, w, h, 0.9f); return; }
        float a = layout.alpha;
        for (Control ctl : layout.controls) {
            if (!ctl.visible && !edit) continue;
            drawControl(c, ctl, ctl.visible ? a : Math.min(a, 0.3f), ctl == selected);
        }
        float hw = HANDLE_W_DP * density, hh = HANDLE_H_DP * density;
        if (edit) drawBars(c); else drawHandle(c, (w - hw) / 2f, 0, (w + hw) / 2f, hh, Math.max(0.25f, a * 0.8f));
    }

    private void drawHandle(Canvas c, float l, float t, float r, float b, float a) {
        handle.set(l, t, r, b);
        fill.setColor(shade(a * 0.55f, 0x202020)); fill.setStyle(Paint.Style.FILL);
        c.drawRoundRect(handle, 8 * density, 8 * density, fill);
        stroke.setColor(shade(a, 0xFFFFFF));
        c.drawRoundRect(handle, 8 * density, 8 * density, stroke);
        float cy = (t + b) / 2f; stroke.setStrokeWidth(2f * density);
        for (int i = -1; i <= 1; i++) c.drawLine((l + r) / 2f + i * 9 * density - 3 * density, cy, (l + r) / 2f + i * 9 * density + 3 * density, cy, stroke);
    }

    private boolean pressed(int bit) { return (last.buttons >> bit & 1) != 0; }
    private int effective(Control c) { return swapAB ? Sw.swapAB(c.target) : c.target; }

    private void drawControl(Canvas c, Control ctl, float alpha, boolean sel) {
        float cx = ctl.x * getWidth(), cy = ctl.y * getHeight(), r = ctl.sizeDp * density / 2f;
        stroke.setStrokeWidth(2f * density);
        stroke.setColor(shade(sel ? 1f : alpha, sel ? 0xFFD54F : 0xFFFFFF));
        text.setColor(shade(Math.min(1f, alpha + 0.15f), 0xFFFFFF));
        if (!ctl.visible) stroke.setPathEffect(new android.graphics.DashPathEffect(new float[] {8 * density, 6 * density}, 0));
        switch (ctl.kind) {
            case STICK: {
                fill.setStyle(Paint.Style.FILL); fill.setColor(shade(alpha * 0.35f, 0x000000));
                c.drawCircle(cx, cy, r, fill); c.drawCircle(cx, cy, r, stroke);
                float[] t = engine.thumb(ctl);
                boolean held = t[0] != 0f || t[1] != 0f;
                fill.setColor(shade(alpha * (held ? 0.9f : 0.6f), 0xFFFFFF));
                c.drawCircle(cx + t[0] * r * 0.6f, cy + t[1] * r * 0.6f, r * 0.42f, fill);
                break;
            }
            case DPAD: {
                float arm = r * 0.36f, len = r;
                fill.setStyle(Paint.Style.FILL);
                int[] bits = {Sw.UP, Sw.DOWN, Sw.LEFT, Sw.RIGHT};
                for (int bit : bits) {
                    boolean down = pressed(bit);
                    fill.setColor(shade(alpha * (down ? 0.85f : 0.35f), down ? 0xFFFFFF : 0x000000));
                    if (bit == Sw.UP) rect.set(cx - arm, cy - len, cx + arm, cy - arm * 0.2f);
                    else if (bit == Sw.DOWN) rect.set(cx - arm, cy + arm * 0.2f, cx + arm, cy + len);
                    else if (bit == Sw.LEFT) rect.set(cx - len, cy - arm, cx - arm * 0.2f, cy + arm);
                    else rect.set(cx + arm * 0.2f, cy - arm, cx + len, cy + arm);
                    c.drawRoundRect(rect, arm * 0.3f, arm * 0.3f, fill); c.drawRoundRect(rect, arm * 0.3f, arm * 0.3f, stroke);
                }
                break;
            }
            case SHOULDER: {
                boolean down = pressed(effective(ctl));
                rect.set(cx - r * 1.7f, cy - r, cx + r * 1.7f, cy + r);
                fill.setStyle(Paint.Style.FILL); fill.setColor(shade(alpha * (down ? 0.8f : 0.35f), down ? 0xFFFFFF : 0x000000));
                c.drawRoundRect(rect, r * 0.5f, r * 0.5f, fill); c.drawRoundRect(rect, r * 0.5f, r * 0.5f, stroke);
                label(c, Sw.label(effective(ctl)), cx, cy, r * 0.9f);
                break;
            }
            default: {
                boolean down = pressed(effective(ctl));
                fill.setStyle(Paint.Style.FILL); fill.setColor(shade(alpha * (down ? 0.8f : 0.35f), down ? 0xFFFFFF : 0x000000));
                c.drawCircle(cx, cy, r, fill); c.drawCircle(cx, cy, r, stroke);
                label(c, Sw.label(effective(ctl)), cx, cy, r);
            }
        }
        stroke.setPathEffect(null);
    }
    private void label(Canvas c, String s, float cx, float cy, float r) {
        text.setTextSize(Math.max(10 * density, r * (s.length() > 1 ? 0.62f : 0.85f)));
        Paint.FontMetrics m = text.getFontMetrics();
        c.drawText(s, cx, cy - (m.ascent + m.descent) / 2f, text);
    }

    // ---- touches ----
    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!enabled) return handleOnlyTouch(e);
        if (edit) return editTouch(e);
        int action = e.getActionMasked(), idx = e.getActionIndex(), id = e.getPointerId(idx);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if (handle.contains(e.getX(idx), e.getY(idx)) && handleFinger < 0) { handleFinger = id; break; }
                engine.down(id, e.getX(idx), e.getY(idx));
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) engine.move(e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                if (id == handleFinger) {
                    handleFinger = -1;
                    if (handle.contains(e.getX(idx), e.getY(idx))) { engine.cancel(); publish(true); openMenu(); }
                } else engine.up(id);
                break;
            case MotionEvent.ACTION_CANCEL:
                engine.cancel(); handleFinger = -1;
                break;
            default: return true;
        }
        publish(false);
        invalidate();
        return true;
    }

    private boolean handleOnlyTouch(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_UP) openMenu();
        return true;
    }

    // ---- edit mode ----
    private void layoutBars() {
        bars.clear();
        bars.add(new Bar("Done", this::endEdit));
        bars.add(new Bar("See-through", this::opacityDialog));
        bars.add(new Bar(swapAB ? "A↔B: on" : "A↔B: off", () -> { swapAB = !swapAB; store.setSwapAB(swapAB); pushLayout(); invalidate(); }));
        bars.add(new Bar("Presets", this::presetsDialog));
        bars.add(new Bar("Reset", this::resetDialog));
        float h = 40 * density, gap = 6 * density, w = getWidth(), bw = (w - gap * (bars.size() + 1)) / bars.size();
        bw = Math.min(bw, 150 * density);
        float total = bw * bars.size() + gap * (bars.size() - 1), x = (w - total) / 2f;
        for (Bar b : bars) { b.area.set(x, gap, x + bw, gap + h); x += bw + gap; }
        if (selected != null) {
            List<Bar> low = new ArrayList<>();
            low.add(new Bar("Smaller", () -> resize(0.9f)));
            low.add(new Bar("Larger", () -> resize(1.1f)));
            low.add(new Bar(selected.visible ? "Hide" : "Show", () -> { selected.visible = !selected.visible; saveNow(); invalidate(); }));
            if (selected.remappable()) low.add(new Bar("Button: " + Sw.label(selected.target), this::remapDialog));
            float lw = Math.min(150 * density, (w - gap * (low.size() + 1)) / low.size());
            float lt = lw * low.size() + gap * (low.size() - 1), lx = (w - lt) / 2f, ly = getHeight() - gap - h;
            for (Bar b : low) { b.area.set(lx, ly, lx + lw, ly + h); lx += lw + gap; bars.add(b); }
        }
    }
    private void drawBars(Canvas c) {
        layoutBars();
        for (Bar b : bars) {
            fill.setStyle(Paint.Style.FILL); fill.setColor(b == barDown ? 0xFF5C6BC0 : 0xE6303030);
            c.drawRoundRect(b.area, 8 * density, 8 * density, fill);
            text.setColor(Color.WHITE); text.setTextSize(14 * density);
            Paint.FontMetrics m = text.getFontMetrics();
            c.drawText(b.label, b.area.centerX(), b.area.centerY() - (m.ascent + m.descent) / 2f, text);
        }
        text.setColor(Color.WHITE); text.setTextSize(12 * density);
        c.drawText(ScreenKey.wide(screen) ? "Editing the folded-screen layout" : "Editing the unfolded-screen layout",
                getWidth() / 2f, bars.get(0).area.bottom + 18 * density, text);
    }
    private Bar barAt(float x, float y) { for (Bar b : bars) if (b.area.contains(x, y)) return b; return null; }

    private boolean editTouch(MotionEvent e) {
        scaler.onTouchEvent(e);
        int action = e.getActionMasked(), idx = e.getActionIndex();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                float x = e.getX(), y = e.getY();
                Bar b = barAt(x, y);
                if (b != null) { barDown = b; barFinger = e.getPointerId(0); invalidate(); return true; }
                selected = pick(x, y);
                dragging = selected != null;
                if (selected != null) { grabDx = x - selected.x * getWidth(); grabDy = y - selected.y * getHeight(); }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (dragging && selected != null && e.getPointerCount() == 1 && !scaler.isInProgress() && barDown == null) {
                    selected.x = (e.getX() - grabDx) / getWidth(); selected.y = (e.getY() - grabDy) / getHeight();
                    selected.clamp(); invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP: {
                Bar b = barDown; barDown = null; barFinger = -1;
                if (b != null && b.area.contains(e.getX(), e.getY())) b.action.run();
                else if (dragging) saveNow();
                dragging = false; invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                barDown = null; dragging = false; saveNow(); invalidate();
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                saveNow();
                return true;
            default: return true;
        }
    }
    private Control pick(float x, float y) {
        Control best = null; float bestD = Float.MAX_VALUE;
        for (Control c : layout.controls) {
            float cx = c.x * getWidth(), cy = c.y * getHeight(), r = c.sizeDp * density / 2f;
            float d = c.kind == Control.Kind.SHOULDER
                    ? Math.max(Math.abs(x - cx) / (r * 1.7f), Math.abs(y - cy) / r)
                    : (float) Math.hypot(x - cx, y - cy) / r;
            if (d <= 1.1f && d < bestD) { bestD = d; best = c; }
        }
        return best;
    }
    private void resize(float f) { if (selected != null) { selected.sizeDp *= f; selected.clamp(); saveNow(); invalidate(); } }
    private void beginEdit() {
        if (!enabled) setEnabledState(true);
        engine.cancel(); publish(true);
        edit = true; selected = null; invalidate();
    }
    private void endEdit() { edit = false; selected = null; saveNow(); invalidate(); }

    // ---- menus ----
    private void openMenu() {
        final String[] items = {"Edit controls", enabled ? "Hide touch controls" : "Show touch controls", "See-through level…",
                swapAB ? "Swap A and B: on (turn off)" : "Swap A and B: off (turn on)", "Presets…", "Reset this screen's layout"};
        new AlertDialog.Builder(activity).setTitle("Touch controls").setItems(items, (d, which) -> {
            switch (which) {
                case 0: beginEdit(); break;
                case 1: setEnabledState(!enabled); break;
                case 2: if (enabled) opacityDialog(); break;
                case 3: swapAB = !swapAB; store.setSwapAB(swapAB); pushLayout(); invalidate(); break;
                case 4: presetsDialog(); break;
                default: resetDialog();
            }
        }).setNegativeButton("Close", null).show();
    }
    private void setEnabledState(boolean on) {
        enabled = on; store.setEnabled(on);
        engine.cancel(); publish(true);
        host.setFull(on);
        pushLayout(); invalidate();
    }
    private void opacityDialog() {
        SeekBar bar = new SeekBar(activity);
        bar.setMax(85);   // 0.15 .. 1.00
        bar.setProgress(Math.round((layout.alpha - PadLayout.MIN_ALPHA) * 100));
        bar.setPadding(Math.round(24 * density), Math.round(24 * density), Math.round(24 * density), Math.round(12 * density));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) { layout.alpha = PadLayout.MIN_ALPHA + p / 100f; invalidate(); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) { saveNow(); }
        });
        new AlertDialog.Builder(activity).setTitle("See-through level (left = more see-through)").setView(bar)
                .setPositiveButton("OK", (d, w) -> saveNow()).show();
    }
    private void remapDialog() {
        if (selected == null) return;
        final Control target = selected;
        String[] names = new String[Sw.REMAPPABLE.length];
        for (int i = 0; i < names.length; i++) names[i] = Sw.name(Sw.REMAPPABLE[i]);
        new AlertDialog.Builder(activity).setTitle("This button sends…").setItems(names, (d, which) -> {
            target.target = Sw.REMAPPABLE[which]; saveNow(); invalidate();
        }).setNegativeButton("Cancel", null).show();
    }
    private void resetDialog() {
        new AlertDialog.Builder(activity).setTitle("Reset the " + (ScreenKey.wide(screen) ? "folded" : "unfolded") + "-screen layout?")
                .setMessage("The other screen's layout and your saved presets are not changed.")
                .setPositiveButton("Reset", (d, w) -> { store.reset(screen); layout = store.load(screen); selected = null; pushLayout(); invalidate(); })
                .setNegativeButton("Cancel", null).show();
    }
    private void presetsDialog() {
        final List<String> names = store.presets();
        List<String> items = new ArrayList<>();
        items.add("Save this layout as a preset…");
        items.addAll(names);
        new AlertDialog.Builder(activity).setTitle("Presets (apply to either screen)").setItems(items.toArray(new String[0]), (d, which) -> {
            if (which == 0) savePresetDialog(); else presetActions(names.get(which - 1));
        }).setNegativeButton("Close", null).show();
    }
    private void savePresetDialog() {
        final EditText name = new EditText(activity);
        name.setInputType(InputType.TYPE_CLASS_TEXT); name.setSingleLine(true); name.setHint("Preset name");
        new AlertDialog.Builder(activity).setTitle("Save preset").setView(name).setPositiveButton("Save", (d, w) -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) return;
            store.savePreset(n, layout);
            Toast.makeText(activity, "Saved preset “" + n + "”", Toast.LENGTH_SHORT).show();
        }).setNegativeButton("Cancel", null).show();
    }
    private void presetActions(final String name) {
        new AlertDialog.Builder(activity).setTitle(name).setItems(new String[] {"Use on this screen", "Delete"}, (d, which) -> {
            if (which == 0) {
                PadLayout p = store.loadPreset(name, screen);
                if (p != null) { layout = p; selected = null; saveNow(); pushLayout(); invalidate(); }
            } else store.deletePreset(name);
        }).setNegativeButton("Cancel", null).show();
    }
}
