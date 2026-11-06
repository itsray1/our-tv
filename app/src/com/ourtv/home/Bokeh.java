package com.ourtv.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import java.util.Random;

/**
 * Soft out-of-focus lights drifting upward, like a photo of a party taken at night. One small
 * pre-painted sprite is reused for every light, so it stays smooth on the TV's chip.
 */
final class Bokeh extends View {
    private static final int COUNT = 34;
    private final float[] x = new float[COUNT], y = new float[COUNT], r = new float[COUNT], speed = new float[COUNT], sway = new float[COUNT];
    private final int[] tint = new int[COUNT];
    private final android.graphics.ColorFilter[] filters = new android.graphics.ColorFilter[COUNT];
    private final Bitmap sprite;
    private final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect src;
    private final RectF dst = new RectF();
    private long last;
    private boolean running;

    Bokeh(Context c, int[] colors) {
        super(c);
        sprite = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        g.setShader(new RadialGradient(48, 48, 48, new int[] {0xFFFFFFFF, 0xCCFFFFFF, 0x00FFFFFF}, new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        new Canvas(sprite).drawCircle(48, 48, 48, g);
        src = new Rect(0, 0, 96, 96);
        Random rnd = new Random(7);
        for (int i = 0; i < COUNT; i++) {
            x[i] = rnd.nextFloat() * Ui.W;
            y[i] = rnd.nextFloat() * Ui.H;
            // A few big, faint lights in front; many small, brighter ones behind.
            r[i] = i % 6 == 0 ? 70 + rnd.nextFloat() * 60 : 10 + rnd.nextFloat() * 26;
            speed[i] = (i % 6 == 0 ? 14 : 24) + rnd.nextFloat() * 26;
            sway[i] = rnd.nextFloat() * 6.28f;
            int col = colors[1 + i % 3];
            tint[i] = Ui.alpha(Season.mix(col, 0xFFFFFFFF, 0.25f), i % 6 == 0 ? 0.16f : 0.30f + rnd.nextFloat() * 0.35f);
            filters[i] = new android.graphics.PorterDuffColorFilter(tint[i] | 0xFF000000, android.graphics.PorterDuff.Mode.SRC_IN);
        }
    }

    void start() {
        running = true;
        last = 0;
        postInvalidateOnAnimation();
    }

    void stop() {
        running = false;
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        for (int i = 0; i < COUNT; i++) {
            y[i] -= speed[i] * dt;
            sway[i] += dt * 0.6f;
            if (y[i] < -r[i] * 2) {
                y[i] = Ui.H + r[i] * 2;
            }
            float cx = x[i] + (float) Math.sin(sway[i]) * 18;
            dst.set(cx - r[i], y[i] - r[i], cx + r[i], y[i] + r[i]);
            p.setColorFilter(filters[i]);
            p.setAlpha(android.graphics.Color.alpha(tint[i]));
            c.drawBitmap(sprite, src, dst, p);
        }
        if (running) postInvalidateOnAnimation();
    }
}
