package com.ourtv.home;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.icu.util.IslamicCalendar;
import java.util.Date;

/**
 * Special days from the Hijri calendar: all of Ramadan, the three days of Eid al-Fitr and the four
 * days of Eid al-Adha. Each has its own colours, used instead of the chosen mood while it lasts.
 */
final class Season {
    static final String RAMADAN = "ramadan", FITR = "fitr", ADHA = "adha", BIRTHDAY = "birthday";

    /** Night indigo with a warm lamp glow and deep teal: evenings of Ramadan. */
    static final int[] RAMADAN_COLORS = {0xFF060A1C, 0xFF2C3A8C, 0xFFE89B45, 0xFF0F6470};
    /** Mint, peach and rose: bright and festive without gold. */
    static final int[] EID_COLORS = {0xFF0B1220, 0xFF26C6B0, 0xFFFFB98A, 0xFFFF6B8E};
    /** Birthday palettes by colour, so they survive people being added or removed. */
    private static final java.util.Map<Integer, int[]> BIRTHDAY_COLORS = new java.util.HashMap<>();

    static String today(SharedPreferences prefs) {
        if (!prefs.getBoolean("specialDays", true)) return null;
        IslamicCalendar c = Prayer.hijri(prefs, new Date());
        int m = c.get(IslamicCalendar.MONTH), d = c.get(IslamicCalendar.DAY_OF_MONTH);
        if (m == 8) return RAMADAN;
        if (m == 9 && d <= 3) return FITR;
        if (m == 11 && d >= 10 && d <= 13) return ADHA;
        return null;
    }

    static int ramadanDay(SharedPreferences prefs) {
        return Prayer.hijri(prefs, new Date()).get(IslamicCalendar.DAY_OF_MONTH);
    }

    static int[] colors(String kind, int who) {
        if (BIRTHDAY.equals(kind)) return birthday(who);
        return RAMADAN.equals(kind) ? RAMADAN_COLORS : EID_COLORS;
    }

    /** A birthday is lit in the person's own colour. */
    static int[] birthday(int who) {
        int[] letters = MainActivity.LETTER;
        int c = letters[Math.max(0, Math.min(letters.length - 1, who))];
        int[] p = BIRTHDAY_COLORS.get(c);
        if (p == null) {
            p = new int[] {0xFF0C0A14, c, mix(c, 0xFFFFFFFF, 0.35f), mix(c, 0xFFFF6B8E, 0.5f)};
            BIRTHDAY_COLORS.put(c, p);
        }
        return p;
    }

    static int mix(int a, int b, float t) {
        return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }
}
