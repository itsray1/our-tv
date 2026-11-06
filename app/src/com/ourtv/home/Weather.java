package com.ourtv.home;

import android.content.SharedPreferences;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

/** The family city's weather from Open-Meteo (free, no key), kept for 30 minutes and shown from cache when offline. */
final class Weather {
    private static String url() {
        return "https://api.open-meteo.com/v1/forecast?latitude=" + Prayer.LAT + "&longitude=" + Prayer.LNG + "&timezone=auto"
                + "&forecast_days=5&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min";
    }

    static JSONObject cached(SharedPreferences prefs) {
        try {
            return new JSONObject(prefs.getString("wx", ""));
        } catch (Exception e) {
            return null;
        }
    }

    static void refresh(SharedPreferences prefs, Runnable done) {
        if (System.currentTimeMillis() - prefs.getLong("wxAt", 0) < 30 * 60_000L && cached(prefs) != null) return;
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url()).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                try (InputStream in = c.getInputStream()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                    String json = out.toString("UTF-8");
                    new JSONObject(json);
                    prefs.edit().putString("wx", json).putLong("wxAt", System.currentTimeMillis()).apply();
                }
                done.run();
            } catch (Exception ignored) {
                // Offline: the last forecast stays on screen.
            }
        }).start();
    }

    static String describe(int code, boolean ar) {
        String[][] w = {
            {"0", "Clear", "صافٍ"}, {"1", "Mostly clear", "صافٍ غالباً"}, {"2", "Partly cloudy", "غائم جزئياً"}, {"3", "Cloudy", "غائم"},
            {"45", "Fog", "ضباب"}, {"48", "Fog", "ضباب"}, {"51", "Drizzle", "رذاذ"}, {"53", "Drizzle", "رذاذ"}, {"55", "Drizzle", "رذاذ"},
            {"61", "Rain", "مطر"}, {"63", "Rain", "مطر"}, {"65", "Heavy rain", "مطر غزير"}, {"80", "Showers", "زخات"},
            {"81", "Showers", "زخات"}, {"82", "Heavy showers", "زخات غزيرة"}, {"95", "Thunderstorm", "عاصفة رعدية"},
        };
        for (String[] r : w) if (Integer.parseInt(r[0]) == code) return ar ? r[2] : r[1];
        return "";
    }
}
