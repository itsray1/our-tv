package com.ourtv.home;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.TextView;
import java.util.HashMap;
import java.util.Map;

/** Design system: one type family per script, the three moods, and the shared shapes. */
final class Ui {
    static final int W = 1920, H = 1080;
    static final int IVORY = 0xFFF5F2EC, DIM = 0xBDF5F2EC;

    /** Mood palettes: background, then three glow colours. Aurora, Sunset, Graphite. */
    static final int[][] MOODS = {
        {0xFF04101A, 0xFF0FB5A6, 0xFF1D6BFF, 0xFFFF6A4D},
        {0xFF1C0B12, 0xFFFF5A4E, 0xFFFFB347, 0xFFE8366B},
        {0xFF0A0A0B, 0xFF3A3A40, 0xFF6A6A72, 0xFF25252A},
    };

    private static final Map<String, Typeface> FONTS = new HashMap<>();

    static Typeface font(Context c, String file, int weight) {
        String key = file + weight;
        Typeface t = FONTS.get(key);
        if (t == null) {
            t = new Typeface.Builder(c.getAssets(), "fonts/" + file).setFontVariationSettings("'wght' " + weight).build();
            FONTS.put(key, t);
        }
        return t;
    }

    /** Sora for Latin, Alexandria for Arabic; the system picks per character via fallback order. */
    static Typeface sans(Context c, boolean arabic, int weight) {
        return font(c, arabic ? "Alexandria.ttf" : "Sora.ttf", weight);
    }

    static TextView text(Context c, String s, Typeface tf, float px, int color) {
        TextView v = new TextView(c);
        v.setText(s);
        v.setTypeface(tf);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        v.setTextColor(color);
        v.setIncludeFontPadding(false);
        v.setSingleLine(true);
        return v;
    }

    static GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    static GradientDrawable ring(int stroke, int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setStroke(stroke, color);
        return d;
    }

    static void clip(View v, final float radius) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline o) {
                if (radius < 0) o.setOval(0, 0, view.getWidth(), view.getHeight());
                else o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        v.setClipToOutline(true);
    }

    static int measureWidth(View v) {
        v.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        return v.getMeasuredWidth();
    }

    static int alpha(int color, float a) {
        return Color.argb(Math.round(a * 255), Color.red(color), Color.green(color), Color.blue(color));
    }
}
