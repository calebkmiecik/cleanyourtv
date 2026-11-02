package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pinned shows: pins.json in the theme folder (pushed by tools/theme.sh), one entry per app with the show
 * watched there, its scene picture in scenes/ and a link that opens the show. Home shows the scene, dimmed,
 * behind the focused app and the show's name as the title. A missing or malformed file means no pins, and
 * Home's shelf works as before.
 *
 *   {"pins": [{"app": "com.netflix.ninja", "show": "Seinfeld", "scene": "seinfeld.jpg",
 *              "link": "http://www.netflix.com/title/70153373"}]}
 */
final class Pins {
    private Pins() {
    }

    static final class Pin {
        final String show, scene, link;

        Pin(String show, String scene, String link) {
            this.show = show;
            this.scene = scene;
            this.link = link;
        }
    }

    private static final long MAX_BYTES = 64 * 1024;   // as Config.read
    private static final int SCENE_WIDTH = 960;         // plenty for a dimmed, faded backdrop

    /** Pins by package name, in the file's order; empty when there are none. */
    static Map<String, Pin> load(Context ctx) {
        Map<String, Pin> out = new LinkedHashMap<>();
        File dir = Config.dir(ctx);
        File f = dir == null ? null : new File(dir, ThemeFiles.PINS);
        if (f == null || !f.isFile() || f.length() > MAX_BYTES) return out;
        try {
            JSONArray pins = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8))
                    .optJSONArray("pins");
            for (int i = 0; pins != null && i < pins.length(); i++) {
                JSONObject p = pins.optJSONObject(i);
                if (p == null) continue;
                String app = p.optString("app").trim(), show = p.optString("show").trim();
                String scene = p.optString("scene").trim(), link = p.optString("link").trim();
                if (app.isEmpty() || show.isEmpty()) continue;
                out.put(app, new Pin(show, ThemeFiles.collectionImage(scene) ? scene : "", link));
            }
        } catch (Exception e) {   // unreadable or not JSON: no pins rather than half of them
            out.clear();
        }
        return out;
    }

    /** The pin's scene picture, decoded about SCENE_WIDTH wide; null when it has none or it is missing. */
    static Bitmap scene(Context ctx, Pin pin) {
        File dir = Config.dir(ctx);
        if (pin.scene.isEmpty() || dir == null) return null;
        String path = new File(new File(dir, ThemeFiles.SCENES), pin.scene).getPath();
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        if (o.outWidth <= 0) return null;
        o.inJustDecodeBounds = false;
        o.inSampleSize = 1;   // the field starts at 0
        while (o.outWidth / (o.inSampleSize * 2) >= SCENE_WIDTH) o.inSampleSize *= 2;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(path, o);
    }

    /** Average brightness, 0 (black) to 1 (white). */
    static float meanLuma(Bitmap b) {
        Bitmap s = Bitmap.createScaledBitmap(b, 16, 9, true);
        long sum = 0;
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 16; x++) {
                int c = s.getPixel(x, y);
                sum += 299 * ((c >> 16) & 0xFF) + 587 * ((c >> 8) & 0xFF) + 114 * (c & 0xFF);
            }
        }
        if (s != b) s.recycle();
        return sum / (144f * 255f * 1000f);
    }

    /**
     * How opaque to draw a scene: {@code base} for a dark picture, less for a bright one (down to 40% of it),
     * so a sunny scene does not light up a dark room on a projector.
     */
    static float dim(float meanLuma, float base) {
        return base * Math.max(0.4f, Math.min(1f, 0.3f / Math.max(meanLuma, 0.01f)));
    }
}
