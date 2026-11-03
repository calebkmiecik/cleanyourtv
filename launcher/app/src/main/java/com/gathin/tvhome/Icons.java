package com.gathin.tvhome;

import android.graphics.Path;

import androidx.core.graphics.PathParser;

/** Line icons for the projector bar and dial, in a 30-unit box, stroked (the mock's ICON set). */
final class Icons {
    private Icons() {
    }

    static Path path(String id) {
        String d;
        switch (id) {
            case "projector":
                d = "M6 9h18a3 3 0 0 1 3 3v7a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3v-7a3 3 0 0 1 3-3z"
                        + "M15.5 15.5a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0-7 0M7 13h5M7 17h3M8 22v2M22 22v2";
                break;
            case "zoom":
                d = "M3 10V3h7M20 3h7v7M27 20v7h-7M10 27H3v-7";
                break;
            case "rotation":
                d = "M25 15a10 10 0 1 1-3-7.1M23 3v6h-6";
                break;
            case "shift":
                d = "M15 3v24M3 15h24M11 7l4-4 4 4M11 23l4 4 4-4M7 11l-4 4 4 4M23 11l4 4-4 4";
                break;
            case "auto":
                d = "M15 4l2.2 6.8L24 13l-6.8 2.2L15 22l-2.2-6.8L6 13l6.8-2.2z"
                        + "M24 21l.9 2.6 2.6.9-2.6.9L24 28l-.9-2.6-2.6-.9 2.6-.9z";
                break;
            case "focus":
                d = "M4 15a11 11 0 1 0 22 0a11 11 0 1 0-22 0"
                        + "M26.0 15.0L14.1 19.9M20.5 24.5L10.3 16.7M9.5 24.5L11.2 11.8M4.0 15.0L15.9 10.1M9.5 5.5L19.7 13.3M20.5 5.5L18.8 18.2";
                break;
            case "keystone":
                d = "M6 7h18l3 16H3z";
                break;
            case "sleep":
                d = "M22 19A10 10 0 0 1 11 6a10 10 0 1 0 11 13z";
                break;
            // the sleep screen's weather (the mock's SKY set)
            case "clear":
                d = "M20.5 15a5.5 5.5 0 1 1-11 0a5.5 5.5 0 1 1 11 0M15 3v3M15 24v3M3 15h3M24 15h3M6.5 6.5l2.1 2.1M21.4 21.4l2.1 2.1M6.5 23.5l2.1-2.1M21.4 8.6l2.1-2.1";
                break;
            case "night":
                d = "M22 19A10 10 0 0 1 11 6a10 10 0 1 0 11 13z";
                break;
            case "cloud":
                d = "M9 23h13a5 5 0 0 0 0-10 7 7 0 0 0-13.5 1.5A4.3 4.3 0 0 0 9 23z";
                break;
            case "partly":
                d = "M10 8.5a5 5 0 0 1 8.6 2.4M10.5 3v2M4 9.5h2M5.6 4.6l1.4 1.4M11 25h11a4.5 4.5 0 0 0 0-9 6 6 0 0 0-11.5 1.3A3.9 3.9 0 0 0 11 25z";
                break;
            case "rain":
                d = "M9 19h13a5 5 0 0 0 0-10 7 7 0 0 0-13.5 1.5A4.3 4.3 0 0 0 9 19zM11 22l-1.5 4M16 22l-1.5 4M21 22l-1.5 4";
                break;
            case "snow":
                d = "M9 19h13a5 5 0 0 0 0-10 7 7 0 0 0-13.5 1.5A4.3 4.3 0 0 0 9 19zM11 23v.1M16 25v.1M21 23v.1M13.5 27v.1M18.5 27v.1";
                break;
            case "storm":
                d = "M9 19h13a5 5 0 0 0 0-10 7 7 0 0 0-13.5 1.5A4.3 4.3 0 0 0 9 19zM16 20l-3 5h4l-2 4";
                break;
            case "fog":
                d = "M5 12h20M7 17h18M5 22h16";
                break;
            case "settings":   // three sliders; the knobs are filled circles drawn by the caller
                d = "M4 8h22M4 15h22M4 22h22";
                break;
            default:
                return new Path();
        }
        return PathParser.createPathFromPathData(d);
    }
}
