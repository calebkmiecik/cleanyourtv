package com.gathin.tvhome;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Chester's day-and-night looks, so Home changes colour with Chester (chester_looks). A port of Chester's
 * shared/theme: looks.ts (palettes), schedule.ts (which look when, 30-minute fades), calendar.ts (holiday
 * skins) and interpolate.ts (OKLCH mixing). Only the four colours Home uses are kept. Keep the palettes in
 * step with looks.ts by hand. Pure java.time maths, no Android types, so it runs in JVM tests.
 */
final class ChesterLooks {
    private ChesterLooks() {
    }

    // {background, foreground, mutedForeground, ring} as 0xRRGGBB, from looks.ts.
    static final int[] WORKPLACE_EVENING = {0x16141a, 0xe8e2d4, 0xbdb098, 0xd7a29b};
    static final int[] HARBOR_MORNING = {0x151921, 0xe6e9e4, 0xb7c1c1, 0xaecbdd};
    static final int[] STUDY_DAY = {0x131412, 0xece5d0, 0xb2b199, 0xc3ad85};
    static final int[] HARBOR_NIGHT = {0x0f1218, 0xd7dad6, 0x99a4ae, 0x9ab4c6};
    static final int[] PARLOR_DAY = {0x1b1620, 0xece2dc, 0xc1b4ac, 0xe8a08a};
    static final int[] PARLOR_NIGHT = {0x141019, 0xddd2cf, 0xa99c9f, 0xd98d77};

    private static final int KEEP = -1;
    /** Holiday skins from looks.ts SKINS: {background or KEEP, ring}. */
    private static int[] skin(String day) {
        switch (day) {
            case "christmas-eve": return new int[]{KEEP, 0xe2c489};
            case "christmas": return new int[]{KEEP, 0xcf8b7d};
            case "nye": return new int[]{0x0e0d12, 0xe6d3a3};
            case "nyd": return new int[]{KEEP, 0xead9b0};
            case "easter": return new int[]{KEEP, 0xb5d4a8};
            case "july4": return new int[]{0x121521, 0xe08d80};
            case "halloween": return new int[]{0x0e0c10, 0xe08a52};
            case "birthday": return new int[]{KEEP, 0xe8a08a};
            default: return null;
        }
    }

    private static final long MINUTE = 60_000L;
    static final long FADE_MS = 30 * MINUTE;            // full fade window, centred on a boundary
    private static final long HALF_FADE = FADE_MS / 2;
    private static final LocalTime NIGHT = LocalTime.of(22, 30);
    private static final LocalTime WEEKEND_START = LocalTime.of(15, 0);   // Friday

    /** The four colours at an instant: {background, foreground, muted, ring}, 0xRRGGBB. NaN lat/lng = fixed clock. */
    static int[] resolve(ZonedDateTime now, double lat, double lng) {
        long t = now.toInstant().toEpochMilli();
        ZoneId zone = now.getZone();
        long bestAt = 0, bestDist = Long.MAX_VALUE;
        for (int offset = -1; offset <= 1; offset++) {
            // every instant where the look can change: the day's phases, midnight (skins), Friday 15:00
            LocalDate d = now.toLocalDate().plusDays(offset);
            java.util.List<Long> bounds = new java.util.ArrayList<>();
            for (long b : dayTimes(d, zone, lat, lng)) bounds.add(b);
            bounds.add(at(d, LocalTime.MIDNIGHT, zone));
            if (d.getDayOfWeek() == DayOfWeek.FRIDAY) bounds.add(at(d, WEEKEND_START, zone));
            for (long b : bounds) {
                long dist = Math.abs(t - b);
                if (dist >= HALF_FADE || dist >= bestDist) continue;
                if (key(b - HALF_FADE, zone, lat, lng).equals(key(b + HALF_FADE, zone, lat, lng))) continue;
                bestAt = b;
                bestDist = dist;
            }
        }
        if (bestDist == Long.MAX_VALUE) return merged(t, zone, lat, lng);
        int[] from = merged(bestAt - HALF_FADE, zone, lat, lng), to = merged(bestAt + HALF_FADE, zone, lat, lng);
        double f = (t - (bestAt - HALF_FADE)) / (double) FADE_MS;
        int[] out = new int[4];
        for (int i = 0; i < 4; i++) out[i] = from[i] == to[i] ? from[i] : mix(from[i], to[i], f);
        return out;
    }

