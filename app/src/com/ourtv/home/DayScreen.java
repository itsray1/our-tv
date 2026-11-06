package com.ourtv.home;

import android.graphics.Bitmap;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The family's day, inside Our TV: page one is prayer on the sun's path with the weather,
 * page two is what is coming up, notes from phones, and the phone page code.
 */
final class DayScreen {
    static final int PAGES = 2;
    private final MainActivity a;

    DayScreen(MainActivity a) {
        this.a = a;
    }

    FrameLayout build(int page) {
        FrameLayout f = new FrameLayout(a);
        View shade = new View(a);
        shade.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x80000000, 0x1A000000, 0x80000000}));
        f.addView(shade, new FrameLayout.LayoutParams(Ui.W, Ui.H));
        a.put(f, a.logo(22, 26), 84, 70, -2, -2);
        TextView date = a.text(new SimpleDateFormat("EEEE d MMMM", a.ar() ? new Locale("ar") : Locale.UK).format(new Date()), 500, 32, Ui.IVORY);
        TextView hijri = a.text(Prayer.hijriText(a.prefs, a.ar()), 400, 28, Ui.DIM);
        for (TextView t : new TextView[] {date, hijri}) t.setGravity(a.ar() ? Gravity.START : Gravity.END);
        a.put(f, date, 1136, 58, 700, -2);
        a.put(f, hijri, 1136, 104, 700, -2);
        if (page == 0) today(f);
        else comingUp(f);
        for (int i = 0; i < PAGES; i++) {
            View dot = new View(a);
            dot.setBackground(Ui.round(i == page ? Ui.IVORY : 0x55FFFFFF, 6));
            a.put(f, dot, 960 - PAGES * 22 + i * 44 + (i == page ? 0 : 8), 1036, i == page ? 36 : 12, 12);
        }
        return f;
    }

    private void today(FrameLayout f) {
        boolean ar = a.ar();
        double[] next = Prayer.next(a.prefs);
        int ni = (int) next[0];
        int mins = (int) Math.round(next[1] * 60);
        String in = ar ? "بعد " + (mins >= 60 ? mins / 60 + " س " : "") + mins % 60 + " د" : "in " + (mins >= 60 ? mins / 60 + " h " : "") + mins % 60 + " min";
        TextView label = a.text(a.t("Next prayer", "الصلاة القادمة"), 500, 30, Ui.DIM);
        TextView name = a.text(ar ? Prayer.AR[ni] : Prayer.EN[ni], 700, 120, Ui.IVORY);
        name.setLetterSpacing(-0.02f);
        TextView when = a.text(in + "  ·  " + Prayer.hmA(next[2], ar), 400, 38, Ui.IVORY);
        for (TextView t : new TextView[] {label, name, when}) t.setGravity(ar ? Gravity.END : Gravity.START);
        a.put(f, label, 84, 190, 900, -2);
        a.put(f, name, 84, 232, 900, -2);
        a.put(f, when, 84, 378, 900, -2);

        JSONObject wx = Weather.cached(a.prefs);
        JSONObject cur = wx == null ? null : wx.optJSONObject("current");
        if (cur != null) {
            TextView temp = Ui.text(a, Math.round(cur.optDouble("temperature_2m")) + "°", Ui.font(a, "Montserrat.ttf", 300), 150, Ui.IVORY);
            TextView desc = a.text(Weather.describe(cur.optInt("weather_code"), ar) + a.t("  ·  " + Prayer.CITY_EN, "  ·  " + Prayer.CITY_AR), 500, 34, Ui.IVORY);
            TextView more = a.text(a.t("Feels ", "الإحساس ") + Math.round(cur.optDouble("apparent_temperature")) + "°   "
                    + a.t("Humidity ", "الرطوبة ") + cur.optInt("relative_humidity_2m") + "%   "
                    + a.t("Wind ", "الرياح ") + Math.round(cur.optDouble("wind_speed_10m")) + a.t(" km/h", " كم/س"), 400, 28, Ui.DIM);
            for (TextView t : new TextView[] {temp, desc, more}) t.setGravity(ar ? Gravity.START : Gravity.END);
            a.put(f, temp, 1036, 196, 800, -2);
            a.put(f, desc, 1036, 362, 800, -2);
            a.put(f, more, 1036, 412, 800, -2);
        } else {
            TextView wait = a.text(a.t("Weather appears when the TV is online", "يظهر الطقس عند اتصال التلفاز بالإنترنت"), 400, 28, Ui.DIM);
            wait.setGravity(ar ? Gravity.START : Gravity.END);
            a.put(f, wait, 1036, 250, 800, -2);
        }

        SunPath sun = new SunPath(a, Ui.sans(a, ar, 500));
        Calendar now = Calendar.getInstance();
        sun.t = Prayer.times(now, a.prefs);
        sun.now = now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60.0;
        sun.next = ni;
        sun.rtl = ar;
        sun.names = ar ? Prayer.AR : Prayer.EN;
        a.put(f, sun, 84, 470, 1752, 420);

        JSONObject daily = wx == null ? null : wx.optJSONObject("daily");
        if (daily != null) {
            JSONArray days = daily.optJSONArray("time"), hi = daily.optJSONArray("temperature_2m_max"), lo = daily.optJSONArray("temperature_2m_min"),
                    code = daily.optJSONArray("weather_code");
            int n = days == null ? 0 : Math.min(5, days.length());
            for (int i = 0; i < n; i++) {
                String day;
                try {
                    Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(days.optString(i));
                    day = i == 0 ? a.t("Today", "اليوم") : new SimpleDateFormat("EEE", ar ? new Locale("ar") : Locale.UK).format(d);
                } catch (Exception e) {
                    day = "";
                }
                LinearLayout cell = new LinearLayout(a);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(ar ? Gravity.END : Gravity.START);
                TextView tt = a.text(day + "   " + Math.round(hi.optDouble(i)) + "°  " + Math.round(lo.optDouble(i)) + "°", 600, 30, Ui.IVORY);
                TextView ds = a.text(Weather.describe(code.optInt(i), ar), 400, 26, Ui.DIM);
                cell.addView(tt);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                lp.topMargin = 8;
                cell.addView(ds, lp);
                a.put(f, cell, 84 + i * 350, 924, 350, -2);
            }
        }
    }

    private void comingUp(FrameLayout f) {
        boolean ar = a.ar();
        String[] en = MainActivity.EN, arn = MainActivity.AR;
        String[] people = new String[Math.min(en.length, arn.length)];
        for (int i = 0; i < people.length; i++) people[i] = ar ? arn[i] : en[i];
        TextView head = a.text(a.t("Coming up", "قريباً"), 700, 56, Ui.IVORY);
        head.setGravity(ar ? Gravity.END : Gravity.START);
        a.put(f, head, 84, 180, 1000, -2);
        List<Store.Countdown> up = Store.upcoming(a.prefs, ar, people);
        int n = Math.min(4, up.size());
        for (int i = 0; i < n; i++) a.put(f, card(up.get(i), ar), 84 + i * 449, 262, 405, 300);
        if (n == 0) {
            TextView none = a.text(a.t("Add birthdays and occasions from your phone", "أضف أعياد الميلاد والمناسبات من هاتفك"), 400, 32, Ui.DIM);
            none.setGravity(ar ? Gravity.END : Gravity.START);
            a.put(f, none, 84, 280, 1200, -2);
        }

        TextView nh = a.text(a.t("Notes", "رسائل"), 600, 36, Ui.IVORY);
        nh.setGravity(ar ? Gravity.END : Gravity.START);
        a.put(f, nh, 84, 620, 1000, -2);
        JSONArray notes = Store.list(a.prefs, "notes");
        int shown = 0;
        for (int i = notes.length() - 1; i >= 0 && shown < 4; i--, shown++) {
            JSONObject o = notes.optJSONObject(i);
            if (o == null) continue;
            TextView t = a.text(o.optString("text"), 500, 32, Ui.IVORY);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setGravity(Gravity.CENTER_VERTICAL | (ar ? Gravity.END : Gravity.START));
            t.setPadding(32, 0, 32, 0);
            t.setBackground(Ui.round(0x17FFFFFF, 22));
            a.put(f, t, 84, 684 + shown * 88, 1040, 74);
        }
        if (shown == 0) {
            TextView none = a.text(a.t("Send a note from your phone and it appears here", "أرسل رسالة من هاتفك لتظهر هنا"), 400, 30, Ui.DIM);
            none.setGravity(ar ? Gravity.END : Gravity.START);
            a.put(f, none, 84, 690, 1040, -2);
        }

        phoneCard(f, 1276, 620);
    }

    /**
     * One countdown: lit in the occasion's colours (the person's own colour for a birthday, with their
     * picture), a large number of days, then the name and the date. On the day itself it glows.
     */
    private View card(Store.Countdown c, boolean ar) {
        boolean bday = Season.BIRTHDAY.equals(c.kind), holiday = !bday && !"occasion".equals(c.kind), today = c.days == 0;
        int[] col = bday ? Season.birthday(c.who) : holiday ? Season.colors(c.kind, 0) : Ui.MOODS[a.mood()];
        FrameLayout card = new FrameLayout(a);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(
                ar ? android.graphics.drawable.GradientDrawable.Orientation.TR_BL : android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[] {Ui.alpha(col[1], today ? 0.85f : 0.55f), Ui.alpha(Season.mix(col[3], col[0], 0.4f), today ? 0.7f : 0.38f), Ui.alpha(col[0], 0.55f)});
        bg.setCornerRadius(32);
        if (today) bg.setStroke(4, Ui.alpha(col[2], 0.95f));
        card.setBackground(bg);
        Ui.clip(card, 32);
        // A soft light in the top corner, like the screen glow.
        View light = new View(a);
        android.graphics.drawable.GradientDrawable lg = new android.graphics.drawable.GradientDrawable();
        lg.setGradientType(android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT);
        lg.setGradientRadius(240);
        lg.setColors(new int[] {Ui.alpha(col[2], 0.45f), 0x00000000});
        light.setBackground(lg);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(480, 480);
        llp.leftMargin = ar ? -240 : 165;
        llp.topMargin = -240;
        card.addView(light, llp);

        String kindName = bday ? a.t("Birthday", "عيد ميلاد") : Season.RAMADAN.equals(c.kind) ? a.t("Ramadan", "رمضان")
                : holiday ? a.t("Eid", "عيد") : a.t("Occasion", "مناسبة");
        TextView kind = Ui.text(a, ar ? kindName : kindName.toUpperCase(Locale.UK), ar ? Ui.sans(a, true, 500) : Ui.font(a, "Montserrat.ttf", 500), 22, 0xD9FFFFFF);
        if (!ar) kind.setLetterSpacing(0.28f);

        LinearLayout num = new LinearLayout(a);
        num.setOrientation(LinearLayout.HORIZONTAL);
        num.setBaselineAligned(true);
        num.setLayoutDirection(ar ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        TextView big = Ui.text(a, today ? a.t("Today", "اليوم") : String.valueOf(c.days),
                today ? Ui.sans(a, ar, 700) : Ui.font(a, "Montserrat.ttf", 300), today ? 84 : 112, 0xFFFFFFFF);
        num.addView(big);
        if (!today) {
            TextView unit = a.text(c.days == 1 ? a.t("day", "يوم") : a.t("days", "يوماً"), 500, 30, 0xCCFFFFFF);
            LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(-2, -2);
            up.setMarginStart(14);
            num.addView(unit, up);
        }
        TextView title = a.text(c.title, 600, 34, 0xFFFFFFFF);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView date = a.text(new java.text.SimpleDateFormat("EEEE d MMMM", ar ? new Locale("ar") : Locale.UK).format(c.date), 400, 24, 0xB3FFFFFF);
        for (TextView t : new TextView[] {kind, title, date}) {
            t.setGravity(ar ? Gravity.END : Gravity.START);
            t.setPadding(34, 0, 34, 0);
        }
        card.addView(kind, lp(-1, 34));
        FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(-1, -2);
        nlp.topMargin = today ? 92 : 74;
        num.setPadding(34, 0, 34, 0);
        num.setGravity(ar ? Gravity.END | Gravity.BOTTOM : Gravity.START | Gravity.BOTTOM);
        card.addView(num, nlp);
        card.addView(title, lp(-1, 206));
        card.addView(date, lp(-1, 252));

        if (bday) {
            ImageView img = new ImageView(a);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageBitmap(a.avatar(c.who));
            Ui.clip(img, -1);
            img.setBackground(Ui.ring(4, 0xFFFFFFFF));
            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(92, 92);
            ip.topMargin = 24;
            ip.leftMargin = ar ? 24 : 405 - 24 - 92;
            card.addView(img, ip);
        }
        return card;
    }

    /** The QR code, address and PIN for the phone page; also used in family settings. */
    void phoneCard(FrameLayout f, int x, int y) {
        boolean ar = a.ar();
        String url = PhoneServer.address();
        TextView ph = a.text(a.t("Phone page", "صفحة الهاتف"), 600, 36, Ui.IVORY);
        ph.setGravity(ar ? Gravity.END : Gravity.START);
        a.put(f, ph, x, y, 560, -2);
        Bitmap qr = url.isEmpty() ? null : Qr.make(url);
        if (qr != null) {
            ImageView img = new ImageView(a);
            img.setImageBitmap(qr);
            img.setScaleType(ImageView.ScaleType.FIT_XY);
            // Sharp modules: no smoothing when the small code is enlarged.
            img.setImageDrawable(new android.graphics.drawable.BitmapDrawable(a.getResources(), qr) {{ setFilterBitmap(false); }});
            Ui.clip(img, 20);
            a.put(f, img, x, y + 64, 260, 260);
        }
        TextView addr = a.text(url.isEmpty() ? a.t("Connect the TV to Wi-Fi", "وصّل التلفاز بالواي فاي") : url.replace("http://", ""), 500, 28, Ui.IVORY);
        TextView pin = a.text(a.t("PIN  ", "الرقم السري  ") + PhoneServer.pin(a.prefs), 600, 40, Ui.IVORY);
        pin.setLetterSpacing(0.08f);
        TextView how = a.text(a.t("Scan with your camera", "امسح بالكاميرا"), 400, 24, Ui.DIM);
        for (TextView t : new TextView[] {addr, pin, how}) t.setGravity(ar ? Gravity.END : Gravity.START);
        int tx = x + (qr != null ? 290 : 0);
        a.put(f, how, tx, y + 90, 330, -2);
        a.put(f, addr, tx, y + 140, 330, -2);
        a.put(f, pin, tx, y + 200, 330, -2);
    }

    private static FrameLayout.LayoutParams lp(int w, int top) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, -2);
        p.topMargin = top;
        return p;
    }
}
