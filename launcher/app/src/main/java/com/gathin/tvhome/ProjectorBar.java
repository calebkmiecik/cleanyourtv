package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.LinearLayout;

import org.json.JSONObject;

/**
 * Caleb's projector top bar (projector_bar): projector | sleep, settings. Icons only; the focused button grows a
 * label and a ring. The bar is pinned at its left edge, so a growing label never pushes its neighbours.
 * Sizes are the browser mock's pixels (a 1920-wide stage), which is dp x 2 on the projector.
 *
 * Sleep uses XGIMI's own timer (misckey listens for xgimi.intent.action.SLEEP_TIMER with no permission; it warns
 * 3 minutes before and turns the projector off). Its state is Settings.System "sleep_timer":
 * {"type": minutes chosen, "timer": seconds left or -1}.
 */
final class ProjectorBar {
    interface Host {
        void openProjectorDial();
        void openSettings();
        /** Down from the bar: back to the tile Up came from. */
        void barDown();
    }

    static final int[] SLEEP_STEPS = {30, 60, 90};
    private static final String SLEEP_ACTION = "xgimi.intent.action.SLEEP_TIMER";
    private final PathInterpolator ease = new PathInterpolator(0.22f, 1f, 0.36f, 1f);   // the mock's curve; not static, so unit tests load this class

    private final Context ctx;
    private final Host host;
    private final float u;   // projector pixels per mock pixel
    final Button projector, sleep, settings;
    private int fg = 0xFFF1EAE6, muted = 0xFFB8ACA6, ring = 0xFFCF9D97;

