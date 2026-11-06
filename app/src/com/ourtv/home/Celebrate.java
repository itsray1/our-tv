package com.ourtv.home;

import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * The full-screen moment for a birthday, Eid or the start of Ramadan: big type in both languages,
 * soft drifting lights in the occasion's colours, and the family's faces. No symbols, only light.
 */
final class Celebrate {
    private final MainActivity a;
    Bokeh bokeh;

    Celebrate(MainActivity a) {
        this.a = a;
    }

    FrameLayout build(String kind, int who) {
        int[] col = Season.colors(kind, who);
        FrameLayout f = new FrameLayout(a);
        View veil = new View(a);
        veil.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x66000000, 0x1A000000, 0x8C000000}));
        f.addView(veil, new FrameLayout.LayoutParams(Ui.W, Ui.H));
        bokeh = new Bokeh(a, col);
        f.addView(bokeh, new FrameLayout.LayoutParams(Ui.W, Ui.H));
        if (Season.BIRTHDAY.equals(kind)) birthday(f, who, col);
        else holiday(f, kind, col);
        TextView hint = a.text(a.t("Press OK to continue", "اضغط موافق للمتابعة"), 400, 26, 0x99F5F2EC);
        center(f, hint, 1010);
        return f;
    }

    private void birthday(FrameLayout f, int who, int[] col) {
        boolean ar = a.ar();
        TextView label = spaced(new SimpleDateFormat("d MMMM", ar ? new Locale("ar") : Locale.UK).format(new Date()), ar);
        center(f, label, 96);

        // The person's picture with a halo in their own colour.
        View halo = new View(a);
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientRadius(260);
        g.setColors(new int[] {Ui.alpha(col[2], 0.55f), Ui.alpha(col[1], 0.18f), 0x00000000});
        halo.setBackground(g);
        place(f, halo, (Ui.W - 520) / 2, 90, 520, 520);
        View ring = new View(a);
        ring.setBackground(Ui.ring(8, col[2]));
        place(f, ring, (Ui.W - 316) / 2, 192, 316, 316);
        ImageView img = new ImageView(a);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setImageBitmap(a.avatar(who));
        Ui.clip(img, -1);
        place(f, img, (Ui.W - 292) / 2, 204, 292, 292);
        pop(img, 200);
        pop(ring, 200);

        TextView wish = Ui.text(a, a.t("Happy birthday", "عيد ميلاد سعيد"), Ui.sans(a, ar, 300), 64, Ui.IVORY);
        center(f, wish, 566);
        TextView name = Ui.text(a, a.name(who), Ui.sans(a, ar, 700), 176, 0xFFFFFFFF);
        name.setLetterSpacing(-0.02f);
        center(f, name, 646);
        rise(wish, 500);
        rise(name, 650);

        int age = Store.age(a.prefs, who);
        String line = age > 0 ? a.t(age + " today  ·  from all of us", age + " عاماً اليوم  ·  من كل العائلة")
                : a.t("From all of us", "من كل العائلة");
        TextView from = a.text(line, 400, 34, Ui.DIM);
        center(f, from, 878);
        rise(from, 900);
    }

    private void holiday(FrameLayout f, String kind, int[] col) {
        boolean ar = a.ar(), ramadan = Season.RAMADAN.equals(kind);
        // A preview on another day shows the occasion's own first day.
        boolean real = kind.equals(Season.today(a.prefs));
        Date when = real ? new Date() : Prayer.nextHijri(a.prefs, ramadan ? 8 : Season.FITR.equals(kind) ? 9 : 11, Season.ADHA.equals(kind) ? 10 : 1);
        String hijri = Prayer.hijriText(a.prefs, ar, when == null ? new Date() : when);
        TextView label = spaced(ramadan ? a.t("Day ", "اليوم ") + (real ? Season.ramadanDay(a.prefs) : 1) + "  ·  " + hijri : hijri, ar);
        center(f, label, 96);

        TextView big = Ui.text(a, ramadan ? "رمضان كريم" : "عيد مبارك", Ui.font(a, "Alexandria.ttf", 600), 200, 0xFFFFFFFF);
        center(f, big, 190);
        TextView en = Ui.text(a, ramadan ? "RAMADAN KAREEM" : "EID MUBARAK", Ui.font(a, "Montserrat.ttf", 300), 58, Ui.IVORY);
        en.setLetterSpacing(0.32f);
        center(f, en, 486);
        rise(big, 300);
        rise(en, 520);

        // A thin glow under the words, in the occasion's lamp colour.
        View glowLine = new View(a);
        glowLine.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {0x00000000, Ui.alpha(col[2], 0.9f), 0x00000000}));
        place(f, glowLine, (Ui.W - 640) / 2, 584, 640, 3);
        rise(glowLine, 640);

        if (ramadan) {
            double[] t = Prayer.times(Calendar.getInstance(), a.prefs);
            Calendar now = Calendar.getInstance();
            double h = now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60.0;
            String[][] cards = {
                {a.t("Suhoor ends", "ينتهي السحور"), Prayer.hmA(t[0], ar), h < t[0] ? until(t[0] - h) : ""},
                {a.t("Iftar", "الإفطار"), Prayer.hmA(t[4], ar), h < t[4] ? until(t[4] - h) : a.t("Enjoy your iftar", "إفطاراً شهياً")},
            };
            for (int i = 0; i < 2; i++) {
                int slot = ar ? 1 - i : i;
                FrameLayout card = new FrameLayout(a);
                card.setBackground(Ui.round(0x1FFFFFFF, 32));
                TextView k = a.text(cards[i][0], 500, 30, Ui.DIM);
                TextView v = Ui.text(a, cards[i][1], Ui.font(a, "Montserrat.ttf", 300), 84, 0xFFFFFFFF);
                TextView s = a.text(cards[i][2], 500, 28, Season.mix(col[2], 0xFFFFFFFF, 0.3f));
                for (TextView tv : new TextView[] {k, v, s}) tv.setGravity(Gravity.CENTER);
                card.addView(k, lp(30));
                card.addView(v, lp(72));
                card.addView(s, lp(176));
                place(f, card, 960 - 460 + slot * 480, 680, 440, 236);
                rise(card, 760 + 120 * slot);
            }
        } else {
            TextView which = a.text(Season.FITR.equals(kind) ? a.t("Eid al-Fitr", "عيد الفطر") : a.t("Eid al-Adha", "عيد الأضحى"), 500, 34, Ui.DIM);
            center(f, which, 620);
            rise(which, 700);
            // The whole family, together.
            int count = MainActivity.IDS.length, gap = Math.min(230, 1700 / count);
            for (int i = 0; i < count; i++) {
                int slot = ar ? count - 1 - i : i;
                int cx = 960 + Math.round((slot - (count - 1) / 2f) * gap);
                View ring = new View(a);
                ring.setBackground(Ui.ring(4, Ui.alpha(col[1 + i % 3], 0.9f)));
                place(f, ring, cx - 74, 712, 148, 148);
                ImageView img = new ImageView(a);
                img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                img.setImageBitmap(a.avatar(i));
                Ui.clip(img, -1);
                place(f, img, cx - 66, 720, 132, 132);
                TextView n = a.text(a.name(i), 500, 28, Ui.IVORY);
                n.setGravity(Gravity.CENTER);
                place(f, n, cx - (gap - 10) / 2, 878, gap - 10, -2);
                pop(img, 820 + 90 * slot);
                pop(ring, 820 + 90 * slot);
                rise(n, 860 + 90 * slot);
            }
        }
    }

    private String until(double hours) {
        int m = (int) Math.round(hours * 60);
        return a.ar() ? "بعد " + (m >= 60 ? m / 60 + " س " : "") + m % 60 + " د" : "in " + (m >= 60 ? m / 60 + " h " : "") + m % 60 + " min";
    }

    private TextView spaced(String s, boolean ar) {
        TextView t = Ui.text(a, ar ? s : s.toUpperCase(Locale.UK), ar ? Ui.sans(a, true, 400) : Ui.font(a, "Montserrat.ttf", 400), 28, Ui.DIM);
        if (!ar) t.setLetterSpacing(0.3f);
        return t;
    }

    private void center(FrameLayout f, TextView t, int y) {
        t.setGravity(Gravity.CENTER);
        place(f, t, 0, y, Ui.W, -2);
    }

    private static void place(FrameLayout f, View v, int x, int y, int w, int h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.leftMargin = x;
        lp.topMargin = y;
        f.addView(v, lp);
    }

    private static FrameLayout.LayoutParams lp(int top) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-1, -2);
        p.topMargin = top;
        return p;
    }

    private static void rise(View v, long delay) {
        v.setAlpha(0f);
        v.setTranslationY(28);
        v.animate().alpha(1f).translationY(0).setStartDelay(delay).setDuration(900)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
    }

    private static void pop(View v, long delay) {
        v.setAlpha(0f);
        v.setScaleX(0.86f);
        v.setScaleY(0.86f);
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(delay).setDuration(1000)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f)).start();
    }
}
