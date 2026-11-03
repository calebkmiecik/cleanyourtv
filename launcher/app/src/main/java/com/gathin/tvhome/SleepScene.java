package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Locale;

/**
 * The sleep screen (sleep_screen, layout dream_sleep): the browser mock's quiet screen for a dark room. A big serif
 * clock and the date in Chester's look of the moment, the weather where the theme's location is (Open-Meteo, no
 * key), and a note from Chester (chester_feed, agreed with the "Chester on the projector" chat 11-02:
 * {"note": {"text", "updated"} | null}). Shared by ClockActivity (Home left idle) and ClockDream (the screensaver).
 * Nothing on it shows an error: a failed fetch keeps what it had, or shows nothing.
 */
final class SleepScene {
    private static final long MINUTE_MS = 60_000L, REFRESH_MS = 15 * MINUTE_MS, NOTE_MAX_AGE_MS = 24 * 60 * MINUTE_MS;

    private final View root;
    private final TextView clock, ampm, date, temp, skyText, note, noteFrom;
    private final ImageView sky;
    private final View weatherRow, noteBlock;
    private final double[] location;   // null: no weather, Chester's fixed-clock looks
    private final String feedUrl;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int fg = 0xFFE8E2D4, muted = 0xFFBDB098;
    private Weather weather;
    private volatile boolean running;

    private final Runnable minute = new Runnable() {
        @Override
        public void run() {
            applyLook();
            handler.postDelayed(this, MINUTE_MS);
        }
    };
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            fetch();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    SleepScene(View root, Config cfg) {
        this.root = root;
        clock = root.findViewById(R.id.sleepClock);
        ampm = root.findViewById(R.id.sleepAmpm);
        date = root.findViewById(R.id.sleepDate);
        temp = root.findViewById(R.id.sleepTemp);
        skyText = root.findViewById(R.id.sleepSkyText);
        sky = root.findViewById(R.id.sleepSky);
        weatherRow = root.findViewById(R.id.sleepWeather);
        note = root.findViewById(R.id.sleepNoteText);
        noteFrom = root.findViewById(R.id.sleepNoteFrom);
        noteBlock = root.findViewById(R.id.sleepNote);
        location = ChesterLooks.parseLocation(cfg.location);
        feedUrl = cfg.chesterFeed;
        ClockFormat.apply(root, cfg.clockFormat);
        applyLook();
    }

    void start() {
        running = true;
        handler.removeCallbacksAndMessages(null);
        minute.run();
        refresh.run();
    }

