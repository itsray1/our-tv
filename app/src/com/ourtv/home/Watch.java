package com.ourtv.home;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.media.tv.TvContract;
import android.net.Uri;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * What each app shares with the TV: Continue Watching rows (Netflix, YouTube, Shahid publish these
 * when they choose to) and the song playing in Spotify. Everything degrades to nothing when missing.
 */
final class Watch {
    static final class Item {
        String pkg, title, detail, poster, intent;
        long engaged;
        int progress;
    }

    static final class Song {
        String title, artist;
        Bitmap art;
        boolean playing;
    }

    private static final Map<String, Bitmap> POSTERS = new HashMap<>();

    /**
     * The newest unfinished item for an app, but only when this person was the last to open that app
     * before it was watched, so nobody sees another person's series.
     */
    static Item resume(Context c, SharedPreferences prefs, String pkg, int person) {
        Item best = null;
        try (Cursor q = c.getContentResolver().query(TvContract.WatchNextPrograms.CONTENT_URI, null, null, null, null)) {
            if (q == null) return null;
            int cPkg = q.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME);
            while (q.moveToNext()) {
                if (!pkg.equals(q.getString(cPkg))) continue;
                Item it = new Item();
                it.pkg = pkg;
                it.title = str(q, TvContract.WatchNextPrograms.COLUMN_TITLE);
                String ep = str(q, TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE);
                String s = str(q, TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER);
                String e = str(q, TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER);
                it.detail = (s != null && e != null ? "S" + s + " E" + e + (ep != null ? " · " + ep : "") : ep);
                it.poster = str(q, TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI);
                it.intent = str(q, TvContract.WatchNextPrograms.COLUMN_INTENT_URI);
                it.engaged = num(q, TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS);
                long dur = num(q, TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS);
                long pos = num(q, TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS);
                it.progress = dur > 0 ? (int) Math.min(100, pos * 100 / dur) : -1;
                if (it.title == null || it.intent == null) continue;
                if (best == null || it.engaged > best.engaged) best = it;
            }
        } catch (Exception ignored) {
            // No permission yet, or no TV provider (the simulator): nothing to continue.
            return null;
        }
        if (best == null) return null;
        int owner = -1;
        long ownerAt = 0;
        String[] ids = MainActivity.IDS;
        for (int i = 0; i < ids.length; i++) {
            long at = prefs.getLong("recent_" + ids[i] + "_" + pkg, 0);
            // Allow a minute of clock slack between opening the app and the app recording the play.
            if (at > ownerAt && (best.engaged == 0 || at <= best.engaged + 60_000)) {
                owner = i;
                ownerAt = at;
            }
        }
        return owner == person ? best : null;
    }

    static Intent intent(Item it) {
        try {
            return Intent.parseUri(it.intent, Intent.URI_INTENT_SCHEME);
        } catch (Exception e) {
            return null;
        }
    }

    /** Loads a poster off the main thread; calls back on the given handler only when it arrives. */
    static void poster(Context c, String uri, android.os.Handler h, java.util.function.Consumer<Bitmap> done) {
        if (uri == null) return;
        Bitmap cached = POSTERS.get(uri);
        if (cached != null) {
            done.accept(cached);
            return;
        }
        new Thread(() -> {
            Bitmap b = null;
            try {
                InputStream in;
                if (uri.startsWith("http")) {
                    HttpURLConnection con = (HttpURLConnection) new URL(uri).openConnection();
                    con.setConnectTimeout(6000);
                    con.setReadTimeout(6000);
                    in = con.getInputStream();
                } else in = c.getContentResolver().openInputStream(Uri.parse(uri));
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 2;
                try (InputStream s = in) {
                    b = BitmapFactory.decodeStream(s, null, o);
                }
            } catch (Exception ignored) {
                // A missing poster leaves the app's own banner behind the title.
            }
            if (b != null) {
                Bitmap fin = b;
                h.post(() -> {
                    POSTERS.put(uri, fin);
                    done.accept(fin);
                });
            }
        }).start();
    }

    /** The song in Spotify, readable once the TV allows Our TV notification access. */
    static Song song(Context c, String pkg) {
        try {
            MediaSessionManager m = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            for (MediaController mc : m.getActiveSessions(new ComponentName(c, Listener.class))) {
                if (!pkg.equals(mc.getPackageName())) continue;
                MediaMetadata md = mc.getMetadata();
                if (md == null) continue;
                Song s = new Song();
                s.title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
                s.artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
                s.art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (s.art == null) s.art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
                PlaybackState ps = mc.getPlaybackState();
                s.playing = ps != null && ps.getState() == PlaybackState.STATE_PLAYING;
                if (s.title != null) return s;
            }
        } catch (Exception ignored) {
            // Access not granted: Spotify shows as a normal app.
        }
        return null;
    }

    private static String str(Cursor q, String col) {
        int i = q.getColumnIndex(col);
        if (i < 0 || q.isNull(i)) return null;
        String s = q.getString(i);
        return s == null || s.isEmpty() ? null : s;
    }

    private static long num(Cursor q, String col) {
        int i = q.getColumnIndex(col);
        return i < 0 || q.isNull(i) ? 0 : q.getLong(i);
    }
}
