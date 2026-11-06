package com.ourtv.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserManager;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Who lives here and where. The people (one to eight) are kept on the TV and can be added, renamed and
 * removed from the phone page; the first list and the home city come from assets/family.json, which stays
 * on the family's own computer and is never committed (assets/family.example.json is the template).
 */
final class Family {
    static final int MAX = 8;
    /** Each new person's own colour, for their letter picture and their birthday. */
    static final int[] COLORS = {0xFFE07A5F, 0xFF3D8BFD, 0xFF2EC4B6, 0xFFF4A261, 0xFF9B7BEA, 0xFFE76F9A, 0xFF5BBF6E, 0xFFD9A441};
    private static boolean located;
    /** The name on screen, in English and Arabic; family.json can give the family's own. */
    static String BRAND_EN = "OUR TV", BRAND_AR = "تلفازنا";
    private static String store;

    /**
     * The name of the TV's private settings file. family.json may keep an older name so an update never
     * loses a family's settings; it is read before anything else, straight from the file.
     */
    static synchronized String store(Context c) {
        if (store == null) {
            JSONObject f = read(c, "family.json");
            store = f == null ? "ourtv" : f.optString("store", "ourtv");
        }
        return store;
    }

    /** The name the AirPlay receiver announces, shown in the mirroring hints. */
    static String tvName() {
        return BRAND_EN.toUpperCase(java.util.Locale.ROOT).endsWith("TV") ? BRAND_EN : BRAND_EN + " TV";
    }

    static synchronized void load(Context c) {
        JSONObject f = read(c, "family.json");
        if (f == null) f = read(c, "family.example.json");
        if (!located && f != null) {
            BRAND_EN = f.optString("brand_en", BRAND_EN);
            BRAND_AR = f.optString("brand_ar", BRAND_AR);
            JSONObject home = f.optJSONObject("home");
            if (home != null) {
                Prayer.LAT = home.optDouble("lat", Prayer.LAT);
                Prayer.LNG = home.optDouble("lng", Prayer.LNG);
                Prayer.CITY_EN = home.optString("city_en", Prayer.CITY_EN);
                Prayer.CITY_AR = home.optString("city_ar", Prayer.CITY_AR);
            }
            located = true;
        }
        // People are in the TV's private storage, readable only once the TV has fully started.
        UserManager users = (UserManager) c.getSystemService(Context.USER_SERVICE);
        if (users != null && !users.isUserUnlocked()) return;
        SharedPreferences prefs = prefs(c);
        JSONArray people = Store.list(prefs, "people");
        if (people.length() == 0 && f != null && f.optJSONArray("people") != null) {
            // First start: the list from family.json, each with a colour.
            JSONArray seed = f.optJSONArray("people");
            for (int i = 0; i < seed.length() && i < MAX; i++) {
                JSONObject p = seed.optJSONObject(i);
                if (p == null) continue;
                try {
                    p.put("color", COLORS[i % COLORS.length]);
                } catch (Exception ignored) {
                    // A plain JSON put cannot fail here.
                }
                people.put(p);
            }
            Store.save(prefs, "people", people);
        }
        apply(people);
    }

    /** Publishes the list to the rest of the app as new arrays, so readers never see a half-changed list. */
    private static void apply(JSONArray people) {
        int n = Math.max(1, people.length());
        String[] ids = new String[n], en = new String[n], ar = new String[n];
        int[] col = new int[n];
        for (int i = 0; i < n; i++) {
            JSONObject p = people.optJSONObject(i);
            ids[i] = p == null ? "p" + i : p.optString("id", "p" + i);
            en[i] = p == null ? "Person" : p.optString("en", "Person");
            ar[i] = p == null ? "شخص" : p.optString("ar", en[i]);
            col[i] = p == null ? COLORS[i % COLORS.length] : p.optInt("color", COLORS[i % COLORS.length]);
        }
        MainActivity.IDS = ids;
        MainActivity.EN = en;
        MainActivity.AR = ar;
        MainActivity.LETTER = col;
    }

