package com.ourtv.home;

import android.content.SharedPreferences;
import android.icu.util.IslamicCalendar;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Prayer times for the family's city calculated on the TV, with no internet. Angles follow the
 * Egyptian method (Fajr 19.5°, Isha 17.5°, standard Asr); each prayer can be nudged by minutes.
 */
final class Prayer {
    /** The city from family.json (Family.load); these defaults are only a placeholder. */
    static double LAT = 21.4225, LNG = 39.8262;
    static String CITY_EN = "Makkah", CITY_AR = "مكة";
    static final double FAJR = 19.5, ISHA = 17.5;
    static final String[] KEYS = {"fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha"};
    static final String[] EN = {"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};
    static final String[] AR = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};

    private static double rad(double d) {
        return d * Math.PI / 180;
    }

    private static double deg(double r) {
        return r * 180 / Math.PI;
    }

    private static double fix(double a, double b) {
        return ((a % b) + b) % b;
    }

    /** Hours after local midnight for the six times on the given day, adjusted by the family's minutes. */
    static double[] times(Calendar day, SharedPreferences prefs) {
        double tz = TimeZone.getDefault().getOffset(day.getTimeInMillis()) / 3_600_000.0;
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH), 12, 0);
        double n = utc.getTimeInMillis() / 864e5 + 2440587.5 - 2451545;
        double g = fix(357.529 + 0.98560028 * n, 360), q = fix(280.459 + 0.98564736 * n, 360);
        double l = fix(q + 1.915 * Math.sin(rad(g)) + 0.02 * Math.sin(rad(2 * g)), 360), e = 23.439 - 0.00000036 * n;
        double ra = fix(deg(Math.atan2(Math.cos(rad(e)) * Math.sin(rad(l)), Math.cos(rad(l)))) / 15, 24);
        double dec = deg(Math.asin(Math.sin(rad(e)) * Math.sin(rad(l))));
        double noon = fix(12 + tz - LNG / 15 - (q / 15 - ra), 24);
        double asrAlt = deg(Math.atan(1 / (1 + Math.tan(rad(Math.abs(LAT - dec))))));
        double[] t = {noon - arc(-FAJR, dec), noon - arc(-0.833, dec), noon + 1 / 60.0, noon + arc(asrAlt, dec), noon + arc(-0.833, dec), noon + arc(-ISHA, dec)};
        for (int i = 0; i < t.length; i++) t[i] += prefs.getInt("adj_" + KEYS[i], 0) / 60.0;
        return t;
    }

    private static double arc(double alt, double dec) {
        return deg(Math.acos((Math.sin(rad(alt)) - Math.sin(rad(dec)) * Math.sin(rad(LAT))) / (Math.cos(rad(dec)) * Math.cos(rad(LAT))))) / 15;
    }

    /** 12-hour time, as the family reads clocks: 5:57, 12:52, 6:15. The prayer's name says morning or evening. */
    static String hm(double h) {
        int m = (int) Math.round(h * 60);
        int hour = (m / 60) % 12;
        return String.format(Locale.US, "%d:%02d", hour == 0 ? 12 : hour, m % 60);
    }

    /** 12-hour time with AM or PM (ص or م), so 5 in the evening never reads as 5 in the morning. */
    static String hmA(double h, boolean arabic) {
        int m = (int) Math.round(h * 60), hour24 = (m / 60) % 24;
        return hm(h) + (arabic ? (hour24 < 12 ? " ص" : " م") : (hour24 < 12 ? " AM" : " PM"));
    }

    /** An hour of the day in 12-hour form, for quiet hours: "10 PM" or "10 م". */
    static String hour12(int h, boolean arabic) {
        int x = h % 12;
        return (x == 0 ? 12 : x) + (arabic ? (h < 12 ? " ص" : " م") : (h < 12 ? " AM" : " PM"));
    }

    /** Index of the next of the five prayers (sunrise is skipped), and the hours until it. */
    static double[] next(SharedPreferences prefs) {
        Calendar now = Calendar.getInstance();
        double h = now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60.0 + now.get(Calendar.SECOND) / 3600.0;
        double[] t = times(now, prefs);
        for (int i = 0; i < 6; i++) if (i != 1 && t[i] > h) return new double[] {i, t[i] - h, t[i]};
        Calendar tomorrow = (Calendar) now.clone();
        tomorrow.add(Calendar.DAY_OF_MONTH, 1);
        double fajr = times(tomorrow, prefs)[0];
        return new double[] {0, fajr + 24 - h, fajr};
    }

    static IslamicCalendar hijri(SharedPreferences prefs, Date date) {
        IslamicCalendar c = new IslamicCalendar();
        c.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
        c.setTime(date);
        c.add(IslamicCalendar.DAY_OF_MONTH, prefs.getInt("hijriAdj", 0));
        return c;
    }

    static String hijriText(SharedPreferences prefs, boolean arabic) {
        return hijriText(prefs, arabic, new Date());
    }

    static String hijriText(SharedPreferences prefs, boolean arabic, Date date) {
        IslamicCalendar c = hijri(prefs, date);
        android.icu.text.SimpleDateFormat f = new android.icu.text.SimpleDateFormat("d MMMM y",
                arabic ? new android.icu.util.ULocale("ar@calendar=islamic-umalqura;numbers=latn") : new android.icu.util.ULocale("en@calendar=islamic-umalqura"));
        f.setCalendar(c);
        return f.format(c.getTime());
    }

    /** Next date (local) that falls on the given Hijri month (0-based) and day. */
    static Date nextHijri(SharedPreferences prefs, int month, int day) {
        IslamicCalendar now = hijri(prefs, new Date());
        for (int add = 0; add < 2; add++) {
            IslamicCalendar c = (IslamicCalendar) now.clone();
            c.set(IslamicCalendar.YEAR, now.get(IslamicCalendar.YEAR) + add);
            c.set(IslamicCalendar.MONTH, month);
            c.set(IslamicCalendar.DAY_OF_MONTH, day);
            c.add(IslamicCalendar.DAY_OF_MONTH, -prefs.getInt("hijriAdj", 0));
            if (c.getTimeInMillis() > System.currentTimeMillis() - 86_400_000L) return c.getTime();
        }
        return null;
    }
}
