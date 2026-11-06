package com.ourtv.home;

import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

/**
 * Memories: the family's photos as a slow film. Each photo drifts and zooms a little (Ken Burns), the
 * next one fades in over it, and a quiet caption says what, when and who. Portrait photos are shown whole
 * over a soft, blurred copy of themselves. In clock mode it plays behind the time when the TV is idle.
 */
final class MemoriesScreen {
    static final long SLIDE_MS = 9000;
    private final MainActivity a;
    private final boolean clockMode;
    private final FrameLayout root, stage;
    private final TextView caption, when, counter, pausedTag;
    private TextView clock, clockSub;
    private List<Memories.Photo> photos;
    private int index = -1;
    private boolean paused;
    private final Runnable advance = () -> show(1);

    MemoriesScreen(MainActivity a, boolean clockMode) {
        this.a = a;
        this.clockMode = clockMode;
        root = new FrameLayout(a);
        root.setBackgroundColor(0xFF000000);
        stage = new FrameLayout(a);
        root.addView(stage, new FrameLayout.LayoutParams(Ui.W, Ui.H));
        View shade = new View(a);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x73000000, 0x00000000, 0x00000000, 0xB3000000}));
        root.addView(shade, new FrameLayout.LayoutParams(Ui.W, Ui.H));

        boolean ar = a.ar();
        caption = a.text("", 700, clockMode ? 40 : 58, 0xFFFFFFFF);
        when = a.text("", 400, clockMode ? 28 : 32, 0xD9FFFFFF);
        counter = a.text("", 500, 26, 0x99FFFFFF);
        pausedTag = a.text(a.t("Paused", "متوقف"), 600, 26, 0xFF0B0F17);
        pausedTag.setPadding(26, 10, 26, 12);
        pausedTag.setBackground(Ui.round(Ui.IVORY, 30));
        pausedTag.setAlpha(0f);
        for (TextView t : new TextView[] {caption, when}) t.setGravity(ar ? Gravity.END : Gravity.START);
        caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
        a.put(root, caption, 84, clockMode ? 930 : 896, clockMode ? 1100 : 1500, -2);
        a.put(root, when, 84, clockMode ? 984 : 972, 1300, -2);
        if (clockMode) {
            // The time, large but in a corner, so the photos stay the picture.
            clock = Ui.text(a, a.time(), Ui.font(a, "Montserrat.ttf", 200), 150, 0xFFFFFFFF);
            clockSub = a.text("", 400, 30, 0xD9FFFFFF);
            clock.setGravity(ar ? Gravity.START : Gravity.END);
            clockSub.setGravity(ar ? Gravity.START : Gravity.END);
            a.put(root, clock, 1036, 820, 800, -2);
            a.put(root, clockSub, 1036, 990, 800, -2);
            View l = a.logo(20, 24);
            a.put(root, l, 84, 70, -2, -2);
            l.setAlpha(0.85f);
        } else {
            a.put(root, a.logo(22, 26), 84, 70, -2, -2);
            counter.setGravity(ar ? Gravity.START : Gravity.END);
            a.put(root, counter, 1336, 74, 500, -2);
            a.put(root, pausedTag, 1600, 130, -2, -2);
        }
    }

    FrameLayout view() {
        return root;
    }

    void start() {
        photos = Memories.showOrder(a, a.prefs);
        if (photos.isEmpty()) {
            TextView none = a.text(a.t("Add your photos from the phone page", "أضف صوركم من صفحة الهاتف"), 500, 40, 0xFFFFFFFF);
            none.setGravity(Gravity.CENTER);
            root.addView(none, new FrameLayout.LayoutParams(Ui.W, -2));
            MainActivity.at(none, 0, 500);
            return;
        }
        show(1);
    }

    void stop() {
        a.h.removeCallbacks(advance);
    }

    void tickClock() {
        if (clock == null) return;
        clock.setText(a.time());
        clockSub.setText(a.ampm() + "  ·  " + new java.text.SimpleDateFormat("EEEE d MMMM", a.ar() ? new java.util.Locale("ar") : java.util.Locale.UK)
                .format(new java.util.Date()));
    }

    /** Left, right and OK in the Memories app. */
    void key(int dx, boolean ok) {
        if (photos == null || photos.isEmpty()) return;
        if (ok) {
            paused = !paused;
            pausedTag.animate().alpha(paused ? 1f : 0f).setDuration(200).start();
            a.h.removeCallbacks(advance);
            if (!paused) a.h.postDelayed(advance, SLIDE_MS / 2);
            return;
        }
        if (dx != 0) show(dx);
    }

    private void show(int step) {
        a.h.removeCallbacks(advance);
        if (photos.isEmpty()) return;
        index = (index + step + photos.size()) % photos.size();
        Memories.Photo p = photos.get(index);
        Bitmap b = Memories.load(a, p.file, 1920);
        if (b == null) {
            a.h.postDelayed(advance, 300);
            return;
        }
        FrameLayout slide = new FrameLayout(a);
        boolean portrait = b.getHeight() * 16 > b.getWidth() * 10;
        if (portrait) {
            // A soft, darkened copy fills the screen behind a photo that does not fit it.
            ImageView fill = new ImageView(a);
            fill.setScaleType(ImageView.ScaleType.CENTER_CROP);
            fill.setImageBitmap(blur(b));
            fill.setColorFilter(0x66000000, android.graphics.PorterDuff.Mode.SRC_ATOP);
            slide.addView(fill, new FrameLayout.LayoutParams(Ui.W, Ui.H));
        }
        ImageView img = new ImageView(a);
        img.setScaleType(portrait ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
        img.setImageBitmap(b);
        slide.addView(img, new FrameLayout.LayoutParams(Ui.W, Ui.H));
        stage.addView(slide, new FrameLayout.LayoutParams(Ui.W, Ui.H));

        // Ken Burns: each photo drifts a different way, slowly, for the whole time it is on screen.
        float dir = (index % 2 == 0) ? 1 : -1;
        img.setScaleX(portrait ? 1f : 1.04f);
        img.setScaleY(portrait ? 1f : 1.04f);
        img.setTranslationX(portrait ? 0 : -24 * dir);
        img.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        img.animate().scaleX(portrait ? 1.04f : 1.12f).scaleY(portrait ? 1.04f : 1.12f).translationX(portrait ? 0 : 30 * dir)
                .translationY(portrait ? 0 : -14 * dir).setDuration(SLIDE_MS + 1600).setInterpolator(new LinearInterpolator()).start();
        slide.setAlpha(0f);
        slide.animate().alpha(1f).setDuration(1400).withEndAction(() -> {
            // Only the newest slide stays: the TV keeps one or two photos in memory, never the album.
            while (stage.getChildCount() > 1) stage.removeViewAt(0);
        }).start();

        boolean ar = a.ar();
        String by = p.by >= 0 && p.by < MainActivity.IDS.length ? a.name(p.by) : "";
        caption.setText(p.caption.isEmpty() ? (p.yearsAgo > 0 ? a.t("On this day", "في مثل هذا اليوم") : "") : p.caption);
        String w = Memories.when(p, ar);
        when.setText(w + (by.isEmpty() ? "" : (w.isEmpty() ? "" : "  ·  ") + a.t("Added by ", "أضافتها ") + by));
        for (TextView t : new TextView[] {caption, when}) {
            t.setAlpha(0f);
            t.setTranslationY(14);
            t.animate().alpha(1f).translationY(0).setStartDelay(500).setDuration(700).start();
        }
        counter.setText((index + 1) + " / " + photos.size());
        if (!paused) a.h.postDelayed(advance, SLIDE_MS);
    }

    private static Bitmap blur(Bitmap b) {
        int w = 64, h = Math.max(1, 64 * b.getHeight() / b.getWidth());
        Bitmap small = Bitmap.createScaledBitmap(b, w, h, true).copy(Bitmap.Config.ARGB_8888, false);
        int[] px = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < 3; i++) MainActivity.boxBlur(px, w, h, 4);
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }
}
