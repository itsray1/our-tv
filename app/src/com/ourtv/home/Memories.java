package com.ourtv.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The family's photos, kept only on the TV (files/memories) and added from the phone page. Each has an
 * optional caption, the day it was taken and who added it.
 */
final class Memories {
    static final int MAX = 300;

    static final class Photo {
        String file, caption;
        int by;
        Date date;
        int yearsAgo;
    }

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "memories");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static List<Photo> all(Context c, SharedPreferences prefs) {
        List<Photo> out = new ArrayList<>();
        JSONArray a = Store.list(prefs, "memories");
        Calendar now = Calendar.getInstance();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null || !new File(dir(c), o.optString("file")).exists()) continue;
            Photo p = new Photo();
            p.file = o.optString("file");
            p.caption = o.optString("caption");
            p.by = o.optInt("by", -1);
            try {
                p.date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(o.optString("date"));
            } catch (Exception e) {
                p.date = null;
            }
            if (p.date != null) {
                Calendar d = Calendar.getInstance();
                d.setTime(p.date);
                p.yearsAgo = now.get(Calendar.MONTH) == d.get(Calendar.MONTH) && now.get(Calendar.DAY_OF_MONTH) == d.get(Calendar.DAY_OF_MONTH)
                        ? now.get(Calendar.YEAR) - d.get(Calendar.YEAR) : 0;
            }
            out.add(p);
        }
        return out;
    }

    /** "On this day" photos from earlier years first, then everything else shuffled, newest-heavy. */
    static List<Photo> showOrder(Context c, SharedPreferences prefs) {
        List<Photo> all = all(c, prefs), first = new ArrayList<>(), rest = new ArrayList<>();
        for (Photo p : all) (p.yearsAgo > 0 ? first : rest).add(p);
        Collections.shuffle(rest, new Random(System.currentTimeMillis() / 3_600_000L));
        first.addAll(rest);
        return first;
    }

    static int onThisDay(Context c, SharedPreferences prefs) {
        int n = 0;
        for (Photo p : all(c, prefs)) if (p.yearsAgo > 0) n++;
        return n;
    }

    /** Loads a photo no bigger than the screen, in the lighter 16-bit format, to keep the TV's memory free. */
    static Bitmap load(Context c, String file, int maxW) {
        File f = new File(dir(c), file);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        int s = 1;
        while (o.outWidth / (s * 2) >= maxW) s *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = s;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    static String when(Photo p, boolean ar) {
        if (p.date == null) return "";
        String d = new SimpleDateFormat("d MMMM yyyy", ar ? new Locale("ar") : Locale.UK).format(p.date);
        if (p.yearsAgo > 0) {
            String ago = ar ? (p.yearsAgo == 1 ? "قبل سنة" : "قبل " + p.yearsAgo + " سنوات") : (p.yearsAgo == 1 ? "1 year ago" : p.yearsAgo + " years ago");
            return (ar ? "في مثل هذا اليوم · " : "On this day · ") + ago;
        }
        return d;
    }
}