    void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
    }

    /** Chester's look now: a darker background than Home's (a dark room stays dark), text a little softened. */
    private void applyLook() {
        int[] look = ChesterLooks.resolve(ZonedDateTime.now(), location == null ? Double.NaN : location[0],
                location == null ? Double.NaN : location[1]);
        int bg = mixToBlack(look[0], 0.3f);
        fg = ProjectorBar.withAlpha(0xFF000000 | look[1], 0.88f);
        muted = 0xFF000000 | look[2];
        root.setBackgroundColor(bg);
        clock.setTextColor(fg);
        temp.setTextColor(fg);
        for (TextView t : new TextView[]{ampm, date, skyText, note}) t.setTextColor(muted);
        noteFrom.setTextColor(ProjectorBar.withAlpha(muted, 0.7f));
        if (weather != null) sky.setImageDrawable(new LineIcon(weather.icon, muted));
    }

    static int mixToBlack(int rgb, float black) {
        int r = Math.round(((rgb >> 16) & 0xFF) * (1 - black)), g = Math.round(((rgb >> 8) & 0xFF) * (1 - black)),
                b = Math.round((rgb & 0xFF) * (1 - black));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // ---- network: weather and Chester's note, off the main thread --------------------------------------

    private void fetch() {
        new Thread(() -> {
            Weather w = null;
            String n = null;
            boolean noteKnown = false;
            if (location != null) {
                try {
                    w = parseWeather(get(weatherUrl(location[0], location[1])));
                } catch (Exception ignored) {
                    // offline or a bad answer: keep the last weather
                }
            }
            if (!feedUrl.isEmpty()) {
                try {
                    n = parseNote(get(feedUrl), System.currentTimeMillis());
                    noteKnown = true;
                } catch (Exception ignored) {
                    // not on the tailnet yet, or the feed is down: keep the last note
                }
            }
            Weather fw = w;
            String fn = n;
            boolean known = noteKnown;
            handler.post(() -> {
                if (!running) return;
                if (fw != null) showWeather(fw);
                if (known) showNote(fn);
            });
        }, "sleep-fetch").start();
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(5000);
        try (InputStream in = c.getInputStream()) {
            if (c.getResponseCode() != 200) throw new java.io.IOException("HTTP " + c.getResponseCode());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0 && out.size() < 64 * 1024; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    static String weatherUrl(double lat, double lng) {
        return String.format(Locale.ROOT, "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                + "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min"
                + "&temperature_unit=fahrenheit&timezone=auto&forecast_days=1", lat, lng);
    }

    static final class Weather {
        final int temp, hi, lo;
        final String icon, words;

        Weather(int temp, int hi, int lo, String icon, String words) {
            this.temp = temp;
            this.hi = hi;
            this.lo = lo;
            this.icon = icon;
            this.words = words;
        }
    }

    static Weather parseWeather(String json) throws org.json.JSONException {
        JSONObject j = new JSONObject(json), now = j.getJSONObject("current"), day = j.getJSONObject("daily");
        String[] sky = skyOf(now.getInt("weather_code"), now.optInt("is_day", 1) == 1);
        return new Weather((int) Math.round(now.getDouble("temperature_2m")),
                (int) Math.round(day.getJSONArray("temperature_2m_max").getDouble(0)),
                (int) Math.round(day.getJSONArray("temperature_2m_min").getDouble(0)), sky[0], sky[1]);
    }

    /** A WMO weather code (Open-Meteo) as {icon, words}. */
    static String[] skyOf(int code, boolean day) {
        if (code == 0 || code == 1) return new String[]{day ? "clear" : "night", code == 0 ? "Clear" : "Mostly clear"};
        if (code == 2) return new String[]{"partly", "Partly cloudy"};
        if (code == 3) return new String[]{"cloud", "Cloudy"};
        if (code == 45 || code == 48) return new String[]{"fog", "Fog"};
        if (code >= 51 && code <= 67 || code >= 80 && code <= 82) return new String[]{"rain", code < 60 ? "Drizzle" : "Rain"};
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return new String[]{"snow", "Snow"};
        return new String[]{"storm", "Storms"};
    }

    /**
     * The note to show, or null for none: the feed's {"note": null}, a note older than a day (a stalled job must not
     * leave an old note up; the server drops them too), or an empty one. Not JSON (the feed not deployed: the app's
     * HTML 404) throws, which keeps the last note.
     */
    static String parseNote(String json, long nowMs) throws org.json.JSONException {
        JSONObject n = new JSONObject(json).optJSONObject("note");
        if (n == null) return null;
        String text = n.optString("text").trim();
        if (text.isEmpty()) return null;
        String updated = n.optString("updated");
        if (!updated.isEmpty()) {
            try {
                if (nowMs - Instant.parse(updated).toEpochMilli() > NOTE_MAX_AGE_MS) return null;
            } catch (java.time.format.DateTimeParseException ignored) {
                // an odd date: trust the server's own age check
            }
        }
        return text;
    }

    private void showWeather(Weather w) {
        weather = w;
        sky.setImageDrawable(new LineIcon(w.icon, muted));
        temp.setText(w.temp + "°");
        skyText.setText(w.words + "\nH " + w.hi + "° · L " + w.lo + "°");
        weatherRow.setVisibility(View.VISIBLE);
    }

    private void showNote(String text) {
        note.setText(text);
        noteBlock.setVisibility(text == null ? View.GONE : View.VISIBLE);
    }

    /** One of Icons' line icons, stroked in a colour, scaled to its bounds. */
    static final class LineIcon extends Drawable {
        private final Path path;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        LineIcon(String id, int color) {
            path = Icons.path(id);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.6f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(color);
        }

        @Override
        public void draw(Canvas c) {
            c.save();
            c.translate(getBounds().left, getBounds().top);
            c.scale(getBounds().width() / 30f, getBounds().height() / 30f);
            c.drawPath(path, paint);
            c.restore();
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            paint.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