    static synchronized boolean add(Context c, String en, String ar) {
        SharedPreferences prefs = prefs(c);
        JSONArray people = Store.list(prefs, "people");
        if (people.length() >= MAX || en.trim().isEmpty()) return false;
        try {
            JSONObject p = new JSONObject();
            p.put("id", "u" + Long.toString(System.currentTimeMillis(), 36));
            p.put("en", en.trim());
            p.put("ar", ar.trim().isEmpty() ? en.trim() : ar.trim());
            // The first colour no one else has, so two people never look alike.
            int color = COLORS[people.length() % COLORS.length];
            for (int k : COLORS) {
                boolean used = false;
                for (int i = 0; i < people.length(); i++) if (people.optJSONObject(i).optInt("color") == k) used = true;
                if (!used) {
                    color = k;
                    break;
                }
            }
            p.put("color", color);
            people.put(p);
        } catch (Exception e) {
            return false;
        }
        Store.save(prefs, "people", people);
        apply(people);
        return true;
    }

    static synchronized void rename(Context c, int who, String en, String ar) {
        SharedPreferences prefs = prefs(c);
        JSONArray people = Store.list(prefs, "people");
        JSONObject p = people.optJSONObject(who);
        if (p == null || en.trim().isEmpty()) return;
        try {
            p.put("en", en.trim());
            p.put("ar", ar.trim().isEmpty() ? en.trim() : ar.trim());
        } catch (Exception ignored) {
            return;
        }
        Store.save(prefs, "people", people);
        apply(people);
    }

    /**
     * Removes one person and everything that was only theirs: language, picture, photo, app row and
     * recently opened apps. Birthdays saved for them go too; dates and memories of others are kept.
     */
    static synchronized boolean remove(Context c, int who) {
        SharedPreferences prefs = prefs(c);
        JSONArray people = Store.list(prefs, "people");
        if (people.length() <= 1 || who < 0 || who >= people.length()) return false;
        String id = people.optJSONObject(who).optString("id");
        SharedPreferences.Editor e = prefs.edit();
        for (Map.Entry<String, ?> kv : prefs.getAll().entrySet()) {
            String k = kv.getKey();
            if (k.equals("lang_" + id) || k.equals("pic_" + id) || k.equals("dock_" + id) || k.startsWith("recent_" + id + "_")
                    || k.equals("cel_birthday" + id)) e.remove(k);
        }
        new File(c.getFilesDir(), "photo-" + id + ".jpg").delete();
        // Dates and memories point at people by position: shift everyone after the removed person.
        JSONArray events = Store.list(prefs, "events"), keep = new JSONArray();
        for (int i = 0; i < events.length(); i++) {
            JSONObject ev = events.optJSONObject(i);
            if (ev == null) continue;
            int w = ev.optInt("who", -1);
            if (w == who && "birthday".equals(ev.optString("kind"))) continue;
            try {
                if (w == who) ev.put("who", -1);
                else if (w > who) ev.put("who", w - 1);
            } catch (Exception ignored) {
                // A plain JSON put cannot fail here.
            }
            keep.put(ev);
        }
        e.putString("events", keep.toString());
        JSONArray mem = Store.list(prefs, "memories");
        for (int i = 0; i < mem.length(); i++) {
            JSONObject m = mem.optJSONObject(i);
            int b = m == null ? -1 : m.optInt("by", -1);
            try {
                if (b == who) m.put("by", -1);
                else if (b > who) m.put("by", b - 1);
            } catch (Exception ignored) {
                // A plain JSON put cannot fail here.
            }
        }
        e.putString("memories", mem.toString());
        int last = prefs.getInt("last", 0);
        e.putInt("last", last == who ? 0 : last > who ? last - 1 : last);
        people.remove(who);
        e.putString("people", people.toString());
        e.apply();
        apply(people);
        return true;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(store(c), Context.MODE_PRIVATE);
    }

    private static JSONObject read(Context c, String name) {
        try (InputStream in = c.getAssets().open(name)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
            return new JSONObject(b.toString("UTF-8"));
        } catch (Exception e) {
            return null;
        }
    }
}
