package com.casezero.launcher;

import android.content.*;
import android.graphics.*;
import android.util.SparseArray;
import android.view.*;
import java.util.*;

/** Multi-pointer Xbox-layout overlay. One pointer owns one control until UP/CANCEL. */
final class TouchControlsView extends View {
    interface Sink { void send(int buttons, float lx, float ly, float rx, float ry, int lt, int rt); }
    private static final class Control {
        final String id, label; final int bit; final float defaultX, defaultY;
        float x, y;
        Control(String id, String label, int bit, float x, float y) {
            this.id = id; this.label = label; this.bit = bit; this.x = this.defaultX = x; this.y = this.defaultY = y;
        }
    }
    private static final class Press {
        final Control control;
        float x, y, lastX, lastY;
        Press(Control c, float x, float y) { control = c; this.x = lastX = x; this.y = lastY = y; }
    }
    private final List<Control> controls = new ArrayList<>();
    private final SparseArray<Press> pointers = new SparseArray<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SharedPreferences prefs;
    private final Sink sink;
    private final boolean editing;
    private boolean hiddenForPad;
    private float cameraX, cameraY;
    private final Runnable decay = () -> { cameraX = cameraY = 0; publish(); invalidate(); };
    TouchControlsView(Context context, boolean editing, Sink sink) {
        super(context); this.editing = editing; this.sink = sink;
        prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE);
        add("LS", "L", 0, .15f, .72f); add("RS", "R", 0, .72f, .72f);
        add("A", "A", 0x1000, .89f, .75f); add("B", "B", 0x2000, .955f, .59f);
        add("X", "X", 0x4000, .825f, .59f); add("Y", "Y", 0x8000, .89f, .43f);
        add("LB", "LB", 0x0100, .11f, .18f); add("RB", "RB", 0x0200, .89f, .18f);
        add("LT", "LT", 0, .25f, .18f); add("RT", "RT", 0, .75f, .18f);
        add("Back", "BACK", 0x0020, .43f, .88f); add("Start", "START", 0x0010, .57f, .88f);
        add("L3", "L3", 0x0040, .31f, .77f); add("R3", "R3", 0x0080, .68f, .91f);
        add("DU", "↑", 0x0001, .105f, .335f); add("DD", "↓", 0x0002, .105f, .525f);
        add("DL", "←", 0x0004, .055f, .43f); add("DR", "→", 0x0008, .155f, .43f);
        setContentDescription(context.getString(R.string.touch)); setFocusable(false);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }
    private void add(String id, String label, int bit, float x, float y) {
        Control c = new Control(id, label, bit, x, y);
        c.x = finite(prefs.getFloat("control." + id + ".x", x), x);
        c.y = finite(prefs.getFloat("control." + id + ".y", y), y);
        controls.add(c);
    }
    private static float finite(float n, float fallback) { return Float.isFinite(n) ? Math.max(.03f, Math.min(.97f, n)) : fallback; }
    private float radius(Control c) {
        float r = Math.min(getWidth(), getHeight()) * (c.id.equals("LS") || c.id.equals("RS") ? .105f : .048f);
        return r * Math.max(.7f, Math.min(1.5f, prefs.getInt("touch_size", 100) / 100f));
    }
    private boolean shown(Control c) { return editing || !c.id.equals("RS") || !prefs.getBoolean("swipe_camera", false); }
    private boolean available() { return editing || (prefs.getBoolean("touch", true) && !hiddenForPad); }
    void physicalController() {
        if (!editing && prefs.getBoolean("touch_auto", true)) { hiddenForPad = true; release(); invalidate(); }
    }
    void touchDevice() { if (hiddenForPad) { hiddenForPad = false; invalidate(); } }
    void saveLayout() {
        SharedPreferences.Editor edit = prefs.edit();
        for (Control c : controls) edit.putFloat("control." + c.id + ".x", c.x).putFloat("control." + c.id + ".y", c.y);
        edit.apply();
    }
    void resetLayout() {
        for (Control c : controls) { c.x = c.defaultX; c.y = c.defaultY; }
        release(); invalidate();
    }
    void release() { pointers.clear(); cameraX = cameraY = 0; removeCallbacks(decay); publish(); invalidate(); }
    @Override protected void onDetachedFromWindow() { release(); super.onDetachedFromWindow(); }
    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) { release(); }
    @Override protected void onDraw(Canvas canvas) {
        if (!available()) return;
        int alpha = editing ? 210 : Math.max(50, Math.min(230, prefs.getInt("touch_opacity", 65) * 255 / 100));
        for (Control c : controls) {
            if (!shown(c)) continue;
            float x = c.x * getWidth(), y = c.y * getHeight(), r = radius(c);
            boolean down = false;
            Press pressed = null;
            for (int i = 0; i < pointers.size(); ++i) if (pointers.valueAt(i).control == c) { down = true; pressed = pointers.valueAt(i); }
            paint.setStyle(Paint.Style.FILL); paint.setColor(down ? 0xffa4742e : 0xff101316); paint.setAlpha(alpha * 2 / 3);
            canvas.drawCircle(x, y, r, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Ui.dp(getContext(), 1.5f)); paint.setColor(down ? Ui.ACCENT : Ui.WHITE); paint.setAlpha(alpha);
            canvas.drawCircle(x, y, r, paint);
            paint.setStyle(Paint.Style.FILL); paint.setTextSize(r * (c.label.length() > 2 ? .55f : .85f)); paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(c.label, x, y - (paint.ascent() + paint.descent()) / 2, paint);
            if (pressed != null && !editing && (c.id.equals("LS") || c.id.equals("RS"))) {
                float dx = pressed.x - x, dy = pressed.y - y, len = (float)Math.hypot(dx, dy);
                float scale = len > r ? r / len : 1;
                paint.setAlpha(alpha); canvas.drawCircle(x + dx * scale, y + dy * scale, r * .30f, paint);
            }
        }
    }
    private Control hit(float x, float y) {
        // Buttons win over the larger analog hit areas at overlapping layouts.
        for (int i = controls.size() - 1; i >= 0; --i) {
            Control c = controls.get(i);
            if (shown(c) && Math.hypot(x - c.x * getWidth(), y - c.y * getHeight()) <= radius(c) * 1.2f) return c;
        }
        return null;
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!editing && !prefs.getBoolean("touch", true)) return false;
        touchDevice();
        int action = event.getActionMasked(), at = event.getActionIndex();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            float x = event.getX(at), y = event.getY(at);
            Control control = hit(x, y);
            if (control == null && (editing || !prefs.getBoolean("swipe_camera", false) || x < getWidth() / 2f)) {
                return pointers.size() != 0; // Don't consume the Activity's exit button/free screen.
            }
            pointers.put(event.getPointerId(at), new Press(control, x, y));
            getParent().requestDisallowInterceptTouchEvent(true);
            if (control != null && !editing) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        } else if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < event.getPointerCount(); ++i) {
                Press p = pointers.get(event.getPointerId(i)); if (p == null) continue;
                float x = event.getX(i), y = event.getY(i);
                if (editing && p.control != null) {
                    p.control.x = finite(x / getWidth(), p.control.x); p.control.y = finite(y / getHeight(), p.control.y);
                } else if (p.control == null) {
                    cameraX = Math.max(-1, Math.min(1, (x - p.lastX) / (getWidth() * .025f)));
                    cameraY = Math.max(-1, Math.min(1, -(y - p.lastY) / (getHeight() * .04f)));
                    removeCallbacks(decay); postDelayed(decay, 80);
                }
                p.x = p.lastX = x; p.y = p.lastY = y;
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            Press p = pointers.get(event.getPointerId(at));
            if (p != null && p.control == null) { cameraX = cameraY = 0; removeCallbacks(decay); }
            pointers.remove(event.getPointerId(at));
            if (action == MotionEvent.ACTION_UP) { release(); performClick(); }
        } else if (action == MotionEvent.ACTION_CANCEL) release();
        publish(); invalidate(); return true;
    }
    private void publish() {
        if (sink == null || editing) return;
        int buttons = 0, lt = 0, rt = 0; float lx = 0, ly = 0, rx = cameraX, ry = cameraY;
        for (int i = 0; i < pointers.size(); ++i) {
            Press p = pointers.valueAt(i); Control c = p.control; if (c == null) continue;
            buttons |= c.bit;
            if (c.id.equals("LT")) lt = 255;
            else if (c.id.equals("RT")) rt = 255;
            else if (c.id.equals("LS") || c.id.equals("RS")) {
                float x = (p.x - c.x * getWidth()) / radius(c), y = -(p.y - c.y * getHeight()) / radius(c);
                float len = (float)Math.hypot(x, y); if (len > 1) { x /= len; y /= len; }
                if (c.id.equals("LS")) { lx = x; ly = y; } else { rx = x; ry = y; }
            }
        }
        sink.send(buttons, lx, ly, rx, ry, lt, rt);
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
