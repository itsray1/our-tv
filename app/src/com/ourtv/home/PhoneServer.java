package com.ourtv.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The family phone page, served by the TV on the home network only. Every change needs the PIN
 * shown on the TV; photos arrive already resized by the phone.
 */
final class PhoneServer {
    static final int PORT = 8787;
    private static PhoneServer instance;
    private final Context ctx;
    private final SharedPreferences prefs;
    private final Runnable changed;
    private ServerSocket socket;
    private int failures;

    static synchronized void start(Context c, Runnable changed) {
        if (instance == null) {
            instance = new PhoneServer(c.getApplicationContext(), changed);
            new Thread(instance::run, "phone-page").start();
        }
    }

    private PhoneServer(Context c, Runnable changed) {
        ctx = c;
        prefs = c.getSharedPreferences(Family.store(c), Context.MODE_PRIVATE);
        this.changed = changed;
    }

    static String pin(SharedPreferences prefs) {
        String p = prefs.getString("pin", null);
        if (p == null) {
            p = String.format(Locale.US, "%04d", new SecureRandom().nextInt(10000));
            prefs.edit().putString("pin", p).apply();
        }
        return p;
    }

    static String address() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return "http://" + a.getHostAddress() + ":" + PORT;
                }
            }
        } catch (Exception ignored) {
            // No network yet.
        }
        return "";
    }

    private void run() {
        try {
            socket = new ServerSocket(PORT);
            while (true) {
                Socket s = socket.accept();
                new Thread(() -> serve(s)).start();
            }
        } catch (Exception ignored) {
            // Port busy or network gone; the TV keeps working without the phone page.
        }
    }

    private void serve(Socket s) {
        try (Socket client = s) {
            client.setSoTimeout(15000);
            InputStream in = client.getInputStream();
            String head = readHead(in);
            String[] first = head.split("\r\n")[0].split(" ");
            int length = 0;
            for (String line : head.split("\r\n")) {
                if (line.toLowerCase(Locale.US).startsWith("content-length:")) length = Integer.parseInt(line.substring(15).trim());
            }
            if (length > 3_000_000) {
                reply(client.getOutputStream(), 413, "text/plain", "Too large");
                return;
            }
            byte[] body = new byte[length];
            for (int off = 0; off < length; ) {
                int n = in.read(body, off, length - off);
                if (n < 0) break;
                off += n;
            }
            OutputStream out = client.getOutputStream();
            if ("GET".equals(first[0]) && (first[1].equals("/") || first[1].startsWith("/?"))) {
                reply(out, 200, "text/html; charset=utf-8", asset("phone.html"));
            } else if ("GET".equals(first[0]) && first[1].startsWith("/memory/")) {
                // A memory's picture for the phone page's list. Private: it needs the PIN like everything else.
                String path = first[1].substring(8), file = path.split("\\?")[0];
                String pin = path.contains("pin=") ? path.substring(path.indexOf("pin=") + 4).split("&")[0] : "";
                File f = new File(Memories.dir(ctx), file);
                synchronized (this) {
                    if (failures >= 20 || !pin(prefs).equals(pin)) {
                        failures++;
                        reply(out, 403, "text/plain", "PIN");
                        return;
                    }
                }
                if (!file.matches("m\\d+\\.jpg") || !f.exists()) {
                    reply(out, 404, "text/plain", "Not found");
                    return;
                }
                byte[] b = new byte[(int) f.length()];
                try (java.io.FileInputStream fin = new java.io.FileInputStream(f)) {
                    for (int off = 0; off < b.length; ) {
                        int n = fin.read(b, off, b.length - off);
                        if (n < 0) break;
                        off += n;
                    }
                }
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: " + b.length
                        + "\r\nCache-Control: private, max-age=86400\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(b);
                out.flush();
            } else if ("GET".equals(first[0]) && first[1].startsWith("/sync")) {
                // A flash-and-beep page for checking mirrored sound against the picture.
                reply(out, 200, "text/html; charset=utf-8", asset("sync.html"));
            } else if ("POST".equals(first[0]) && first[1].startsWith("/api")) {
                JSONObject req = new JSONObject(new String(body, StandardCharsets.UTF_8));
                synchronized (this) {
                    if (failures >= 20) {
                        reply(out, 429, "application/json", "{\"error\":\"locked\"}");
                        return;
                    }
                    if (!pin(prefs).equals(req.optString("pin"))) {
                        failures++;
                        reply(out, 403, "application/json", "{\"error\":\"pin\"}");
                        return;
                    }
                }
                String result = handle(req);
                reply(out, 200, "application/json", result);
                if (!"state".equals(req.optString("action"))) changed.run();
            } else {
                reply(out, 404, "text/plain", "Not found");
            }
        } catch (Exception ignored) {
            // A broken request never affects the TV.
        }
    }

    private String handle(JSONObject r) throws Exception {
        String action = r.optString("action");
        SharedPreferences.Editor e = prefs.edit();
        // A phone page opened before someone was removed may name a person who is no longer there.
        if (("picture".equals(action) || "photo".equals(action)) && (r.optInt("who", -1) < 0 || r.optInt("who", -1) >= MainActivity.IDS.length)) {
            return state().toString();
        }
        switch (action) {
            case "addEvent": {
                JSONArray a = Store.list(prefs, "events");
                JSONObject ev = new JSONObject();
                ev.put("title", r.optString("title").trim());
                ev.put("date", r.optString("date"));
                ev.put("kind", r.optString("kind", "occasion"));
                ev.put("who", r.optInt("who", -1));
                a.put(ev);
                Store.save(prefs, "events", a);
                break;
            }
            case "delEvent": {
                JSONArray a = Store.list(prefs, "events");
                a.remove(r.optInt("index", -1));
                Store.save(prefs, "events", a);
                break;
            }
            case "addNote": {
                JSONArray a = Store.list(prefs, "notes");
                JSONObject n = new JSONObject();
                n.put("text", r.optString("text").trim());
                n.put("from", r.optString("from"));
                n.put("at", System.currentTimeMillis());
                a.put(n);
                while (a.length() > 6) a.remove(0);
                Store.save(prefs, "notes", a);
                break;
            }
            case "delNote": {
                JSONArray a = Store.list(prefs, "notes");
                a.remove(r.optInt("index", -1));
                Store.save(prefs, "notes", a);
                break;
            }
            case "picture":
                e.putString("pic_" + MainActivity.id(r.getInt("who")), r.optString("mode", "avatar")).apply();
                break;
            case "photo": {
                byte[] jpg = Base64.decode(r.getString("data"), Base64.DEFAULT);
                File f = new File(ctx.getFilesDir(), "photo-" + MainActivity.id(r.getInt("who")) + ".jpg");
                try (FileOutputStream o = new FileOutputStream(f)) {
                    o.write(jpg);
                }
                e.putString("pic_" + MainActivity.id(r.getInt("who")), "photo").putLong("picAt", System.currentTimeMillis()).apply();
                break;
            }
            case "prayer": {
                JSONObject adj = r.optJSONObject("adj");
                if (adj != null) for (String k : Prayer.KEYS) e.putInt("adj_" + k, Math.max(-30, Math.min(30, adj.optInt(k, 0))));
                e.putInt("hijriAdj", Math.max(-2, Math.min(2, r.optInt("hijri", 0))));
                e.putBoolean("islamicDates", r.optBoolean("islamicDates", true)).apply();
                break;
            }
            case "celebrate": {
                String kind = r.optString("kind");
                if (kind.matches("birthday|ramadan|fitr|adha")) e.putString("celebrateNow", kind + "|" + r.optInt("who", 0)).apply();
                break;
            }
            case "mood":
                e.putInt("mood", Math.max(0, Math.min(4, r.optInt("mood", 0)))).apply();
                break;
            case "wallpaper": {
                // A background photo, already resized to 1920×1080 by the phone; it becomes the Photo mood.
                byte[] jpg = Base64.decode(r.getString("data"), Base64.DEFAULT);
                File tmp = new File(ctx.getFilesDir(), "wallpaper.tmp");
                try (FileOutputStream o = new FileOutputStream(tmp)) {
                    o.write(jpg);
                }
                tmp.renameTo(new File(ctx.getFilesDir(), "wallpaper.jpg"));
                e.putInt("mood", 4).apply();
                break;
            }
            case "addPerson":
                Family.add(ctx, r.optString("en"), r.optString("ar"));
                break;
            case "renamePerson":
                Family.rename(ctx, r.optInt("who", -1), r.optString("en"), r.optString("ar"));
                break;
            case "removePerson":
                Family.remove(ctx, r.optInt("who", -1));
                break;
            case "addMemory": {
                // One photo per request, already resized on the phone; caption, date and who added it.
                JSONArray a = Store.list(prefs, "memories");
                if (a.length() >= Memories.MAX) break;
                String file = "m" + System.currentTimeMillis() + ".jpg";
                try (FileOutputStream o = new FileOutputStream(new File(Memories.dir(ctx), file))) {
                    o.write(Base64.decode(r.getString("data"), Base64.DEFAULT));
                }
                JSONObject m = new JSONObject();
                m.put("file", file);
                m.put("caption", r.optString("caption").trim());
                m.put("date", r.optString("date"));
                m.put("by", r.optInt("by", -1));
                a.put(m);
                Store.save(prefs, "memories", a);
                break;
            }
            case "delMemory": {
                JSONArray a = Store.list(prefs, "memories"), keep = new JSONArray();
                String file = r.optString("file");
                for (int i = 0; i < a.length(); i++) {
                    JSONObject m = a.optJSONObject(i);
                    if (m != null && file.equals(m.optString("file"))) new File(Memories.dir(ctx), file).delete();
                    else if (m != null) keep.put(m);
                }
                Store.save(prefs, "memories", keep);
                break;
            }
            case "editMemory": {
                JSONArray a = Store.list(prefs, "memories");
                for (int i = 0; i < a.length(); i++) {
                    JSONObject m = a.optJSONObject(i);
                    if (m != null && r.optString("file").equals(m.optString("file"))) {
                        m.put("caption", r.optString("caption").trim());
                        if (!r.optString("date").isEmpty()) m.put("date", r.optString("date"));
                    }
                }
                Store.save(prefs, "memories", a);
                break;
            }
            case "wallpaperRemove":
                new File(ctx.getFilesDir(), "wallpaper.jpg").delete();
                if (prefs.getInt("mood", 0) == 4) e.putInt("mood", 0).apply();
                break;
            default:
                break;
        }
        return state().toString();
    }

    private JSONObject state() throws Exception {
        JSONObject s = new JSONObject();
        JSONArray people = new JSONArray();
        // One consistent copy of the list, even if someone is being added or removed right now.
        String[] ids = MainActivity.IDS, en = MainActivity.EN, ar = MainActivity.AR;
        for (int i = 0; i < Math.min(ids.length, Math.min(en.length, ar.length)); i++) {
            JSONObject p = new JSONObject();
            p.put("en", en[i]);
            p.put("ar", ar[i]);
            p.put("pic", prefs.getString("pic_" + ids[i], "avatar"));
            p.put("hasPhoto", new File(ctx.getFilesDir(), "photo-" + ids[i] + ".jpg").exists());
            people.put(p);
        }
        s.put("people", people);
        s.put("maxPeople", Family.MAX);
        s.put("events", Store.list(prefs, "events"));
        s.put("notes", Store.list(prefs, "notes"));
        JSONObject adj = new JSONObject();
        for (String k : Prayer.KEYS) adj.put(k, prefs.getInt("adj_" + k, 0));
        s.put("adj", adj);
        s.put("hijri", prefs.getInt("hijriAdj", 0));
        s.put("islamicDates", prefs.getBoolean("islamicDates", true));
        s.put("mood", prefs.getInt("mood", 0));
        s.put("hasWall", new File(ctx.getFilesDir(), "wallpaper.jpg").exists());
        s.put("memories", Store.list(prefs, "memories"));
        return s;
    }

    private String asset(String name) throws Exception {
        try (InputStream in = ctx.getAssets().open(name)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
            return b.toString("UTF-8");
        }
    }

    private static String readHead(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c, matched = 0;
        while ((c = in.read()) >= 0 && b.size() < 16384) {
            b.write(c);
            matched = (c == '\r' || c == '\n') ? matched + 1 : 0;
            if (matched == 4) break;
        }
        return b.toString("UTF-8");
    }

    private static void reply(OutputStream out, int code, String type, String body) throws Exception {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + code + " OK\r\nContent-Type: " + type + "\r\nContent-Length: " + b.length
                + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.UTF_8));
        out.write(b);
        out.flush();
    }
}
