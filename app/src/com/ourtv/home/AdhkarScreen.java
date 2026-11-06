package com.ourtv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * Adhkar, one at a time in large Arabic type. OK counts (a ring fills as you go and the next dhikr comes
 * by itself when the count is done), left and right move between adhkar, up and down change the set.
 * It opens on morning or evening adhkar by the time of day.
 */
final class AdhkarScreen {
    private final MainActivity a;
    private final FrameLayout root;
    private final TextView text, hint, progress, done;
    private final Ring ring;
    private final List<TextView> tabs = new ArrayList<>();
    private int section, item, count;

    /** The counter: a thin circle that fills, with how many are left in the middle. */
    static final class Ring extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), num = new Paint(Paint.ANTI_ALIAS_FLAG);
        int left, total = 1;
        int color = Ui.IVORY;

        Ring(Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            num.setTextAlign(Paint.Align.CENTER);
            num.setTypeface(Ui.font(c, "Montserrat.ttf", 300));
            num.setColor(0xFFFFFFFF);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), s = 8, r = w / 2 - s;
            RectF box = new RectF(s, s, w - s, w - s);
            p.setStrokeWidth(s);
            p.setColor(0x33FFFFFF);
            c.drawCircle(w / 2, w / 2, r, p);
            p.setColor(color);
            c.drawArc(box, -90, 360f * (total - left) / total, false, p);
            num.setTextSize(left >= 100 ? w * 0.3f : w * 0.38f);
            c.drawText(left == 0 ? "✓" : String.valueOf(left), w / 2, w / 2 - (num.descent() + num.ascent()) / 2, num);
        }
    }

    AdhkarScreen(MainActivity a) {
        this.a = a;
        root = new FrameLayout(a);
        // Dark enough that the words stay calm and clear even over a busy family photo.
        View shade = new View(a);
        shade.setBackgroundColor(0xD9050A12);
        root.addView(shade, new FrameLayout.LayoutParams(Ui.W, Ui.H));
        a.put(root, a.logo(22, 26), 84, 70, -2, -2);

        LinearLayout row = new LinearLayout(a);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        for (int i = 0; i < Adhkar.SECTIONS_AR.length; i++) {
            TextView t = Ui.text(a, Adhkar.SECTIONS_AR[i], Ui.font(a, "Alexandria.ttf", 500), 28, Ui.IVORY);
            t.setPadding(28, 12, 28, 14);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginStart(10);
            row.addView(t, lp);
            tabs.add(t);
        }
        root.addView(row, new FrameLayout.LayoutParams(-2, -2));
        MainActivity.at(row, Ui.W - 84 - Ui.measureWidth(row), 56);

        text = Ui.text(a, "", Ui.font(a, "Alexandria.ttf", 500), 56, 0xFFFFFFFF);
        text.setSingleLine(false);
        text.setGravity(Gravity.CENTER);
        text.setLineSpacing(18, 1f);
        text.setTextDirection(View.TEXT_DIRECTION_RTL);
        root.addView(text, new FrameLayout.LayoutParams(1560, 600));
        MainActivity.at(text, 180, 170);

        ring = new Ring(a);
        root.addView(ring, new FrameLayout.LayoutParams(170, 170));
        MainActivity.at(ring, (Ui.W - 170) / 2f, 800);
        hint = Ui.text(a, "اضغط موافق للعد  ·  يمين ويسار للتنقل", Ui.font(a, "Alexandria.ttf", 400), 26, 0x99FFFFFF);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, new FrameLayout.LayoutParams(Ui.W, -2));
        MainActivity.at(hint, 0, 1000);
        progress = Ui.text(a, "", Ui.font(a, "Alexandria.ttf", 400), 26, 0x99FFFFFF);
        progress.setGravity(Gravity.START);
        root.addView(progress, new FrameLayout.LayoutParams(400, -2));
        MainActivity.at(progress, 84, 1000);
        done = Ui.text(a, "تقبل الله منا ومنكم", Ui.font(a, "Alexandria.ttf", 600), 64, 0xFFFFFFFF);
        done.setGravity(Gravity.CENTER);
        done.setAlpha(0f);
        root.addView(done, new FrameLayout.LayoutParams(Ui.W, -2));
        MainActivity.at(done, 0, 420);

        section = Adhkar.sectionNow(a.prefs);
        ring.color = Season.mix(a.palette()[2], 0xFFFFFFFF, 0.35f);
        load(0);
    }

    FrameLayout view() {
        return root;
    }

    private Object[] current() {
        return Adhkar.ALL[section][item];
    }

    private void load(int to) {
        Object[][] list = Adhkar.ALL[section];
        item = Math.max(0, Math.min(list.length - 1, to));
        count = 0;
        String s = (String) current()[0];
        // Long passages get smaller type so a whole dhikr always fits on the screen.
        text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, s.length() > 260 ? 40 : s.length() > 140 ? 48 : s.length() > 60 ? 56 : 72);
        text.setText(s);
        text.setAlpha(0f);
        text.animate().alpha(1f).setDuration(400).start();
        done.setAlpha(0f);
        ring.total = (Integer) current()[1];
        ring.left = ring.total;
        ring.invalidate();
        progress.setText((item + 1) + " / " + list.length);
        for (int i = 0; i < tabs.size(); i++) {
            boolean on = i == section;
            tabs.get(i).setBackground(Ui.round(on ? Ui.IVORY : 0x1FFFFFFF, 30));
            tabs.get(i).setTextColor(on ? 0xFF0B0F17 : Ui.IVORY);
        }
    }

    /** OK counts; right/left move; up/down change the set of adhkar. */
    void key(int dx, int dy, boolean ok) {
        if (dy != 0) {
            section = (section + dy + Adhkar.ALL.length) % Adhkar.ALL.length;
            a.snd.play("page");
            load(0);
            return;
        }
        if (dx != 0) {
            int to = item + dx;
            if (to < 0 || to >= Adhkar.ALL[section].length) {
                a.snd.play("edge");
                return;
            }
            a.snd.play("focus");
            load(to);
            return;
        }
        if (!ok || ring.left == 0) return;
        ring.left--;
        ring.invalidate();
        ring.animate().scaleX(1.06f).scaleY(1.06f).setDuration(80).withEndAction(() -> ring.animate().scaleX(1f).scaleY(1f).setDuration(120).start()).start();
        if (ring.left > 0) {
            a.snd.play("focus");
            return;
        }
        a.snd.play("toggle");
        if (item < Adhkar.ALL[section].length - 1) {
            a.h.postDelayed(() -> { if (ring.left == 0) load(item + 1); }, 900);
        } else {
            // The whole set is done.
            a.snd.play("pick");
            text.animate().alpha(0f).setDuration(400).start();
            done.animate().alpha(1f).setDuration(700).start();
        }
    }
}
