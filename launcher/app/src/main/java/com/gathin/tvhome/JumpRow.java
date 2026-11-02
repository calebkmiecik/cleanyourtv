package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "jump back in": a card per pinned show (Pins) under the single row, the most recently opened app first
 * (LastOpened). A card opens the pin's link in its app, one press from Resume, or the app itself when the
 * link is missing or no longer resolves. Up goes back to the dock; the row's ends keep focus.
 */
final class JumpRow {
    interface Host {
        /** A card took focus: show that app on the shelf. */
        void onJumpFocused(String app);

        /** Up from a card: back to the dock. */
        void onJumpUp();

        void open(Intent intent);
    }

    /** What the row takes from the bottom of the screen, label and cards included. */
    static final float HEIGHT_DP = 150;
    private static final float CARD_W_DP = 200, CARD_H_DP = 112.5f, GAP_DP = 16;

    final LinearLayout view;
    private final LinearLayout cards;
    private final Context ctx;
    private final Host host;
    private final Map<String, Pins.Pin> pins;
    private final Map<String, Bitmap> scenes = new HashMap<>();
    private final float density;
    private int ringColor = 0xFFFFFFFF;

    JumpRow(Context ctx, Host host, Map<String, Pins.Pin> pins, float density) {
        this.ctx = ctx;
        this.host = host;
        this.pins = pins;
        this.density = density;
        view = new LinearLayout(ctx);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setClipChildren(false);
        view.setClipToPadding(false);
        TextView label = new TextView(ctx);
        label.setText(R.string.jump_back_in);
        label.setTypeface(UiFont.get(ctx, R.string.font_medium));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setLetterSpacing(0.25f);
        label.setTextColor(0x99FFFFFF);
        view.addView(label);
        cards = new LinearLayout(ctx);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.setClipChildren(false);
        cards.setPadding(0, dp(10), 0, 0);
        view.addView(cards);
        refresh();
    }

    FrameLayout.LayoutParams layoutParams() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        lp.bottomMargin = dp(28);
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
    }

    boolean focusFirst() {
        return cards.getChildCount() > 0 && cards.getChildAt(0).requestFocus();
    }

    /** The highlight's colour, from Chester's look (or focus_color). */
    void setRingColor(int argb) {
        ringColor = argb == 0 ? 0xFFFFFFFF : argb;
        for (int i = 0; i < cards.getChildCount(); i++) ring(cards.getChildAt(i));
    }

    private View card(String app, Pins.Pin pin) {
        PackageManager pm = ctx.getPackageManager();
        String appName;
        try {
            appName = pm.getApplicationLabel(pm.getApplicationInfo(app, 0)).toString();
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
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(12));
            }
        });
        card.setClipToOutline(true);
        card.setBackgroundColor(0xFF1C1715);

        Bitmap scene = scenes.get(app);
        if (scene == null && !scenes.containsKey(app)) {
            scene = Pins.scene(ctx, pin);
            scenes.put(app, scene);
        }
        if (scene != null) {
            ImageView image = new ImageView(ctx);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setImageBitmap(scene);
            card.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        View shade = new View(ctx);   // darkens the lower half so the names read over any scene
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x00000000, 0xD9000000}));
        card.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout names = new LinearLayout(ctx);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(dp(12), 0, dp(12), dp(8));
        names.addView(text(pin.show, R.string.font_serif_regular, 17, 0xFFFFFFFF));
        names.addView(text(appName, R.string.font_medium, 11, 0xB3FFFFFF));
        card.addView(names, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        card.setOnFocusChangeListener((v, focused) -> {
            ring(v);
            v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f).setDuration(160).start();
            if (focused) host.onJumpFocused(app);
        });
        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            int i = cards.indexOfChild(v);
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
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
        ring(card);
        return card;
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

    private void ring(View card) {
        GradientDrawable stroke = new GradientDrawable();
        stroke.setCornerRadius(dp(12));
        stroke.setStroke(dp(card.isFocused() ? 3 : 1), card.isFocused() ? ringColor : 0x26FFFFFF);
        card.setForeground(stroke);
    }

    private TextView text(String s, int font, float sp, int color) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTypeface(UiFont.get(ctx, font));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
