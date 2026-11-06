package com.ourtv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.View;

/**
 * The day drawn as the sun's real path: above the horizon from sunrise to sunset, shallower below it
 * for Fajr and Isha, with the sun at the current time.
 */
final class SunPath extends View {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG), label = new Paint(Paint.ANTI_ALIAS_FLAG);
    double[] t;
    double now;
    int next;
    boolean rtl;
    String[] names;

    SunPath(Context c, Typeface tf) {
        super(c);
        label.setTypeface(tf);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(30);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    private float x(double v, float w) {
        double a = t[0] - 0.7, b = t[5] + 0.7;
        float px = (float) (110 + (v - a) / (b - a) * (w - 220));
        return rtl ? w - px : px;
    }

    private float y(double v, float hor) {
        double rise = t[1], set = t[4];
        if (v >= rise && v <= set) return hor - (float) (Math.sin((v - rise) / (set - rise) * Math.PI) * (hor - 30));
        double away = v < rise ? rise - v : v - set;
        return hor + (float) (Math.abs(Math.sin(Math.min(1, away / 2.4) * Math.PI / 2)) * 44);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (t == null) return;
        float w = getWidth(), hor = getHeight() - 150;
        double a = t[0] - 0.7, b = t[5] + 0.7, sun = Math.max(a, Math.min(b, now));
        line.setColor(0x33FFFFFF);
        line.setStrokeWidth(2);
        c.drawLine(0, hor, w, hor, line);
        Path all = new Path(), done = new Path();
        for (int i = 0; i <= 90; i++) {
            double v = a + (b - a) * i / 90;
            if (i == 0) all.moveTo(x(v, w), y(v, hor));
            else all.lineTo(x(v, w), y(v, hor));
            if (v <= sun) {
                if (i == 0) done.moveTo(x(v, w), y(v, hor));
                else done.lineTo(x(v, w), y(v, hor));
            }
        }
        line.setColor(0x40FFFFFF);
        line.setStrokeWidth(3);
        c.drawPath(all, line);
        line.setColor(0xE6FFFFFF);
        line.setStrokeWidth(4);
        c.drawPath(done, line);
        for (int i = 0; i < 6; i++) {
            float px = x(t[i], w), py = y(t[i], hor);
            boolean on = i == next;
            dot.setColor(on ? 0xFFFFFFFF : 0xB3FFFFFF);
            c.drawCircle(px, py, on ? 12 : i == 1 ? 5 : 8, dot);
            label.setColor(on ? 0xFFFFFFFF : 0xB3FFFFFF);
            label.setFakeBoldText(on);
            c.drawText(names[i], px, hor + 96, label);
            c.drawText(Prayer.hmA(t[i], rtl), px, hor + 134, label);
        }
        float sx = x(sun, w), sy = y(sun, hor);
        dot.setColor(0x26FFFFFF);
        c.drawCircle(sx, sy, 46, dot);
        dot.setColor(0x4DFFFFFF);
        c.drawCircle(sx, sy, 26, dot);
        dot.setColor(0xFFFFF8E6);
        c.drawCircle(sx, sy, 14, dot);
    }
}
