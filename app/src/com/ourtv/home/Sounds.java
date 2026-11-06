package com.ourtv.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * The family's chosen sound style, played from WAV files (tools/make_sound_styles.py). Silent while
 * any film or song is playing.
 */
final class Sounds {
    static final String[] NAMES = {"sig", "focus", "pick", "open", "bday", "night", "back", "toggle", "menu", "page", "note", "edge"};
    /** Folders under assets/sfx; Glass is the default. */
    static final String[] STYLES = {"glass", "piano", "marimba", "minimal"};
    static final String[] STYLE_EN = {"Glass", "Piano", "Marimba", "Minimal"};
    static final String[] STYLE_AR = {"زجاج", "بيانو", "ماريمبا", "هادئ جداً"};
    private static Sounds instance;
    private final Context ctx;
    private String style;
    private final SoundPool pool;
    private final Map<String, Integer> ids = new HashMap<>();
    private final SharedPreferences prefs;
    private final AudioManager audio;
    private final java.util.Set<Integer> loaded = new java.util.HashSet<>();
    private int waiting = -1;
    private float waitingVolume;

    static synchronized Sounds get(Context c) {
        if (instance == null) instance = new Sounds(c.getApplicationContext());
        return instance;
    }

    private Sounds(Context c) {
        prefs = c.getSharedPreferences(Family.store(c), Context.MODE_PRIVATE);
        audio = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        pool = new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        pool.setOnLoadCompleteListener((p, id, status) -> {
            if (status != 0) return;
            loaded.add(id);
            if (id == waiting) {
                pool.play(id, waitingVolume, waitingVolume, 1, 0, 1f);
                waiting = -1;
            }
        });
        ctx = c;
        load(prefs.getString("sndStyle", STYLES[0]));
    }

    static int styleIndex(SharedPreferences prefs) {
        int i = java.util.Arrays.asList(STYLES).indexOf(prefs.getString("sndStyle", STYLES[0]));
        return Math.max(0, i);
    }

    /** Switches to another style: the old sounds are freed and the new ones loaded. */
    void setStyle(String s) {
        prefs.edit().putString("sndStyle", s).apply();
        if (!s.equals(style)) load(s);
    }

    private void load(String s) {
        for (int id : ids.values()) pool.unload(id);
        ids.clear();
        loaded.clear();
        style = s;
        for (String n : NAMES) {
            try {
                ids.put(n, pool.load(ctx.getAssets().openFd("sfx/" + s + "/" + n + ".wav"), 1));
            } catch (Exception ignored) {
                // A missing sound must never stop the TV from working.
            }
        }
    }

    boolean quietNow() {
        if (!prefs.getBoolean("quiet", true)) return false;
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int from = prefs.getInt("qs", 22), to = prefs.getInt("qe", 7);
        return from < to ? h >= from && h < to : h >= from || h < to;
    }

    void play(String name) {
        play(name, false);
    }

    /** force: a preview from the sound settings, which ignores quiet hours and the on/off switch. */
    void play(String name, boolean force) {
        Integer id = ids.get(name);
        if (id == null) return;
        if (!force && (!prefs.getBoolean("snd", true) || audio.isMusicActive())) return;
        float v = prefs.getInt("vol", 7) / 10f;
        // Quiet hours make the sounds soft, not silent: silence felt like the TV was broken.
        if (!force && quietNow()) v *= 0.3f;
        if (!loaded.contains(id)) {
            waiting = id;
            waitingVolume = v;
            return;
        }
        pool.play(id, v, v, 1, 0, 1f);
    }
}