    ProjectorBar(Context ctx, LinearLayout bar, Host host, float density) {
        this.ctx = ctx;
        this.host = host;
        this.u = density / 2f;
        for (int i = 0; i < bar.getChildCount(); i++) bar.getChildAt(i).setVisibility(View.GONE);   // the stock pills
        bar.setGravity(Gravity.CENTER_VERTICAL);
        projector = add(bar, "projector", "projector", 0);
        View sep = new View(ctx);
        sep.setBackgroundColor(0x1AFFFFFF);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Math.round(2 * u), Math.round(28 * u));
        sl.setMargins(Math.round(10 * u), 0, Math.round(10 * u), 0);   // the mock: an 8 px gap plus 2 px
        bar.addView(sep, sl);
        this.sep = sep;
        sleep = add(bar, "sleep", "sleep", 0);
        settings = add(bar, "settings", "settings", Math.round(8 * u));
        projector.setOnClickListener(v -> host.openProjectorDial());
        sleep.setOnClickListener(v -> nextSleep());
        settings.setOnClickListener(v -> host.openSettings());
        View.OnKeyListener keys = (v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                host.barDown();
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (v == sleep) projector.requestFocus();
                else if (v == settings) sleep.requestFocus();
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (v == projector) sleep.requestFocus();
                else if (v == sleep) settings.requestFocus();
                return true;
            }
            return false;
        };
        projector.setOnKeyListener(keys);
        sleep.setOnKeyListener(keys);
        settings.setOnKeyListener(keys);
        pinLeft(bar);
        refreshSleep();
    }

    private final View sep;
    private View lastFocused;

    private Button add(LinearLayout bar, String icon, String label, int gapBefore) {
        Button b = new Button(ctx, icon, label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int out = Math.round(b.out);   // the ring's room around the button, which the margins give back
        lp.setMargins(gapBefore - out, -out, -out, -out);
        bar.addView(b, lp);
        b.setOnFocusChangeListener((v, has) -> {
            if (has) lastFocused = v;
        });
        return b;
    }

    /** Up from the tiles lands on the button used last (the projector button at first). */
    void focus() {
        (lastFocused != null ? lastFocused : projector).requestFocus();
    }

    boolean hasFocus() {
        return projector.hasFocus() || sleep.hasFocus() || settings.hasFocus();
    }

    /**
     * Once laid out at rest, the bar keeps that width, so a growing label spills right instead of pushing the
     * buttons to its left (Caleb, 11-02: "a weird bounce to the icons to the left"). It sits where the mock has
     * it: 96 px from the top and 128 px from the right edge, level with the greeting rather than the clock.
     */
    private void pinLeft(LinearLayout bar) {
        bar.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                if (r <= l) return;
                bar.removeOnLayoutChangeListener(this);
                View page = (View) bar.getParent().getParent();   // the column the header sits in, with its padding
                android.widget.RelativeLayout.LayoutParams lp = (android.widget.RelativeLayout.LayoutParams) bar.getLayoutParams();
                lp.removeRule(android.widget.RelativeLayout.ALIGN_BOTTOM);
                lp.removeRule(android.widget.RelativeLayout.ALIGN_TOP);
                lp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP);
                lp.topMargin = Math.round(96 * u) - page.getPaddingTop();
                lp.bottomMargin = 0;
                lp.setMarginEnd(Math.round(128 * u) - page.getPaddingEnd());
                lp.width = r - l;
                bar.setTranslationY(0f);
                bar.setLayoutParams(lp);
            }
        });
    }

    void setColors(int fg, int muted, int ring) {
        this.fg = fg;
        this.muted = muted;
        this.ring = ring;
        sep.setBackgroundColor((fg & 0xFFFFFF) | 0x1A000000);
        for (Button b : new Button[]{projector, sleep, settings}) b.invalidate();
    }

    // ---- sleep ---------------------------------------------------------------------------------------

    /** XGIMI's timer: {minutes chosen, minutes left}, both 0 when off. */
    private int[] sleepTimer() {
        try {
            String s = Settings.System.getString(ctx.getContentResolver(), "sleep_timer");
            if (s == null) return new int[2];
            JSONObject o = new JSONObject(s);
            int type = o.optInt("type", 0), seconds = o.optInt("timer", -1);
            return type > 0 && seconds > 0 ? new int[]{type, (seconds + 59) / 60} : new int[2];
        } catch (Exception e) {
            return new int[2];
        }
    }

    /** The step after the one chosen: off → 30 → 60 → 90 → off (a press never restarts the same step). */
    static int nextSleepStep(int chosen) {
        for (int step : SLEEP_STEPS) if (step > chosen) return step;
        return 0;
    }

    private void nextSleep() {
        int next = nextSleepStep(sleepTimer()[0]);
        ctx.sendBroadcast(new Intent(SLEEP_ACTION).putExtra("type", next).putExtra("from_page", "misckey"));
        setSleepLabel(next);
    }

    /** The label says what is set: "sleep" or "sleep 27m". */
    void refreshSleep() {
        setSleepLabel(sleepTimer()[1]);
    }

    private void setSleepLabel(int minutes) {
        sleep.setLabel(minutes > 0 ? "sleep " + minutes + "m" : "sleep");
    }

    // ---- one button ----------------------------------------------------------------------------------

    final class Button extends View {
        private final Path icon;
        private final boolean knobs;
        private String label;
        private float reveal;   // 0 at rest, 1 focused: the label's width, the fill and the ring
        private ValueAnimator anim;
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG),
                text = new Paint(Paint.ANTI_ALIAS_FLAG), ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final float h = 64 * u, pad = 17 * u, iconSize = 30 * u, gap = 12 * u, out = 12 * u + 2.5f * u * 2;

        Button(Context c, String iconId, String label) {
            super(c);
            this.icon = Icons.path(iconId);
            this.knobs = "settings".equals(iconId);
            this.label = label;
            setFocusable(true);
            setContentDescription(label);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(2.2f);   // in the icon's 30-unit box
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            text.setTextSize(24 * u);
            text.setLetterSpacing(1f / 24f);
            text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(5 * u);
        }

        void setLabel(String s) {
            if (s.equals(label)) return;
            label = s;
            setContentDescription(s);
            requestLayout();
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int hSpec) {
            float width = Math.max(h, 2 * pad + iconSize + reveal * (gap + text.measureText(label)));
            setMeasuredDimension(Math.round(width + 2 * out), Math.round(h + 2 * out));
        }

        @Override
        protected void onFocusChanged(boolean focused, int direction, android.graphics.Rect previous) {
            super.onFocusChanged(focused, direction, previous);
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(reveal, focused ? 1f : 0f);
            anim.setDuration(380);
            anim.setInterpolator(ease);
            anim.addUpdateListener(a -> {
                reveal = (float) a.getAnimatedValue();
                requestLayout();
                invalidate();
            });
            anim.start();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth() - 2 * out;
            RectF box = new RectF(out, out, out + w, out + h);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(withAlpha(fg, 0.12f * reveal));
            c.drawRoundRect(box, h / 2, h / 2, fill);
            if (reveal > 0f) {   // the ring sits outside the button, as around the tiles and cards
                float o = 12 * u + 2.5f * u;
                RectF r = new RectF(box.left - o, box.top - o, box.right + o, box.bottom + o);
                ringPaint.setColor(withAlpha(ring, reveal));
                c.drawRoundRect(r, h / 2 + o, h / 2 + o, ringPaint);
            }
            int ink = blend(withAlpha(muted, 0.8f), fg, reveal);
            c.save();
            c.translate(out + pad, out + (h - iconSize) / 2);
            c.scale(iconSize / 30f, iconSize / 30f);
            stroke.setColor(ink);
            c.drawPath(icon, stroke);
            if (knobs) {
                fill.setColor(ink);
                c.drawCircle(11, 8, 2.6f, fill);
                c.drawCircle(20, 15, 2.6f, fill);
                c.drawCircle(13, 22, 2.6f, fill);
            }
            c.restore();
            if (reveal > 0.02f) {
                c.save();
                c.clipRect(out + pad + iconSize, out, out + w - pad * (1 - reveal) + 1, out + h);
                text.setColor(withAlpha(ink, Math.min(1f, reveal * 1.5f)));
                Paint.FontMetrics fm = text.getFontMetrics();
                c.drawText(label, out + pad + iconSize + gap * reveal, out + h / 2 - (fm.ascent + fm.descent) / 2, text);
                c.restore();
            }
        }
    }

    static int withAlpha(int rgb, float a) {
        return (Math.round(Math.max(0f, Math.min(1f, a)) * ((rgb >>> 24) == 0 ? 255 : (rgb >>> 24))) << 24) | (rgb & 0xFFFFFF);
    }

    static int blend(int a, int b, float t) {
        int r = 0;
        for (int s = 0; s <= 24; s += 8) {
            int x = (a >> s) & 0xFF, y = (b >> s) & 0xFF;
            r |= Math.round(x + (y - x) * t) << s;
        }
        return r;
    }
}
