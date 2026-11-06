package com.ourtv.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.view.View;

/**
 * The mood background. The glow is painted once into a bitmap and then only moved, so the
 * GPU composites a single layer per frame: blur, masks and blend modes stall this TV's Mali-G31.
 */
final class GlowView extends View {
    private static final int PAD = 80;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Rect dst = new Rect();
    private Bitmap bitmap;

    GlowView(Context c) {
        super(c);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    void setMood(int[] m) {
        Bitmap b = Bitmap.createBitmap((Ui.W + PAD * 2) / 2, (Ui.H + PAD * 2) / 2, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(m[0]);
        c.scale(0.5f, 0.5f);
        blob(c, m[1], 0.62f, 0.18f, 0.08f, 760);
        blob(c, m[2], 0.62f, 0.86f, 0.40f, 820);
        blob(c, m[3], 0.48f, 0.50f, 1.10f, 720);
        bitmap = b;
        invalidate();
    }

    private static void blob(Canvas c, int color, float strength, float fx, float fy, float r) {
        float x = PAD + fx * Ui.W, y = PAD + fy * Ui.H;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setShader(new RadialGradient(x, y, r, new int[] {Ui.alpha(color, strength), Ui.alpha(color, strength * 0.45f), Ui.alpha(color, 0)},
                new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, p);
    }

    /**
     * Slow drift of the whole layer, a few pixels a second. Moved four times a second, not sixty: at this
     * speed the eye cannot tell, and every frame the TV does not redraw leaves its small GPU free for Home.
     */
    private boolean drifting;
    private final Runnable step = new Runnable() {
        @Override
        public void run() {
            if (!drifting) return;
            double t = (android.os.SystemClock.uptimeMillis() % 52_000L) / 26_000.0;
            float v = (float) -Math.cos(Math.PI * t);
            setTranslationX(-PAD + v * 50);
            setTranslationY(-PAD + v * 30);
            postDelayed(this, 250);
        }
    };

    void startDrift() {
        if (drifting || getVisibility() != VISIBLE) return;
        drifting = true;
        post(step);
    }

    void stopDrift() {
        drifting = false;
        removeCallbacks(step);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (bitmap == null) return;
        dst.set(0, 0, getWidth(), getHeight());
        c.drawBitmap(bitmap, null, dst, paint);
    }
}