    /** "lat,lng" from the theme's location key, or null when missing or malformed. */
    static double[] parseLocation(String s) {
        if (s == null) return null;
        String[] p = s.split(",");
        if (p.length != 2) return null;
        try {
            double lat = Double.parseDouble(p[0].trim()), lng = Double.parseDouble(p[1].trim());
            return Math.abs(lat) <= 90 && Math.abs(lng) <= 180 ? new double[]{lat, lng} : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---- schedule.ts ----------------------------------------------------------------------------------

    /** {sunrise, midMorning, golden, sunset, night} in epoch ms for one local day. */
    static long[] dayTimes(LocalDate d, ZoneId zone, double lat, double lng) {
        int[] sun = Double.isNaN(lat) || Double.isNaN(lng) ? null : Sun.riseAndSet(d, lat, lng, zone);
        long sunrise, midMorning, golden, sunset;
        long midnight = at(d, LocalTime.MIDNIGHT, zone);
        if (sun != null) {
            sunrise = midnight + sun[0] * MINUTE;
            sunset = midnight + sun[1] * MINUTE;
            long noon = (sunrise + sunset) / 2;          // solar noon, near enough
            midMorning = (sunrise + noon) / 2;
            golden = sunset - 60 * MINUTE;               // golden hour = sunset - 1 h
        } else {                                         // fixed-clock fallback, as Chester's
            sunrise = at(d, LocalTime.of(7, 0), zone);
            midMorning = at(d, LocalTime.of(10, 0), zone);
            golden = at(d, LocalTime.of(17, 30), zone);
            sunset = at(d, LocalTime.of(18, 30), zone);
        }
        long night = at(d, NIGHT, zone);
        if (midMorning <= sunrise) midMorning = sunrise + MINUTE;
        if (golden <= midMorning) golden = midMorning + MINUTE;
        if (night <= golden) golden = night - MINUTE;
        return new long[]{sunrise, midMorning, golden, sunset, night};
    }

    private static int[] lookAt(long t, ZoneId zone, double lat, double lng) {
        ZonedDateTime z = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(t), zone);
        long[] dt = dayTimes(z.toLocalDate(), zone, lat, lng);
        if (isWeekend(z)) return t >= dt[0] && t < dt[3] ? PARLOR_DAY : PARLOR_NIGHT;
        if (t < dt[0]) return HARBOR_NIGHT;
        if (t < dt[1]) return HARBOR_MORNING;
        if (t < dt[2]) return STUDY_DAY;
        if (t < dt[4]) return WORKPLACE_EVENING;
        return HARBOR_NIGHT;
    }

    /** Weekend = Friday 15:00 to Sunday 22:30, local. */
    private static boolean isWeekend(ZonedDateTime z) {
        LocalTime time = z.toLocalTime();
        switch (z.getDayOfWeek()) {
            case SATURDAY: return true;
            case FRIDAY: return !time.isBefore(WEEKEND_START);
            case SUNDAY: return time.isBefore(NIGHT);
            default: return false;
        }
    }

    private static int[] merged(long t, ZoneId zone, double lat, double lng) {
        int[] look = lookAt(t, zone, lat, lng).clone();
        String day = specialDay(ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(t), zone).toLocalDate());
        int[] s = day == null ? null : skin(day);
        if (s != null) {
            if (s[0] != KEEP) look[0] = s[0];
            look[3] = s[1];
        }
        return look;
    }

    /** What makes two sides of a boundary differ: the look and the holiday skin. */
    private static String key(long t, ZoneId zone, double lat, double lng) {
        int[] look = lookAt(t, zone, lat, lng);
        return Integer.toHexString(look[0]) + "/"
                + specialDay(ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(t), zone).toLocalDate());
    }

    private static long at(LocalDate d, LocalTime time, ZoneId zone) {
        return d.atTime(time).atZone(zone).toInstant().toEpochMilli();
    }

    // ---- calendar.ts ----------------------------------------------------------------------------------

    /** The holiday skin for a local date, or null. Christmas week: Eve 24, Christmas 25-30, NYE 31, NYD 1. */
    static String specialDay(LocalDate d) {
        int m = d.getMonthValue(), day = d.getDayOfMonth();
        if (m == 12 && day == 24) return "christmas-eve";
        if (m == 12 && day == 31) return "nye";
        if (m == 12 && day >= 25) return "christmas";
        if (m == 1 && day == 1) return "nyd";
        if (m == 7 && day == 4) return "july4";
        if (m == 10 && day == 31) return "halloween";
        if (m == 10 && day == 16) return "birthday";
        return d.equals(easterSunday(d.getYear())) ? "easter" : null;
    }

    /** Anonymous Gregorian computus. */
    static LocalDate easterSunday(int year) {
        int a = year % 19, b = year / 100, c = year % 100, d = b / 4, e = b % 4, f = (b + 8) / 25;
        int g = (b - f + 1) / 3, h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7, m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31, day = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(year, month, day);
    }

    // ---- interpolate.ts: OKLCH mixing (Björn Ottosson's OKLab constants) ------------------------------

    /** Mixes two 0xRRGGBB colours in OKLCH, hue the short way round; f clamped to [0, 1]. */
    static int mix(int from, int to, double f) {
        double t = Math.max(0, Math.min(1, f));
        if (t == 0) return from;
        if (t == 1) return to;
        double[] a = oklch(from), b = oklch(to);
        double ha = a[1] < 0.004 ? b[2] : a[2], hb = b[1] < 0.004 ? a[2] : b[2];
        double dh = hb - ha;
        if (dh > 180) dh -= 360;
        if (dh < -180) dh += 360;
        return rgb(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, ((ha + dh * t) % 360 + 360) % 360);
    }

    static double[] oklch(int rgb) {
        double r = lin((rgb >> 16) & 0xFF), g = lin((rgb >> 8) & 0xFF), b = lin(rgb & 0xFF);
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        double L = 0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s;
        double A = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s;
        double B = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s;
        return new double[]{L, Math.hypot(A, B), (Math.toDegrees(Math.atan2(B, A)) + 360) % 360};
    }

    static int rgb(double L, double C, double H) {
        double A = C * Math.cos(Math.toRadians(H)), B = C * Math.sin(Math.toRadians(H));
        double l = Math.pow(L + 0.3963377774 * A + 0.2158037573 * B, 3);
        double m = Math.pow(L - 0.1055613458 * A - 0.0638541728 * B, 3);
        double s = Math.pow(L - 0.0894841775 * A - 1.291485548 * B, 3);
        return (srgb(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s) << 16)
                | (srgb(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s) << 8)
                | srgb(-0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s);
    }

    private static double lin(int c8) {
        double c = c8 / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static int srgb(double c) {
        double v = c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
        return (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
    }
}
