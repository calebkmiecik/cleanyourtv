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
            case "settings":   // three sliders; the knobs are filled circles drawn by the caller
                d = "M4 8h22M4 15h22M4 22h22";
                break;
            default:
                return new Path();
        }
        return PathParser.createPathFromPathData(d);
    }
}
