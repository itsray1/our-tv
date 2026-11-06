package com.ourtv.home;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Family dates and notes, kept on the TV only. Dates come from the phone page; Eid and Ramadan are calculated. */
final class Store {
    static final class Countdown {
        final String title, kind;
        final int days, who;
        final Date date;

        Countdown(String title, int days, String kind, int who, Date date) {
            this.title = title;
            this.days = days;
            this.kind = kind;
            this.who = who;
            this.date = date;
        }
    }

    static JSONArray list(SharedPreferences prefs, String key) {
        try {
            return new JSONArray(prefs.getString(key, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static void save(SharedPreferences prefs, String key, JSONArray a) {
        prefs.edit().putString(key, a.toString()).apply();
    }

    private static int daysUntil(Date d) {
        Calendar a = Calendar.getInstance(), b = Calendar.getInstance();
        b.setTime(d);
        for (Calendar c : new Calendar[] {a, b}) {
            c.set(Calendar.HOUR_OF_DAY, 12);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
        }
        return Math.round((b.getTimeInMillis() - a.getTimeInMillis()) / 86_400_000f);
    }

    /** "MM-DD" repeats every year; "YYYY-MM-DD" happens once, except birthdays, which always repeat. */
    private static Date nextDate(String s) {
        return nextDate(s, false);
    }

    private static Date nextDate(String s, boolean everyYear) {
        try {
            String[] p = s.split("-");
            Calendar c = Calendar.getInstance();
            if (everyYear && p.length == 3) p = new String[] {p[1], p[2]};
            boolean yearly = p.length == 2;
            int month = Integer.parseInt(p[yearly ? 0 : 1]) - 1, day = Integer.parseInt(p[yearly ? 1 : 2]);
            if (!yearly) c.set(Calendar.YEAR, Integer.parseInt(p[0]));
            c.set(Calendar.MONTH, month);
            c.set(Calendar.DAY_OF_MONTH, day);
            if (yearly && daysUntil(c.getTime()) < 0) c.add(Calendar.YEAR, 1);
            return c.getTime();
        } catch (Exception e) {
            return null;
        }
    }

    static List<Countdown> upcoming(SharedPreferences prefs, boolean ar, String[] names) {
        List<Countdown> out = new ArrayList<>();
        JSONArray ev = list(prefs, "events");
        for (int i = 0; i < ev.length(); i++) {
            JSONObject e = ev.optJSONObject(i);
            if (e == null) continue;
            boolean bday = "birthday".equals(e.optString("kind"));
            Date d = nextDate(e.optString("date"), bday);
            if (d == null) continue;
            int days = daysUntil(d), who = e.optInt("who", -1);
            if (days < 0) continue;
            String title = e.optString("title");
            if (bday && who >= 0 && who < names.length) {
                title = ar ? "عيد ميلاد " + names[who] : names[who] + "'s birthday";
            } else who = -1;
            out.add(new Countdown(title, days, bday && who >= 0 ? Season.BIRTHDAY : "occasion", who, d));
        }
        if (prefs.getBoolean("islamicDates", true)) {
            Object[][] fixed = {{8, 1, "Ramadan", "رمضان", Season.RAMADAN}, {9, 1, "Eid al-Fitr", "عيد الفطر", Season.FITR},
                {11, 10, "Eid al-Adha", "عيد الأضحى", Season.ADHA}};
            for (Object[] f : fixed) {
                Date d = Prayer.nextHijri(prefs, (Integer) f[0], (Integer) f[1]);
                if (d != null && daysUntil(d) >= 0) out.add(new Countdown((String) (ar ? f[3] : f[2]), daysUntil(d), (String) f[4], -1, d));
            }
        }
        Collections.sort(out, (a, b) -> a.days - b.days);
        return out;
    }

    /** True on the day a birthday saved for this person falls. */
    static boolean birthdayToday(SharedPreferences prefs, int who) {
        JSONArray ev = list(prefs, "events");
        for (int i = 0; i < ev.length(); i++) {
            JSONObject e = ev.optJSONObject(i);
            if (e == null || !"birthday".equals(e.optString("kind")) || e.optInt("who", -1) != who) continue;
            Date d = nextDate(e.optString("date"), true);
            if (d != null && daysUntil(d) == 0) return true;
        }
        return false;
    }

    /** The age turned today, when the phone page saved a birth year; otherwise 0. */
    static int age(SharedPreferences prefs, int who) {
        JSONArray ev = list(prefs, "events");
        for (int i = 0; i < ev.length(); i++) {
            JSONObject e = ev.optJSONObject(i);
            if (e == null || !"birthday".equals(e.optString("kind")) || e.optInt("who", -1) != who) continue;
            String[] p = e.optString("date").split("-");
            if (p.length == 3) {
                try {
                    return Calendar.getInstance().get(Calendar.YEAR) - Integer.parseInt(p[0]);
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

    static String when(int days, boolean ar) {
        if (days == 0) return ar ? "اليوم" : "Today";
        if (days == 1) return ar ? "غداً" : "Tomorrow";
        return ar ? "بعد " + days + " يوم" : "In " + days + " days";
    }
}
