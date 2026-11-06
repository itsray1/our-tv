package com.ourtv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Drawn icons for the top bar (no emoji): moon for Good night, note for sound, gear for settings. */
final class IconView extends View {
    static final int MOON = 0, NOTE = 1, GEAR = 2, INPUT = 3;
    private final int kind;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean selected;

    IconView(Context c, int kind) {
        super(c);
        this.kind = kind;
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    void setOn(boolean on) {
        selected = on;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), cx = w / 2, cy = getHeight() / 2f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(selected ? Ui.IVORY : 0x1FFFFFFF);
        c.drawCircle(cx, cy, w / 2, p);
        int ink = selected ? 0xFF0B0F17 : Ui.IVORY;
        p.setColor(ink);
        p.setStrokeWidth(w * 0.055f);
        float s = w * 0.22f;
        if (kind == MOON) {
            p.setStyle(Paint.Style.STROKE);
            Path path = new Path();
            path.addCircle(cx, cy, s, Path.Direction.CW);
            Path cut = new Path();
            cut.addCircle(cx + s * 0.55f, cy - s * 0.4f, s * 0.82f, Path.Direction.CW);
            path.op(cut, Path.Op.DIFFERENCE);
            p.setStyle(Paint.Style.FILL);
            c.drawPath(path, p);
        } else if (kind == NOTE) {
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(cx - s * 0.55f, cy + s * 0.7f, s * 0.34f, p);
            c.drawCircle(cx + s * 0.75f, cy + s * 0.45f, s * 0.34f, p);
            p.setStyle(Paint.Style.STROKE);
            Path path = new Path();
            path.moveTo(cx - s * 0.24f, cy + s * 0.7f);
            path.lineTo(cx - s * 0.24f, cy - s * 0.85f);
            path.lineTo(cx + s * 1.06f, cy - s * 1.1f);
            path.lineTo(cx + s * 1.06f, cy + s * 0.45f);
            c.drawPath(path, p);
        } else if (kind == INPUT) {
            // A screen with an arrow coming in: HDMI and other inputs.
            p.setStyle(Paint.Style.STROKE);
            c.drawRoundRect(new RectF(cx - s * 1.1f, cy - s * 0.8f, cx + s * 1.1f, cy + s * 0.65f), s * 0.18f, s * 0.18f, p);
            c.drawLine(cx - s * 0.45f, cy + s * 1.05f, cx + s * 0.45f, cy + s * 1.05f, p);
            c.drawLine(cx - s * 0.5f, cy - s * 0.08f, cx + s * 0.3f, cy - s * 0.08f, p);
            c.drawLine(cx, cy - s * 0.38f, cx + s * 0.3f, cy - s * 0.08f, p);
            c.drawLine(cx, cy + s * 0.22f, cx + s * 0.3f, cy - s * 0.08f, p);
        } else {
            p.setStyle(Paint.Style.STROKE);
            c.drawCircle(cx, cy, s * 0.42f, p);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i;
                c.drawLine(cx + (float) Math.cos(a) * s * 0.78f, cy + (float) Math.sin(a) * s * 0.78f,
                        cx + (float) Math.cos(a) * s * 1.08f, cy + (float) Math.sin(a) * s * 1.08f, p);
            }
            c.drawOval(new RectF(cx - s * 0.8f, cy - s * 0.8f, cx + s * 0.8f, cy + s * 0.8f), p);
        }
    }
}
