package com.ourtv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** A soft light in the focused app's own colour, behind the shelf. Redrawn only when the app changes. */
final class HeroGlow extends View {
    private final Paint p = new Paint(Paint.DITHER_FLAG);
    private int color;
    boolean rtl;

    HeroGlow(Context c) {
        super(c);
    }

    void setColor(int c) {
        color = c;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float x = rtl ? w * 0.22f : w * 0.78f, y = h * 0.42f, r = w * 0.48f;
        p.setShader(new RadialGradient(x, y, r, new int[] {Ui.alpha(color, 0.55f), Ui.alpha(color, 0.18f), Ui.alpha(color, 0f)},
                new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
    }
}
