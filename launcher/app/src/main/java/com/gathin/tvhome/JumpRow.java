package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "jump back in": a card per pinned show (Pins) under the single row, the most recently opened app first
 * (LastOpened). At rest the cards peek above the bottom edge; focusing them slides the rows up (the host moves
 * them), the chosen card grows and a ring sits just outside it. A card shows the show's key art and its logo in
 * white; OK opens the pin's link in its app, or the app itself when the link no longer resolves. Up goes back
 * to the dock; the row's ends keep focus.
 */
final class JumpRow {
    interface Host {
        /** A card took focus: show that app on the shelf. */
        void onJumpFocused(String app);

        /** Focus entered (true) or left (false) the row: slide the rows up or back down. */
        void onJumpActive(boolean active);

        /** Up from a card: back to the dock. */
        void onJumpUp();

        void open(Intent intent);

        /** The dock ring's box in window pixels {x, y, w, h}: the card ring glides from there. */
        float[] dockRing();
    }

    // Projector layout, in dp (the screen is 960 x 540 dp): see the browser mock, projector-research/mock.
    static final float CARD_W_DP = 260, CARD_H_DP = 146, GAP_DP = 20;
    /** How far below the screen's bottom the resting row reaches: the cards peek, the rest waits off screen. */
    static final float BELOW_DP = 56;
    /** How far the rows slide up while a card is focused. */
    static final float LIFT_DP = 155;
    private static final float GROW = 1.06f, RING_OUT_DP = 6, RADIUS_DP = 9;
    /** Critically damped, response about 0.38 s: quick, no overshoot (a D-pad move carries no momentum). */
    static final float STIFFNESS = 273f;

    final FrameLayout view;
    private final LinearLayout cards;
    private final View ring;
    private final Context ctx;
    private final Host host;
    private final Map<String, Pins.Pin> pins;
    private final Map<String, Bitmap> pictures = new HashMap<>();
    private final float density;
    private int ringColor = 0xFFFFFFFF;
    private boolean active;

    JumpRow(Context ctx, Host host, Map<String, Pins.Pin> pins, float density) {
        this.ctx = ctx;
        this.host = host;
        this.pins = pins;
        this.density = density;
        view = new FrameLayout(ctx);
        view.setClipChildren(false);
        view.setClipToPadding(false);
        // The cards take focus only through focusFirst (Down from the dock): Android's own first focus at start-up
        // landed on them before the dock was laid out, and lifted the rows.
        view.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        LinearLayout column = new LinearLayout(ctx);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        TextView label = new TextView(ctx);
        label.setText(R.string.jump_back_in);
        label.setAllCaps(true);
        label.setTypeface(UiFont.get(ctx, R.string.font_medium));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        label.setLetterSpacing(0.27f);
        label.setTextColor(0x99FFFFFF);
        column.addView(label);
        cards = new LinearLayout(ctx);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.setClipChildren(false);
        cards.setPadding(0, dp(18), 0, 0);
        column.addView(cards);
        view.addView(column);
        ring = new View(ctx);   // outside the card, so its own clipping never cuts it
        ring.setVisibility(View.INVISIBLE);
        view.addView(ring, new FrameLayout.LayoutParams(0, 0));
        refresh();
    }

