package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.KeyEvent;
import android.view.View;

import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * The projector dial (the projector button): the screen dims, "auto fit" sits in the middle and focus, zoom,
 * rotation, keystone and shift ring it. Arrows move to the nearest node that way, OK runs it, Back closes.
 * Drawn in the browser mock's pixels (a 1920 x 1080 stage), scaled to the view.
 */
final class ProjectorDial extends View {
    interface Host {
        void runDialTool(String id);
        void dialClosed();
    }

    private static final float CX = 960, CY = 500, R = 250, RING = 12;
    private static final String[] IDS = {"auto", "focus", "zoom", "rotation", "keystone", "shift"};
    private static final String[] LABELS = {"auto fit", "focus", "zoom", "rotation", "keystone", "shift"};

    private final Host host;
    private final float[] nx = new float[IDS.length], ny = new float[IDS.length], nr = new float[IDS.length];
    private final Path[] icons = new Path[IDS.length];
    private final float[] grow = new float[IDS.length];
    private int sel;
    private boolean open;
    private float shown;   // 0..1, the fade
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG),
            text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final FloatValueHolder rx = new FloatValueHolder(), ry = new FloatValueHolder(), rr = new FloatValueHolder();
    private final SpringAnimation sx, sy, sr;
    private int bg = 0xFF110E0D, fg = 0xFFF1EAE6, muted = 0xFFB8ACA6, ring = 0xFFCF9D97;

    ProjectorDial(Context c, Host host) {
        super(c);
        this.host = host;
        nx[0] = CX;
        ny[0] = CY;
        nr[0] = 105;
        for (int i = 1; i < IDS.length; i++) {
            double a = Math.toRadians(-90 + 72 * (i - 1));
            nx[i] = (float) (CX + R * Math.cos(a));
            ny[i] = (float) (CY + R * Math.sin(a));
            nr[i] = 66;
        }
        for (int i = 0; i < IDS.length; i++) icons[i] = Icons.path(IDS[i]);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        sx = spring(rx);
        sy = spring(ry);
        sr = spring(rr);
        setFocusable(true);
        setVisibility(GONE);
    }

    /** Critically damped, as the tiles' ring (stiffness 273: about 0.38 s). */
    private SpringAnimation spring(FloatValueHolder h) {
        SpringAnimation s = new SpringAnimation(h);
        s.setSpring(new SpringForce().setStiffness(273f).setDampingRatio(1f));
        s.addUpdateListener((a, v, vel) -> invalidate());
        return s;
    }

    void setColors(int bg, int fg, int muted, int ring) {
        this.bg = bg;
        this.fg = fg;
        this.muted = muted;
        this.ring = ring;
        invalidate();
    }

    boolean isOpen() {
        return open;
    }

    void open() {
        open = true;
        sel = 0;
        rx.setValue(nx[0]);
        ry.setValue(ny[0]);
        rr.setValue(ringRadius(0));
        java.util.Arrays.fill(grow, 1f);
        grow[0] = 1.06f;
        setVisibility(VISIBLE);
        requestFocus();
        fade(1f);
    }

    void close() {
        if (!open) return;
        open = false;
        fade(0f);
        host.dialClosed();
    }

    private void fade(float to) {
        android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(shown, to);
        a.setDuration(280);
        a.setInterpolator(new android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f));
        a.addUpdateListener(v -> {
            shown = (float) v.getAnimatedValue();
            invalidate();
        });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (!open) setVisibility(GONE);
            }
        });
        a.start();
    }

    private float ringRadius(int i) {
        return nr[i] * 1.06f + RING + 2.5f;
    }

    /** Every key while open: arrows choose, OK runs, Back closes. */
    boolean key(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP: move(0, -1); break;
            case KeyEvent.KEYCODE_DPAD_DOWN: move(0, 1); break;
            case KeyEvent.KEYCODE_DPAD_LEFT: move(-1, 0); break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: move(1, 0); break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                String id = IDS[sel];
                close();
                host.runDialTool(id);
                break;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                close();
                break;
            default:
                return false;   // volume and the like pass through
        }
        return true;
    }

    /** Spatial: the nearest node that lies the pressed way (as the mock's dialMove). */
    static int nearest(float[] xs, float[] ys, int from, int dx, int dy) {
        int best = -1;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < xs.length; i++) {
            if (i == from) continue;
            double vx = xs[i] - xs[from], vy = ys[i] - ys[from], d = Math.hypot(vx, vy), dot = (vx * dx + vy * dy) / d;
            if (dot < 0.3) continue;
            double score = d * (2 - dot);
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    private void move(int dx, int dy) {
        int to = nearest(nx, ny, sel, dx, dy);
        if (to < 0) return;
        grow[sel] = 1f;
        sel = to;
        grow[sel] = 1.06f;
        sx.animateToFinalPosition(nx[sel]);
        sy.animateToFinalPosition(ny[sel]);
        sr.animateToFinalPosition(ringRadius(sel));
        playSoundEffect(android.view.SoundEffectConstants.getContantForFocusDirection(
                dx > 0 ? FOCUS_RIGHT : dx < 0 ? FOCUS_LEFT : dy > 0 ? FOCUS_DOWN : FOCUS_UP));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (shown <= 0f) return;
        float s = getWidth() / 1920f;
        c.save();
        c.scale(s, s);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(ProjectorBar.withAlpha(bg, 0.92f * shown));
        c.drawRect(0, 0, 1920, 1080, fill);
        for (int i = 0; i < IDS.length; i++) {
            boolean on = i == sel;
            float r = nr[i] * grow[i];
            fill.setColor(ProjectorBar.withAlpha(fg, (on ? 0.15f : 0.06f) * shown));
            c.drawCircle(nx[i], ny[i], r, fill);
            stroke.setStrokeWidth(2);
            stroke.setColor(ProjectorBar.withAlpha(fg, 0.10f * shown));
            c.drawCircle(nx[i], ny[i], r - 1, stroke);
            int ink = ProjectorBar.withAlpha(on ? fg : muted, shown);
            boolean center = i == 0;
            float labelSize = center ? 24 : 21, iconSize = 38;
            float block = iconSize + 6 + labelSize + (center ? 6 + 16 : 0);
            float top = ny[i] - block / 2;
            c.save();
            c.translate(nx[i] - iconSize / 2, top);
            c.scale(iconSize / 30f, iconSize / 30f);
            stroke.setStrokeWidth(2);
            stroke.setColor(ink);
            c.drawPath(icons[i], stroke);
            c.restore();
            text.setColor(ink);
            text.setTextSize(labelSize);
            text.setLetterSpacing(1f / labelSize);
            c.drawText(LABELS[i], nx[i], top + iconSize + 6 + labelSize * 0.8f, text);
            if (center) {
                text.setColor(ProjectorBar.withAlpha(muted, shown));
                text.setTextSize(16);
                text.setLetterSpacing(0.5f / 16);
                c.drawText("keystone, then focus", nx[i], top + iconSize + 6 + labelSize + 6 + 16 * 0.8f, text);
            }
        }
        stroke.setStrokeWidth(5);
        stroke.setColor(ProjectorBar.withAlpha(ring, shown));
        c.drawCircle(rx.getValue(), ry.getValue(), rr.getValue(), stroke);
        text.setColor(ProjectorBar.withAlpha(muted, shown));
        text.setTextSize(22);
        text.setLetterSpacing(2f / 22);
        c.drawText("arrows choose · OK · Back closes", 960, 930 + 22, text);
        c.restore();
    }

    /**
     * The focus help: a sharp pattern over the whole picture (a big star, rings, fine lines, a star in each corner,
     * where the edges go soft first) while XGIMI's own focus control sits on top. Back or OK closes it.
     */
    static final class Pattern extends View {
        private final Path stars = new Path(), bars = new Path();
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG),
                text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Runnable onClose;

        Pattern(Context c, Runnable onClose) {
            super(c);
            this.onClose = onClose;
            star(960, 480, 300, 48);
            for (float[] p : new float[][]{{150, 150}, {1770, 150}, {150, 930}, {1770, 930}}) star(p[0], p[1], 90, 24);
            for (int k = 0; k < 9; k++) {
                float w = 1 + k * .5f;
                bars.addRect(470 + k * 14, 380, 470 + k * 14 + w, 580, Path.Direction.CW);
                bars.addRect(1450 - k * 14, 380, 1450 - k * 14 + w, 580, Path.Direction.CW);
            }
            fill.setColor(0xFFECE4DF);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(2);
            ringPaint.setColor(0xFFECE4DF);
            text.setColor(0xFFB8ACA6);
            text.setTextSize(26);
            text.setLetterSpacing(1f / 26);
            text.setTextAlign(Paint.Align.CENTER);
            setBackgroundColor(0xFF050404);
            setFocusable(true);
            setVisibility(GONE);
        }

        private void star(float cx, float cy, float r, int n) {
            for (int k = 0; k < n; k++) {
                double a0 = 2 * Math.PI * k / n, a1 = 2 * Math.PI * (k + .5) / n;
                stars.moveTo(cx, cy);
                stars.lineTo((float) (cx + r * Math.cos(a0)), (float) (cy + r * Math.sin(a0)));
                stars.lineTo((float) (cx + r * Math.cos(a1)), (float) (cy + r * Math.sin(a1)));
                stars.close();
            }
        }

        private boolean closeArmed, xgimiShown;
        private static final String SHOWN = "xgimi_focus_show";   // XGIMI's FocusAdjust: 1 while its control is up
        private final android.database.ContentObserver watch =
                new android.database.ContentObserver(new android.os.Handler(android.os.Looper.getMainLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        boolean up = android.provider.Settings.System.getInt(getContext().getContentResolver(), SHOWN, 0) == 1;
                        if (up) xgimiShown = true;
                        else if (xgimiShown) hide();   // XGIMI's control closed (it eats Back): the pattern goes with it
                    }
                };

        void show() {
            closeArmed = false;
            xgimiShown = false;
            setVisibility(VISIBLE);
            requestFocus();
            getContext().getContentResolver().registerContentObserver(
                    android.provider.Settings.System.getUriFor(SHOWN), false, watch);
        }


        void hide() {
            if (getVisibility() != VISIBLE) return;
            getContext().getContentResolver().unregisterContentObserver(watch);
            setVisibility(GONE);
            onClose.run();
        }

        /** Every key while shown: Back or OK closes; the rest belong to XGIMI's focus control. */
        boolean key(KeyEvent e) {
            int code = e.getKeyCode();
            boolean close = code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE
                    || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
            // close on a press made while shown: the release of the OK that opened it must not close it
            if (close && e.getAction() == KeyEvent.ACTION_DOWN) closeArmed = true;
            if (close && e.getAction() == KeyEvent.ACTION_UP && closeArmed) hide();
            return close || (code >= KeyEvent.KEYCODE_DPAD_UP && code <= KeyEvent.KEYCODE_DPAD_RIGHT);
        }

        @Override
        protected void onDraw(Canvas c) {
            float s = getWidth() / 1920f;
            c.save();
            c.scale(s, s);
            c.drawPath(stars, fill);
            for (float r : new float[]{330, 360, 380}) c.drawCircle(960, 480, r, ringPaint);
            c.drawPath(bars, fill);
            c.drawText("hold ‹ or › until the middle and all four corners look sharp · Back to close", 960, 40 + 26, text);
            c.restore();
        }
    }
}