    FrameLayout.LayoutParams layoutParams() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        lp.bottomMargin = -dp(BELOW_DP);
        return lp;
    }

    /** Rebuilds the cards in recency order (on every return to Home: an app opened since moves to the front). */
    void refresh() {
        List<String> order = new ArrayList<>(pins.keySet());
        order.sort((a, b) -> Long.compare(LastOpened.get(ctx, b), LastOpened.get(ctx, a)));
        boolean hadFocus = cards.hasFocus();
        cards.removeAllViews();
        for (String app : order) {
            View card = card(app, pins.get(app));
            if (card != null) cards.addView(card);
        }
        view.setVisibility(cards.getChildCount() > 0 ? View.VISIBLE : View.GONE);
        if (hadFocus) focusFirst();
        else setActive(false);
    }

    boolean focusFirst() {
        view.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        return cards.getChildCount() > 0 && cards.getChildAt(0).requestFocus();
    }

    /** The ring's colour, from Chester's look (or focus_color). */
    void setRingColor(int argb) {
        ringColor = argb == 0 ? 0xFFFFFFFF : argb;
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(RADIUS_DP + RING_OUT_DP));
        d.setStroke(dp(2.5f), ringColor);
        ring.setBackground(d);
    }

    private void setActive(boolean on) {
        if (active == on) return;
        active = on;
        dimCards();
        if (!on) {
            ring.setVisibility(View.INVISIBLE);
            view.post(() -> { if (!active) view.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS); });
        }
        host.onJumpActive(on);
    }

    private View card(String app, Pins.Pin pin) {
        PackageManager pm = ctx.getPackageManager();
        try {
            pm.getApplicationInfo(app, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;   // not installed: no card
        }
        FrameLayout card = new FrameLayout(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(CARD_W_DP), dp(CARD_H_DP));
        lp.rightMargin = dp(GAP_DP);
        card.setLayoutParams(lp);
        card.setFocusable(true);
        card.setClickable(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(RADIUS_DP));
            }
        });
        card.setClipToOutline(true);
        card.setBackgroundColor(0xFF1C1715);

        Bitmap art = picture(app + "/card", pin.card.isEmpty() ? pin.scene : pin.card, 960);
        if (art != null) card.addView(image(art, ImageView.ScaleType.CENTER_CROP), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View shade = new View(ctx);   // darkens the lower part so the logo reads over any art
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x00000000, 0xB3000000}));
        card.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        Bitmap logo = picture(app + "/logo", pin.logo, 540);
        if (logo != null) {
            // fit a 135 x 48 dp box, so wide and tall logos carry the same weight
            float s = Math.min(dp(135) / (float) logo.getWidth(), dp(48) / (float) logo.getHeight());
            FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(Math.round(logo.getWidth() * s),
                    Math.round(logo.getHeight() * s), Gravity.BOTTOM | Gravity.START);
            llp.leftMargin = dp(16);
            llp.bottomMargin = dp(15);
            card.addView(image(logo, ImageView.ScaleType.FIT_CENTER), llp);
        } else {
            TextView name = new TextView(ctx);   // no logo yet: the show's name in the serif
            name.setText(pin.show);
            name.setTypeface(UiFont.get(ctx, R.string.font_serif_regular));
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            name.setTextColor(0xFFFFFFFF);
            FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
            nlp.leftMargin = dp(16);
            nlp.bottomMargin = dp(12);
            card.addView(name, nlp);
        }
        idleEdge(card);

        card.setOnFocusChangeListener((v, focused) -> {
            spring(v, DynamicAnimation.SCALE_X, focused ? GROW : 1f);
            spring(v, DynamicAnimation.SCALE_Y, focused ? GROW : 1f);
            if (!focused) return;
            boolean arriving = !active;
            setActive(true);
            dimCards();
            moveRing(v, arriving);
            host.onJumpFocused(app);
        });
        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            int i = cards.indexOfChild(v);
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    setActive(false);
                    host.onJumpUp();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    return i == 0;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    return i == cards.getChildCount() - 1;
                default:
                    return false;
            }
        });
        card.setOnClickListener(v -> {
            Intent open = intent(pm, app, pin);
            if (open == null) return;   // installed but not launchable
            LastOpened.record(ctx, app);
            host.open(open);
        });
        return card;
    }

    /** The mock's card dimming: the focused card full, the others 0.8 while the row is up, all 0.9 at rest. */
    private void dimCards() {
        for (int i = 0; i < cards.getChildCount(); i++) {
            View c = cards.getChildAt(i);
            c.animate().alpha(!active ? 0.9f : c.isFocused() ? 1f : 0.8f).setDuration(220).start();
        }
    }

    /** The ring glides to just outside the focused card, at its grown size; from the dock's ring when arriving. */
    private void moveRing(View card, boolean arriving) {
        float w = card.getWidth() * GROW + 2 * dp(RING_OUT_DP), h = card.getHeight() * GROW + 2 * dp(RING_OUT_DP);
        if (w <= 2 * dp(RING_OUT_DP)) {   // not laid out yet (first focus right after a refresh)
            card.post(() -> { if (card.isFocused()) moveRing(card, arriving); });
            return;
        }
        ViewGroup.LayoutParams lp = ring.getLayoutParams();
        if (lp.width != (int) w || lp.height != (int) h) {
            lp.width = (int) w;
            lp.height = (int) h;
            ring.setLayoutParams(lp);
        }
        // the card's centre in the row's coordinates (cards sit inside a column inside this frame)
        float cx = card.getLeft() + cards.getLeft() + ((View) cards.getParent()).getLeft() + card.getWidth() / 2f;
        float cy = card.getTop() + cards.getTop() + ((View) cards.getParent()).getTop() + card.getHeight() / 2f;
        float x = cx - w / 2f, y = cy - h / 2f;
        if (arriving || ring.getVisibility() != View.VISIBLE) {
            // start where the dock's ring is, at its size (scale about the centre), then glide down and grow
            float[] from = host.dockRing();
            int[] here = new int[2];
            view.getLocationInWindow(here);
            float fx = from[0] - here[0] + from[2] / 2f - w / 2f, fy = from[1] - here[1] + from[3] / 2f - h / 2f;
            ring.setX(fx);
            ring.setY(fy);
            ring.setScaleX(from[2] / w);
            ring.setScaleY(from[3] / h);
            ring.setVisibility(View.VISIBLE);
            spring(ring, DynamicAnimation.SCALE_X, 1f);
            spring(ring, DynamicAnimation.SCALE_Y, 1f);
        }
        spring(ring, DynamicAnimation.X, x);
        spring(ring, DynamicAnimation.Y, y);
    }

    static void spring(View v, DynamicAnimation.ViewProperty property, float to) {
        SpringAnimation a = new SpringAnimation(v, property, to);
        a.getSpring().setStiffness(STIFFNESS).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY);
        a.start();
    }

    /** The pin's link in its own app, or the app's start screen when there is no link or it no longer resolves. */
    static Intent intent(PackageManager pm, String app, Pins.Pin pin) {
        if (!pin.link.isEmpty()) {
            Intent link = new Intent(Intent.ACTION_VIEW, Uri.parse(pin.link)).setPackage(app);
            if (link.resolveActivity(pm) != null) return link;
        }
        Intent start = pm.getLeanbackLaunchIntentForPackage(app);
        return start != null ? start : pm.getLaunchIntentForPackage(app);
    }

    private Bitmap picture(String key, String file, int width) {
        if (!pictures.containsKey(key)) pictures.put(key, Pins.picture(ctx, file, width));
        return pictures.get(key);
    }

    private ImageView image(Bitmap b, ImageView.ScaleType scale) {
        ImageView i = new ImageView(ctx);
        i.setScaleType(scale);
        i.setImageBitmap(b);
        return i;
    }

    /** A hairline edge on every card, so dark art still reads as a card on a dark wall. */
    private void idleEdge(View card) {
        GradientDrawable edge = new GradientDrawable();
        edge.setCornerRadius(dp(RADIUS_DP));
        edge.setStroke(dp(1), 0x1FFFFFFF);
        card.setForeground(edge);
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
