package com.ourtv.home;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Our TV: opening, the family, each person's Home, settings and Good night, all drawn natively. */
public class MainActivity extends Activity {
    /** The people, one to eight; replaced as a whole by Family whenever someone is added, renamed or removed. */
    static String[] IDS = {"p1"};
    static String[] EN = {"Person"};
    static String[] AR = {"شخص"};
    static final String[] DEFAULT_DOCK = {"com.netflix.ninja", "com.spotify.tv.android", "com.google.android.youtube.tv", "net.mbc.shahidTV",
        "io.github.jqssun.airplay"};
    static final String[] MOOD_EN = {"Aurora", "Sunset", "Graphite", "Auto", "Photo"};
    static final String[] MOOD_AR = {"الشفق", "الغروب", "جرافيت", "تلقائي", "صورة"};
    /** Apps with their own profile picker, where the hand-off shows a reminder. */
    static final List<String> ASKS = Arrays.asList("com.netflix.ninja", "net.mbc.shahidTV", "com.google.android.youtube.tv", "com.disneyplus.mea", "com.osn.go");
    static final String ADD = "+", DAY = "day", MEM = "memories", ADH = "adhkar";
    static final String SPOTIFY = "com.spotify.tv.android";
    /** Open-source AirPlay receiver (F-Droid), so iPad and iPhone screens can be mirrored. */
    static final String AIRPLAY = "io.github.jqssun.airplay";
    /** Each person's own colour for the letter picture. */
    static int[] LETTER = {0xFFE07A5F};
    static final String[] PICS = {"avatar", "letter", "photo"};

    static final int INTRO = 0, WHO = 1, HOME = 2, SETTINGS = 3, CLOCK = 4, DAYS = 5, MEMS = 6, ADHK = 7;
    static final int NONE = 0, EDIT = 1, MOVE = 2, PICK = 3, NIGHT = 4, NOTE = 5, INPUTS = 6, PHONE = 7, CELEBRATE = 8, PERSON = 9, CONFIRM = 10;
    static final long IDLE_MS = 180_000, LONG_MS = 800;

    SharedPreferences prefs;
    Sounds snd;
    final Handler h = new Handler(Looper.getMainLooper());
    private FrameLayout root, layer, over;
    private GlowView glow;
    private int screen = INTRO, overlay = NONE;
    private int[] shownPalette;
    private int who, active, row, app, top, set;
    private boolean moodFocus, longFired;
    private long pausedAt;
    private String menu = "family";

    private final List<View> discs = new ArrayList<>();
    private final List<TextView> names = new ArrayList<>(), pills = new ArrayList<>();
    private final List<View> tiles = new ArrayList<>(), topItems = new ArrayList<>();
    private final List<String> dock = new ArrayList<>();
    private final Map<String, Bitmap> banners = new HashMap<>();
    private final Map<String, Integer> colors = new HashMap<>();
    private TextView heroLabel, heroTitle, heroSub, clockText;
    private HeroGlow heroGlow;
    private View dockBand;
    private int dockShift, tileW;
    private final List<TextView[]> rows = new ArrayList<>();
    private final List<String> pickList = new ArrayList<>();
    private final List<TextView> overRows = new ArrayList<>();
    private int osel;
    private final ImageView[] backs = new ImageView[2];
    private int backOn;
    private ImageView meImg, wall;
    private String peopleShown = "", activeId = "";
    private long wallStamp;
    private boolean resumed;
    private final Map<Object, Bitmap> soft = new HashMap<>();
    private final Map<String, Bitmap> avatars = new HashMap<>();
    private View moodRow, moodHint;
    private final List<TextView> tileLabels = new ArrayList<>();
    private DayScreen day;
    private int dayPage;
    private Watch.Item resume;

    private final Runnable idle = () -> { if (overlay == NONE && (screen == WHO || screen == HOME)) showClock(); };
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (clockText != null) clockText.setText(clockLabel());
            if (shownPalette != palette()) applyMood();
            if (screen == DAYS && overlay == NONE) drawDay(false);
            h.postDelayed(this, 30_000);
        }
    };
    private final Runnable longPress = () -> { longFired = true; onLong(); };

    // ---------- lifecycle ----------

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Family.load(this);
        peopleShown = String.join(",", IDS);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences(Family.store(this), MODE_PRIVATE);
        snd = Sounds.get(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        glow = new GlowView(this);
        root.addView(glow, new FrameLayout.LayoutParams(Ui.W + 160, Ui.H + 160));
        glow.setTranslationX(-80);
        glow.setTranslationY(-80);
        wall = new ImageView(this);
        wall.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wall.setAlpha(0f);
        wall.setVisibility(View.INVISIBLE);
        // A little darker than the photo itself, so white text stays easy to read on any picture.
        wall.setColorFilter(0x40000000, android.graphics.PorterDuff.Mode.SRC_ATOP);
        root.addView(wall, new FrameLayout.LayoutParams(-1, -1));
        for (int i = 0; i < 2; i++) {
            backs[i] = new ImageView(this);
            backs[i].setScaleType(ImageView.ScaleType.CENTER_CROP);
            backs[i].setAlpha(0f);
            backs[i].setVisibility(View.INVISIBLE);
            root.addView(backs[i], new FrameLayout.LayoutParams(-1, -1));
        }
        View veil = new View(this);
        veil.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x66000000, 0x33000000, 0xA6000000}));
        root.addView(veil, new FrameLayout.LayoutParams(-1, -1));
        layer = new FrameLayout(this);
        root.addView(layer, new FrameLayout.LayoutParams(Ui.W, Ui.H));
        over = new FrameLayout(this);
        root.addView(over, new FrameLayout.LayoutParams(Ui.W, Ui.H));
        setContentView(root);
        float scale = getResources().getDisplayMetrics().widthPixels / (float) Ui.W;
        for (View v : new View[] {layer, over}) {
            v.setPivotX(0);
            v.setPivotY(0);
            v.setScaleX(scale);
            v.setScaleY(scale);
        }
        who = prefs.getInt("last", 0);
        day = new DayScreen(this);
        // Once: put the mirroring tile at the end of every row that was already arranged.
        if (!prefs.getBoolean("mirrorAdded", false)) {
            SharedPreferences.Editor ed = prefs.edit().putBoolean("mirrorAdded", true);
            for (String id : IDS) {
                String d = prefs.getString("dock_" + id, null);
                if (d != null && !d.contains(AIRPLAY)) ed.putString("dock_" + id, d.isEmpty() ? AIRPLAY : d + "," + AIRPLAY);
            }
            ed.apply();
        }
        PhoneServer.start(this, () -> h.post(this::phoneChanged));
        applyMood();
        showIntro();
    }

    /** Something was changed from a phone: pictures, dates, notes, prayer or mood. */
    private void phoneChanged() {
        avatars.clear();
        soft.clear();
        // Someone added, renamed or removed from a phone: keep the right person, or go back to the family.
        String people = String.join(",", IDS);
        if (!people.equals(peopleShown)) {
            peopleShown = people;
            boolean gone = !java.util.Arrays.asList(IDS).contains(activeId);
            who = Math.max(0, Math.min(IDS.length - 1, who));
            if (!gone) active = java.util.Arrays.asList(IDS).indexOf(activeId);
            if (overlay == NONE && (screen == WHO || (gone && screen != INTRO && screen != CLOCK))) {
                active = Math.min(active, IDS.length - 1);
                showWho();
            }
        }
        applyMood();
        org.json.JSONArray notes = Store.list(prefs, "notes");
        org.json.JSONObject last = notes.length() == 0 ? null : notes.optJSONObject(notes.length() - 1);
        long at = last == null ? 0 : last.optLong("at");
        boolean fresh = at > prefs.getLong("noteSeen", 0);
        if (fresh) prefs.edit().putLong("noteSeen", at).apply();
        // "Show on TV" from the phone page: a preview of a celebration.
        String preview = prefs.getString("celebrateNow", null);
        if (preview != null && screen != INTRO && screen != CLOCK) {
            prefs.edit().remove("celebrateNow").apply();
            String[] p = preview.split("\\|");
            showCelebrate(p[0], Math.max(0, Math.min(IDS.length - 1, Integer.parseInt(p[1]))), true);
            return;
        }
        if (overlay != NONE) return;
        if (screen == DAYS) drawDay(false);
        else if (screen == WHO && !moodFocus) showWho();
        else if (screen == HOME) refreshMe();
        if (fresh) snd.play("note");
        if (fresh && (screen == HOME || screen == WHO)) toast(t("Note: ", "رسالة: ") + last.optString("text"));
    }

    /**
     * Wakes the AirPlay receiver in the background, so an iPad finds the TV even after a restart or a
     * crash of the receiver. It answers its own start-up broadcast; nothing appears on screen.
     */
    /**
     * Puts the helper back in Accessibility if Android dropped it (a force-stop does that on Android 11),
     * as long as the family still uses the Home button or prayer reminders and has not chosen to return to
     * the original TV. Needs WRITE_SECURE_SETTINGS, granted once over adb; without it, nothing happens.
     * Returns true when the helper is (now) switched on.
     */
    boolean repairHelper() {
        // The full class name: the app id can differ from the code's package (see tools/build.ps1).
        String me = getPackageName() + "/" + Helper.class.getName();
        android.content.ContentResolver cr = getContentResolver();
        String list = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        boolean on = list != null && list.contains(me);
        boolean wanted = !prefs.getBoolean("helperOff", false) && (prefs.getBoolean("homeRedirect", false) || prefs.getBoolean("prayerNotes", true));
        if (checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED) {
            // Spotify's song: if this app was allowed under an older class name, allow the current one too.
            try {
                String nl = Settings.Secure.getString(cr, "enabled_notification_listeners"), lme = getPackageName() + "/" + Listener.class.getName();
                if (nl != null && nl.contains(getPackageName() + "/") && !nl.contains(lme)) Settings.Secure.putString(cr, "enabled_notification_listeners", nl + ":" + lme);
            } catch (Exception ignored) {
                // Spotify then shows as a normal app.
            }
        }
        if (on || !wanted || checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) return on;
        try {
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list == null || list.isEmpty() ? me : list + ":" + me);
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** "Return to the original TV": the helper is switched off and stays off until the family turns a feature back on. */
    private void helperOff() {
        prefs.edit().putBoolean("helperOff", true).apply();
        if (checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) return;
        try {
            android.content.ContentResolver cr = getContentResolver();
            String list = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (list == null) return;
            StringBuilder keep = new StringBuilder();
            for (String s : list.split(":")) {
                if (s.isEmpty() || s.startsWith(getPackageName() + "/")) continue;
                if (keep.length() > 0) keep.append(':');
                keep.append(s);
            }
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, keep.toString());
        } catch (Exception ignored) {
            // Without the permission the family turns it off in Accessibility themselves.
        }
    }

    private static long lastWake;

    static void wakeAirPlay(android.content.Context c) {
        // Android 12 and later forbid the receiver starting itself from the background, and it would crash.
        if (android.os.Build.VERSION.SDK_INT >= 31) return;
        long now = android.os.SystemClock.elapsedRealtime();
        if (lastWake != 0 && now - lastWake < 10 * 60_000L) return;
        lastWake = now;
        try {
            c.sendBroadcast(new Intent("android.intent.action.QUICKBOOT_POWERON")
                    .setComponent(new android.content.ComponentName(AIRPLAY, AIRPLAY + ".BootReceiver"))
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES));
        } catch (Exception ignored) {
            // Receiver not installed: nothing to wake.
        }
    }

    /** The Home button while Our TV is open: back to this person's Home, like Apple TV. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (!intent.getBooleanExtra("fromHome", false)) return;
        if (screen == CLOCK) hideClock();
        if (overlay != NONE && overlay != CELEBRATE) closeOverlay();
        if (screen == SETTINGS || screen == DAYS) showHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        wakeAirPlay(this);
        repairHelper();
        glow.startDrift();
        h.post(tick);
        // A long absence starts again at the family, without replaying the opening.
        if (pausedAt > 0 && System.currentTimeMillis() - pausedAt > 30 * 60_000L && screen != INTRO) showWho();
        else if (screen == HOME) refreshHero();
        poke();
    }

    @Override
    protected void onPause() {
        super.onPause();
        pausedAt = System.currentTimeMillis();
        resumed = false;
        glow.stopDrift();
        h.removeCallbacks(tick);
        h.removeCallbacks(idle);
    }

    // ---------- helpers ----------

    private int person() {
        return screen == WHO ? who : active;
    }

    /**
     * A person's id by position, made safe: a phone can add or remove someone at any moment, and the
     * screen catches up a moment later.
     */
    static String id(int i) {
        String[] ids = IDS;
        return ids[Math.max(0, Math.min(ids.length - 1, i))];
    }

    private boolean ar(int i) {
        return "ar".equals(prefs.getString("lang_" + id(i), "en"));
    }

    boolean ar() {
        return ar(person());
    }

    String t(String en, String arabic) {
        return ar() ? arabic : en;
    }

    String name(int i) {
        String[] en = EN, ar = AR;
        int k = Math.max(0, Math.min(Math.min(en.length, ar.length) - 1, i));
        return ar(k) ? ar[k] : en[k];
    }

    /** The background photo sent from a phone; with it, a fifth mood, Photo, appears. */
    java.io.File wallFile() {
        return new java.io.File(getFilesDir(), "wallpaper.jpg");
    }

    private int moodCount() {
        return wallFile().exists() ? 5 : 4;
    }

    private boolean photoMood() {
        return prefs.getInt("mood", 0) == 4 && wallFile().exists();
    }

    int mood() {
        int m = prefs.getInt("mood", 0);
        if (m < 3) return m;
        // Under a photo, the screen's accents use the quiet Graphite colours.
        if (m == 4) return wallFile().exists() ? 2 : 0;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return hour >= 6 && hour < 19 ? 0 : 2;
    }

    /**
     * The colours on screen now: a person's birthday lights their own Home in their colour, Ramadan
     * and Eid bring their own palettes, and every other day uses the chosen mood.
     */
    int[] palette() {
        if (prefs.getBoolean("specialDays", true) && screen != WHO && screen != INTRO && Store.birthdayToday(prefs, active)) return Season.birthday(active);
        String s = Season.today(prefs);
        if (s != null) return Season.colors(s, active);
        return Ui.MOODS[mood()];
    }

    /** The mood button names a special day while its colours are showing. */
    private String moodName() {
        int[] p = palette();
        if (p == Season.RAMADAN_COLORS) return t("Ramadan", "رمضان");
        if (p == Season.EID_COLORS) return t("Eid", "العيد");
        if (p != Ui.MOODS[mood()]) return t("Birthday", "عيد ميلاد");
        return ar() ? MOOD_AR[prefs.getInt("mood", 0)] : MOOD_EN[prefs.getInt("mood", 0)];
    }

    // ---------- celebrations ----------

    private String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    /** Shows each celebration once a day; returns true when it was shown. */
    private boolean celebrateOnce(String kind, int who) {
        String key = "cel_" + kind + (Season.BIRTHDAY.equals(kind) ? id(who) : "");
        if (todayKey().equals(prefs.getString(key, ""))) return false;
        prefs.edit().putString(key, todayKey()).apply();
        showCelebrate(kind, who, !Season.BIRTHDAY.equals(kind));
        return true;
    }

    void showCelebrate(String kind, int who, boolean sound) {
        overlay = CELEBRATE;
        over.removeAllViews();
        Celebrate c = new Celebrate(this);
        FrameLayout f = c.build(kind, who);
        over.addView(f, new FrameLayout.LayoutParams(Ui.W, Ui.H));
        f.setAlpha(0f);
        f.animate().alpha(1f).setDuration(700).start();
        layer.animate().alpha(0f).setDuration(700).start();
        glow.setMood(Season.colors(kind, who));
        shownPalette = null;
        c.bokeh.start();
        if (sound) snd.play(Season.RAMADAN.equals(kind) ? "sig" : "bday");
        f.setTag(c);
        h.postDelayed(() -> { if (overlay == CELEBRATE && over.getChildAt(0) == f) closeCelebrate(); }, 15_000);
    }

    private void closeCelebrate() {
        View f = over.getChildAt(0);
        if (f != null && f.getTag() instanceof Celebrate) ((Celebrate) f.getTag()).bokeh.stop();
        overlay = NONE;
        if (f != null) f.animate().alpha(0f).setDuration(500).withEndAction(() -> { if (overlay == NONE) over.removeAllViews(); }).start();
        layer.animate().alpha(1f).setDuration(500).start();
        applyMood();
    }

    private void applyMood() {
        shownPalette = palette();
        glow.setMood(shownPalette);
        // Photo mood: the family's picture fills the screen; special days' colours still take over.
        boolean show = photoMood() && shownPalette == Ui.MOODS[mood()];
        long stamp = show ? wallFile().lastModified() : 0;
        if (stamp != wallStamp) {
            wallStamp = stamp;
            if (show) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inPreferredConfig = Bitmap.Config.RGB_565;
                wall.setImageBitmap(BitmapFactory.decodeFile(wallFile().getPath(), o));
            } else wall.setImageDrawable(null);
        }
        if (show) wall.setVisibility(View.VISIBLE);
        wall.animate().alpha(show ? 1f : 0f).setDuration(600).withEndAction(() -> {
            // Hidden layers cost nothing: under a photo the glow is invisible, so it is not drawn at all.
            wall.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
            glow.setVisibility(show ? View.INVISIBLE : View.VISIBLE);
            if (show) glow.stopDrift();
            else if (resumed) glow.startDrift();
        }).start();
    }

    String time() {
        return new SimpleDateFormat("h:mm", Locale.US).format(new Date());
    }

    /** The Home clock: the time large, with a small AM or PM after it. */
    CharSequence clockLabel() {
        String t = time(), s = t + " " + ampm();
        android.text.SpannableString sp = new android.text.SpannableString(s);
        sp.setSpan(new android.text.style.RelativeSizeSpan(0.42f), t.length(), s.length(), 0);
        sp.setSpan(new android.text.style.ForegroundColorSpan(Ui.DIM), t.length(), s.length(), 0);
        return sp;
    }

    /** AM or PM in the person's language, shown beside the big clocks. */
    String ampm() {
        boolean am = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < 12;
        return ar() ? (am ? "ص" : "م") : (am ? "AM" : "PM");
    }

    private int mx(int x, int w) {
        return ar() ? Ui.W - x - w : x;
    }

    <T extends View> T put(FrameLayout parent, T v, int x, int y, int w, int hgt) {
        parent.addView(v, new FrameLayout.LayoutParams(w, hgt));
        at(v, mx(x, w < 0 ? Ui.measureWidth(v) : w), y);
        return v;
    }

    /** Places a child of a FrameLayout. Translation is left for animations only. */
    static void at(View v, float x, float y) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        lp.leftMargin = Math.round(x);
        lp.topMargin = Math.round(y);
        v.setLayoutParams(lp);
    }

    TextView text(String s, int weight, float px, int color) {
        return Ui.text(this, s, Ui.sans(this, ar(), weight), px, color);
    }

    View logo(float enPx, float arPx) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        TextView en = Ui.text(this, Family.BRAND_EN, Ui.font(this, "Montserrat.ttf", 300), enPx, Ui.IVORY);
        en.setLetterSpacing(0.3f);
        TextView a = Ui.text(this, Family.BRAND_AR, Ui.font(this, "Alexandria.ttf", 400), arPx, Ui.IVORY);
        l.addView(en);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = Math.round(enPx * 0.7f);
        l.addView(a, lp);
        return l;
    }

    /** Blur by shrinking then enlarging with filtering: one-time cost, no GPU blur on this TV. */
    private Bitmap soften(Object key, Bitmap b) {
        Bitmap s = soft.get(key);
        if (s == null && b != null) {
            int w = 96, hh = Math.max(1, 96 * b.getHeight() / b.getWidth());
            Bitmap small = Bitmap.createScaledBitmap(b, w, hh, true);
            int[] px = new int[w * hh];
            small.getPixels(px, 0, w, 0, 0, w, hh);
            for (int pass = 0; pass < 3; pass++) boxBlur(px, w, hh, 6);
            small = Bitmap.createBitmap(px, w, hh, Bitmap.Config.ARGB_8888);
            s = Bitmap.createScaledBitmap(small, 480, Math.max(1, 480 * hh / w), true);
            soft.put(key, s);
        }
        return s;
    }

    /** Separable box blur on a tiny image; three passes look Gaussian. Runs once per picture. */
    static void boxBlur(int[] px, int w, int h, int r) {
        int[] tmp = new int[px.length];
        for (int pass = 0; pass < 2; pass++) {
            int[] src = pass == 0 ? px : tmp, dst = pass == 0 ? tmp : px;
            int lines = pass == 0 ? h : w, len = pass == 0 ? w : h;
            for (int line = 0; line < lines; line++) {
                for (int i = 0; i < len; i++) {
                    int a = 0, rr = 0, g = 0, bb = 0, n = 0;
                    for (int k = Math.max(0, i - r); k <= Math.min(len - 1, i + r); k++) {
                        int c = src[pass == 0 ? line * w + k : k * w + line];
                        a += c >>> 24;
                        rr += (c >> 16) & 255;
                        g += (c >> 8) & 255;
                        bb += c & 255;
                        n++;
                    }
                    dst[pass == 0 ? line * w + i : i * w + line] = (a / n) << 24 | (rr / n) << 16 | (g / n) << 8 | (bb / n);
                }
            }
        }
    }

    private void backdrop(Object key, Bitmap b, float alpha) {
        ImageView next = backs[1 - backOn], cur = backs[backOn];
        if (b == null) {
            for (ImageView v : backs) v.animate().alpha(0f).setDuration(500).withEndAction(() -> v.setVisibility(View.INVISIBLE)).start();
            return;
        }
        next.setImageBitmap(soften(key, b));
        next.setVisibility(View.VISIBLE);
        next.animate().alpha(alpha).setDuration(520).withEndAction(null).start();
        cur.animate().alpha(0f).setDuration(520).withEndAction(() -> { if (cur.getAlpha() == 0f) cur.setVisibility(View.INVISIBLE); }).start();
        backOn = 1 - backOn;
    }

    private void swap(FrameLayout next) {
        layer.removeAllViews();
        layer.addView(next, new FrameLayout.LayoutParams(Ui.W, Ui.H));
        next.setAlpha(0f);
        next.animate().alpha(1f).setDuration(260).start();
    }

    private void poke() {
        h.removeCallbacks(idle);
        h.postDelayed(idle, IDLE_MS);
    }

    // ---------- opening ----------

    private void showIntro() {
        screen = INTRO;
        backdrop(null, null, 0);
        FrameLayout f = new FrameLayout(this);
        View l = logo(56, 50);
        int w = Ui.measureWidth(l);
        f.addView(l, new FrameLayout.LayoutParams(-2, -2));
        at(l, (Ui.W - w) / 2f, Ui.H / 2f - l.getMeasuredHeight() / 2f);
        l.setAlpha(0f);
        l.setTranslationY(18);
        swap(f);
        l.animate().alpha(1f).translationY(0).setStartDelay(250).setDuration(1100)
                .setInterpolator(new DecelerateInterpolator()).start();
        h.postDelayed(() -> snd.play("sig"), 300);
        h.postDelayed(() -> { if (screen == INTRO) showWho(); }, 3200);
    }

    // ---------- choosing a person ----------

    private void showWho() {
        screen = WHO;
        overlay = NONE;
        over.removeAllViews();
        moodFocus = false;
        FrameLayout f = new FrameLayout(this);
        View l = logo(36, 40);
        int lw = Ui.measureWidth(l);
        f.addView(l, new FrameLayout.LayoutParams(-2, -2));
        at(l, (Ui.W - lw) / 2f, 64);
        discs.clear();
        names.clear();
        // One to eight people: the circles share the width, as large as four allow.
        int count = IDS.length;
        who = Math.max(0, Math.min(count - 1, who));
        int gap = Math.min(398, 1720 / count), size = Math.min(354, Math.round(gap * 0.89f)), top = 473 - size / 2;
        float scale = size / 354f;
        for (int i = 0; i < count; i++) {
            int slot = ar() ? count - 1 - i : i;
            int cx = Math.round(960 + (slot - (count - 1) / 2f) * gap);
            FrameLayout d = new FrameLayout(this);
            View ring = new View(this);
            ring.setBackground(Ui.ring(Math.max(4, Math.round(7 * scale)), Ui.IVORY));
            d.addView(ring, new FrameLayout.LayoutParams(size, size));
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageBitmap(avatar(i));
            Ui.clip(img, -1);
            int inset = Math.max(4, Math.round(7 * scale));
            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(size - inset * 2, size - inset * 2);
            ip.leftMargin = inset;
            ip.topMargin = inset;
            d.addView(img, ip);
            f.addView(d, new FrameLayout.LayoutParams(size, size));
            at(d, cx - size / 2f, top);
            discs.add(d);
            TextView n = Ui.text(this, name(i), Ui.sans(this, ar(i), 400), Math.max(32, 46 * scale), Ui.IVORY);
            n.setGravity(Gravity.CENTER);
            n.setEllipsize(android.text.TextUtils.TruncateAt.END);
            f.addView(n, new FrameLayout.LayoutParams(gap, -2));
            at(n, cx - gap / 2f, top + size + 58);
            names.add(n);
            d.setAlpha(0f);
            d.setTranslationY(40);
            d.animate().alpha(1f).translationY(0).setStartDelay(80L * slot).setDuration(520).setInterpolator(new DecelerateInterpolator()).start();
        }
        pills.clear();
        LinearLayout row = new LinearLayout(this);
        row.setLayoutDirection(ar() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        for (int m = 0; m < moodCount(); m++) {
            TextView p = text(ar() ? MOOD_AR[m] : MOOD_EN[m], 500, 28, Ui.IVORY);
            p.setPadding(30, 12, 30, 14);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginStart(8);
            lp.setMarginEnd(8);
            row.addView(p, lp);
            pills.add(p);
        }
        int rw = Ui.measureWidth(row);
        f.addView(row, new FrameLayout.LayoutParams(-2, -2));
        at(row, (Ui.W - rw) / 2f, 936);
        moodRow = row;
        TextView hint = text(t("Mood  ▾", "الأجواء  ▾"), 400, 26, Ui.DIM);
        f.addView(hint, new FrameLayout.LayoutParams(-2, -2));
        at(hint, (Ui.W - Ui.measureWidth(hint)) / 2f, 960);
        moodHint = hint;
        swap(f);
        drawWho(false);
        applyMood();
        String season = Season.today(prefs);
        if (season != null) h.postDelayed(() -> { if (screen == WHO && overlay == NONE) celebrateOnce(season, who); }, 900);
    }

    /** Each person chooses: their avatar, the first letter of their name, or a photo sent from a phone. */
    String pic(int i) {
        String p = prefs.getString("pic_" + id(i), "avatar");
        return "photo".equals(p) && !photoFile(i).exists() ? "avatar" : p;
    }

    private java.io.File photoFile(int i) {
        return new java.io.File(getFilesDir(), "photo-" + id(i) + ".jpg");
    }

    private String picKey(int i) {
        return i + pic(i) + ar(i) + prefs.getLong("picAt", 0);
    }

    Bitmap avatar(int i) {
        String key = picKey(i);
        Bitmap b = avatars.get(key);
        if (b != null) return b;
        String p = pic(i);
        try {
            if ("letter".equals(p)) b = letter(i);
            else if ("photo".equals(p)) b = BitmapFactory.decodeFile(photoFile(i).getPath());
            if (b == null) b = BitmapFactory.decodeStream(getAssets().open("avatars/avatar-" + id(i) + ".png"));
        } catch (Exception e) {
            // No avatar file for this person (they are kept out of the repository): use their letter.
            b = letter(i);
        }
        avatars.put(key, b);
        return b;
    }

    private Bitmap letter(int i) {
        Bitmap b = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        int[] letters = LETTER;
        int col = letters[Math.max(0, Math.min(letters.length - 1, i))];
        c.drawColor(col);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        // A soft light from the top so the colour feels like a surface, not a flat swatch.
        p.setShader(new android.graphics.LinearGradient(0, 0, 0, 512, 0x33FFFFFF, 0x26000000, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 512, 512, p);
        p.setShader(null);
        p.setColor(Color.WHITE);
        p.setTextAlign(android.graphics.Paint.Align.CENTER);
        p.setTypeface(Ui.sans(this, ar(i), 600));
        p.setTextSize(250);
        String s = name(i).substring(0, 1);
        android.graphics.Rect r = new android.graphics.Rect();
        p.getTextBounds(s, 0, s.length(), r);
        c.drawText(s, 256, 256 - r.exactCenterY(), p);
        return b;
    }

    private void drawWho(boolean animate) {
        float big = Math.min(1f, 4f / Math.max(4, discs.size()));
        for (int i = 0; i < discs.size(); i++) {
            View d = discs.get(i);
            boolean on = i == who && !moodFocus;
            float s = on ? 1.17f : 0.8f;
            if (animate) d.animate().scaleX(s).scaleY(s).alpha(on ? 1f : 0.55f).setStartDelay(0).setDuration(240).setInterpolator(new DecelerateInterpolator()).start();
            else {
                d.setScaleX(s);
                d.setScaleY(s);
                d.setAlpha(on ? 1f : 0.55f);
            }
            ((FrameLayout) d).getChildAt(0).setVisibility(on ? View.VISIBLE : View.INVISIBLE);
            TextView n = names.get(i);
            n.setTypeface(Ui.sans(this, ar(i), on ? 600 : 400));
            n.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, (on ? 56 : 36) * Math.max(0.7f, big));
            n.setTextColor(on ? Color.WHITE : Ui.DIM);
            n.animate().translationY(on ? 34 : -10).setDuration(240).start();
        }
        if (!moodFocus) backdrop("p" + picKey(who), avatar(who), 0.42f);
        if (moodRow != null) {
            moodRow.animate().alpha(moodFocus ? 1f : 0f).setDuration(220).start();
            moodHint.animate().alpha(moodFocus ? 0f : 1f).setDuration(220).start();
        }
        int sel = prefs.getInt("mood", 0);
        for (int m = 0; m < pills.size(); m++) {
            TextView p = pills.get(m);
            boolean on = m == sel;
            p.setBackground(Ui.round(on ? Ui.IVORY : 0x33000000, 40));
            p.setTextColor(on ? 0xFF0B0F17 : Ui.IVORY);
            p.setScaleX(moodFocus && on ? 1.12f : 1f);
            p.setScaleY(moodFocus && on ? 1.12f : 1f);
        }
    }

    private void whoKey(int dx, int dy, boolean ok, boolean back) {
        if (moodFocus) {
            if (dx != 0) {
                prefs.edit().putInt("mood", (prefs.getInt("mood", 0) + dx + moodCount()) % moodCount()).apply();
                snd.play("toggle");
                applyMood();
            }
            if (dy < 0 || ok || back) moodFocus = false;
            drawWho(true);
            return;
        }
        if (dy > 0) {
            moodFocus = true;
            snd.play("menu");
            drawWho(true);
            return;
        }
        if (dx != 0) {
            who = (who + dx + IDS.length) % IDS.length;
            snd.play("focus");
            drawWho(true);
        }
        if (dy < 0) {
            prefs.edit().putString("lang_" + id(who), ar(who) ? "en" : "ar").apply();
            showWho();
        }
        if (ok) {
            snd.play(Store.birthdayToday(prefs, who) ? "bday" : "pick");
            active = who;
            activeId = id(who);
            prefs.edit().putInt("last", who).apply();
            View d = discs.get(who);
            d.animate().scaleX(1.5f).scaleY(1.5f).alpha(0f).setDuration(420).start();
            h.postDelayed(this::showHome, 380);
        }
        // When Our TV is the Home button, leaving would only bring it straight back; the way out is in settings.
        if (back && !(prefs.getBoolean("homeRedirect", false) && Helper.running())) finish();
    }

    // ---------- home ----------

    private List<String> loadDock() {
        String saved = prefs.getString("dock_" + id(active), null);
        List<String> out = new ArrayList<>();
        List<String> src = saved == null ? Arrays.asList(DEFAULT_DOCK) : Arrays.asList(saved.split(","));
        // The family's apps first; Our TV's own (Today, Memories, Adhkar) always last and never hidden.
        for (String pkg : src) if (!pkg.isEmpty() && !DAY.equals(pkg) && !MEM.equals(pkg) && !ADH.equals(pkg) && launchable(pkg)) out.add(pkg);
        out.add(DAY);
        out.add(MEM);
        out.add(ADH);
        return out;
    }

    private void saveDock() {
        List<String> apps = new ArrayList<>(dock);
        apps.remove(DAY);
        apps.remove(MEM);
        apps.remove(ADH);
        prefs.edit().putString("dock_" + id(active), String.join(",", apps)).apply();
    }

    private boolean launchable(String pkg) {
        if (DAY.equals(pkg) || MEM.equals(pkg) || ADH.equals(pkg)) return true;
        PackageManager pm = getPackageManager();
        return pm.getLeanbackLaunchIntentForPackage(pkg) != null || pm.getLaunchIntentForPackage(pkg) != null;
    }

    private String label(String pkg) {
        if (DAY.equals(pkg)) return t("Today", "اليوم");
        if (MEM.equals(pkg)) return t("Memories", "الذكريات");
        if (ADH.equals(pkg)) return t("Adhkar", "أذكار");
        if (AIRPLAY.equals(pkg)) return t("iPad & iPhone", "آيباد وآيفون");
        try {
            return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private Bitmap banner(String pkg) {
        if (DAY.equals(pkg)) return dayBanner();
        if (MEM.equals(pkg)) return memBanner();
        if (ADH.equals(pkg)) return adhBanner();
        Bitmap b = banners.get(pkg);
        if (b != null) return b;
        if (AIRPLAY.equals(pkg)) return mirrorBanner();
        Drawable d = null, icon = null;
        try {
            d = getPackageManager().getApplicationBanner(pkg);
            if (d == null) icon = getPackageManager().getApplicationIcon(pkg);
        } catch (Exception ignored) {
            // Unknown app: a plain tile is drawn instead.
        }
        b = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        if (d != null) {
            d.setBounds(0, 0, 320, 180);
            d.draw(cv);
        } else {
            cv.drawColor(0xFF1C2230);
            if (icon != null) {
                icon.setBounds(100, 30, 220, 150);
                icon.draw(cv);
            }
        }
        banners.put(pkg, b);
        colors.put(pkg, brandColor(b));
        return b;
    }

    /** Back from one of Our TV's own apps: Home with that same tile still selected. */
    private void homeOn(String tile) {
        showHome();
        int i = dock.indexOf(tile);
        if (i >= 0) {
            app = i;
            row = 0;
            drawHome();
        }
    }

    // ---------- adhkar ----------

    private AdhkarScreen adh;

    private void showAdhkar() {
        snd.play("open");
        screen = ADHK;
        backdrop(null, null, 0);
        adh = new AdhkarScreen(this);
        swap(adh.view());
    }

    private void adhKey(int dx, int dy, boolean ok, boolean back) {
        if (back) {
            adh = null;
            snd.play("back");
            homeOn(ADH);
            return;
        }
        adh.key(dx, dy, ok);
    }

    /** The Adhkar tile: calm teal, the word in large Arabic type and which adhkar are due now. */
    private Bitmap adhBanner() {
        int sec = Adhkar.sectionNow(prefs);
        String key = ADH + sec;
        Bitmap b = banners.get(key);
        if (b != null) return b;
        b = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setShader(new android.graphics.LinearGradient(0, 0, 320, 180, 0xFF0E3B43, 0xFF1F6F6A, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 320, 180, p);
        p.setShader(new android.graphics.RadialGradient(260, 30, 180, 0x55F2C879, 0, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 320, 180, p);
        p.setShader(null);
        p.setColor(Color.WHITE);
        p.setTextAlign(android.graphics.Paint.Align.RIGHT);
        p.setTypeface(Ui.font(this, "Alexandria.ttf", 700));
        p.setTextSize(54);
        c.drawText("أذكار", 296, 112, p);
        p.setTypeface(Ui.font(this, "Alexandria.ttf", 400));
        p.setTextSize(24);
        p.setColor(0xD9FFFFFF);
        c.drawText(Adhkar.SECTIONS_AR[sec], 296, 152, p);
        banners.put(key, b);
        colors.put(ADH, 0xFF2E9C8F);
        return b;
    }

    // ---------- memories ----------

    private MemoriesScreen mem;

    private void showMemories() {
        snd.play("open");
        screen = MEMS;
        backdrop(null, null, 0);
        mem = new MemoriesScreen(this, false);
        swap(mem.view());
        mem.start();
    }

    private void memKey(int dx, boolean ok, boolean back) {
        if (back) {
            mem.stop();
            mem = null;
            snd.play("back");
            homeOn(MEM);
            return;
        }
        if (dx != 0) snd.play("page");
        mem.key(dx, ok);
    }

    /** The Memories tile: the newest photo, darkened at the bottom, with the name over it. */
    private Bitmap memBanner() {
        List<Memories.Photo> all = Memories.all(this, prefs);
        String newest = all.isEmpty() ? "" : all.get(all.size() - 1).file;
        String key = MEM + newest + ar();
        Bitmap b = banners.get(key);
        if (b != null) return b;
        b = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
        Bitmap photo = newest.isEmpty() ? null : Memories.load(this, newest, 640);
        if (photo != null) {
            float s = Math.max(320f / photo.getWidth(), 180f / photo.getHeight());
            float w = 320 / s, hgt = 180 / s;
            c.drawBitmap(photo, new android.graphics.Rect((int) ((photo.getWidth() - w) / 2), (int) ((photo.getHeight() - hgt) / 2),
                    (int) ((photo.getWidth() + w) / 2), (int) ((photo.getHeight() + hgt) / 2)), new android.graphics.RectF(0, 0, 320, 180), p);
        } else {
            p.setShader(new android.graphics.LinearGradient(0, 0, 320, 180, 0xFFE8A04A, 0xFFD6488A, android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(0, 0, 320, 180, p);
        }
        p.setShader(new android.graphics.LinearGradient(0, 70, 0, 180, 0x00000000, 0xB3000000, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 320, 180, p);
        p.setShader(null);
        p.setColor(Color.WHITE);
        p.setTypeface(Ui.sans(this, ar(), 700));
        p.setTextSize(38);
        p.setTextAlign(ar() ? android.graphics.Paint.Align.RIGHT : android.graphics.Paint.Align.LEFT);
        c.drawText(label(MEM), ar() ? 296 : 24, 156, p);
        banners.put(key, b);
        colors.put(MEM, photo != null ? brandColor(b) : 0xFFE8A04A);
        return b;
    }

    /** The mirroring tile: a tablet sending its screen to the TV, drawn because the receiver has no banner. */
    private Bitmap mirrorBanner() {
        Bitmap b = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setShader(new android.graphics.LinearGradient(0, 0, 320, 180, 0xFF2B3A67, 0xFF5B4B9A, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 320, 180, p);
        p.setShader(new android.graphics.RadialGradient(240, 50, 160, 0x66A78BFA, 0, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 320, 180, p);
        p.setShader(null);
        p.setColor(Color.WHITE);
        p.setStyle(android.graphics.Paint.Style.STROKE);
        p.setStrokeWidth(4);
        p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        c.drawRoundRect(new android.graphics.RectF(44, 54, 98, 126), 9, 9, p);
        c.drawRoundRect(new android.graphics.RectF(176, 46, 282, 114), 8, 8, p);
        c.drawLine(210, 130, 248, 130, p);
        p.setStrokeWidth(3.5f);
        for (int i = 0; i < 3; i++) {
            p.setAlpha(255 - i * 70);
            float r = 16 + i * 14;
            c.drawArc(new android.graphics.RectF(118 - r, 88 - r, 118 + r, 88 + r), -40, 80, false, p);
        }
        banners.put(AIRPLAY, b);
        colors.put(AIRPLAY, 0xFF7C6BD6);
        return b;
    }

    /** The next prayer; in Ramadan, iftar while the day is fasting. */
    private String dayLine(double[] nx) {
        if (Season.RAMADAN.equals(Season.today(prefs))) {
            double[] t = Prayer.times(Calendar.getInstance(), prefs);
            Calendar now = Calendar.getInstance();
            double hr = now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60.0;
            if (hr > t[0] && hr < t[4]) return t("Iftar at ", "الإفطار ") + Prayer.hmA(t[4], ar());
        }
        return (ar() ? Prayer.AR : Prayer.EN)[(int) nx[0]] + " " + t("at ", "") + Prayer.hmA(nx[2], ar());
    }

    /** The Today tile: the mood's colours, a small sun on its arc, and the next prayer time. */
    private Bitmap dayBanner() {
        int[] m = palette();
        String key = DAY + java.util.Arrays.hashCode(m) + ar() + Prayer.next(prefs)[0];
        Bitmap b = banners.get(key);
        if (b != null) return b;
        b = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setShader(new android.graphics.LinearGradient(0, 0, 320, 180, m[2], m[1], android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 320, 180, p);
        p.setShader(new android.graphics.RadialGradient(250, 40, 170, Ui.alpha(m[3], 0.7f), 0, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 320, 180, p);
        p.setShader(null);
        p.setStyle(android.graphics.Paint.Style.STROKE);
        p.setStrokeWidth(3);
        p.setColor(0x80FFFFFF);
        c.drawArc(new android.graphics.RectF(40, 60, 280, 260), 200, 140, false, p);
        p.setStyle(android.graphics.Paint.Style.FILL);
        p.setColor(0xFFFFF8E6);
        c.drawCircle(214, 76, 11, p);
        p.setTypeface(Ui.sans(this, ar(), 700));
        p.setTextSize(40);
        p.setColor(Color.WHITE);
        p.setTextAlign(ar() ? android.graphics.Paint.Align.RIGHT : android.graphics.Paint.Align.LEFT);
        float tx = ar() ? 296 : 24;
        c.drawText(label(DAY), tx, 132, p);
        double[] nx = Prayer.next(prefs);
        p.setTypeface(Ui.sans(this, ar(), 500));
        p.setTextSize(22);
        p.setColor(0xD9FFFFFF);
        c.drawText(dayLine(nx), tx, 162, p);
        banners.put(key, b);
        colors.put(DAY, m[1]);
        return b;
    }

    /** The app's most vivid colour, so the shelf glows in Netflix red or Spotify green. */
    private int brandColor(Bitmap b) {
        float[] hsv = new float[3];
        long r = 0, g = 0, bl = 0;
        int n = 0;
        for (int y = 4; y < b.getHeight(); y += 8) {
            for (int x = 4; x < b.getWidth(); x += 8) {
                int c = b.getPixel(x, y);
                Color.colorToHSV(c, hsv);
                if (hsv[1] > 0.45f && hsv[2] > 0.35f && Color.alpha(c) > 200) {
                    r += Color.red(c);
                    g += Color.green(c);
                    bl += Color.blue(c);
                    n++;
                }
            }
        }
        return n < 6 ? palette()[1] : Color.rgb((int) (r / n), (int) (g / n), (int) (bl / n));
    }

    private void showHome() {
        screen = HOME;
        overlay = NONE;
        over.removeAllViews();
        row = 0;
        top = 0;
        dock.clear();
        dock.addAll(loadDock());
        app = 0;
        long newest = 0;
        for (int i = 0; i < dock.size(); i++) {
            long t = prefs.getLong("recent_" + id(active) + "_" + dock.get(i), 0);
            if (t > newest) {
                newest = t;
                app = i;
            }
        }
        FrameLayout f = new FrameLayout(this);
        heroGlow = new HeroGlow(this);
        heroGlow.rtl = ar();
        f.addView(heroGlow, new FrameLayout.LayoutParams(Ui.W, Ui.H));
        View shade = new View(this);
        shade.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x99000000, 0x00000000, 0x00000000, 0x66000000}));
        f.addView(shade, new FrameLayout.LayoutParams(Ui.W, Ui.H));

        // Top bar: logo, person, then clock and the three round controls.
        topItems.clear();
        View l = logo(22, 26);
        put(f, l, 84, 70, -2, -2);
        int x = 84 + Ui.measureWidth(l) + 36;
        LinearLayout me = new LinearLayout(this);
        me.setGravity(Gravity.CENTER_VERTICAL);
        me.setLayoutDirection(ar() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        me.setPadding(6, 6, 26, 6);
        meImg = new ImageView(this);
        meImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        meImg.setImageBitmap(avatar(active));
        Ui.clip(meImg, -1);
        me.addView(meImg, new LinearLayout.LayoutParams(72, 72));
        TextView nm = text(name(active), 600, 38, Ui.IVORY);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-2, -2);
        np.setMarginStart(18);
        me.addView(nm, np);
        put(f, me, x, 42, -2, -2);
        topItems.add(me);

        int right = Ui.W - 84;
        IconView gear = new IconView(this, IconView.GEAR), note = new IconView(this, IconView.NOTE), moon = new IconView(this, IconView.MOON),
                input = new IconView(this, IconView.INPUT);
        TextView moodPill = text(moodName(), 500, 26, Ui.IVORY);
        moodPill.setPadding(28, 14, 28, 16);
        moodPill.setBackground(Ui.round(0x1FFFFFFF, 40));
        int[] sizes = {64, 64, 64, 64};
        View[] icons = {gear, note, moon, input};
        for (int i = 0; i < icons.length; i++) {
            right -= sizes[i];
            put(f, icons[i], right, 48, 64, 64);
            right -= 20;
        }
        int pw = Ui.measureWidth(moodPill);
        right -= pw;
        put(f, moodPill, right, 50, -2, -2);
        right -= 40;
        clockText = Ui.text(this, "", Ui.font(this, "Montserrat.ttf", 300), 60, Ui.IVORY);
        clockText.setText(clockLabel());
        int cw = Ui.measureWidth(clockText);
        right -= cw;
        put(f, clockText, right, 46, -2, -2);
        topItems.add(moodPill);
        topItems.add(input);
        topItems.add(moon);
        topItems.add(note);
        topItems.add(gear);

        // Shelf text, then the dock.
        heroLabel = text("", 500, 30, Ui.IVORY);
        heroTitle = text("", 700, 104, Ui.IVORY);
        heroTitle.setLetterSpacing(-0.02f);
        heroSub = text("", 400, 32, Ui.DIM);
        for (TextView tv : new TextView[] {heroLabel, heroTitle, heroSub}) {
            tv.setGravity(ar() ? Gravity.END : Gravity.START);
        }
        put(f, heroLabel, 84, 520, 1300, -2);
        put(f, heroTitle, 84, 566, 1300, -2);
        put(f, heroSub, 84, 694, 1300, -2);

        dockBand = new View(this);
        dockBand.setBackground(null);
        put(f, dockBand, 84, 792, 1752, 232);
        tiles.clear();
        tileLabels.clear();
        List<String> all = new ArrayList<>(dock);
        all.add(ADD);
        int n = all.size(), gap = 40, inner = 1752;
        // Tiles never shrink below a comfortable size; when they no longer fit, the row slides like Apple TV's.
        int tw = Math.max(300, Math.min(368, (inner - gap * (n - 1)) / n)), th = tw * 9 / 16;
        int total = tw * n + gap * (n - 1);
        int start = total <= inner ? 84 + (inner - total) / 2 : 84, ty = Ui.H - 112 - th;
        dockShift = 0;
        tileW = tw;
        for (int i = 0; i < n; i++) {
            View tile = tile(all.get(i), tw);
            put(f, tile, start + i * (tw + gap), ty, tw, th);
            tiles.add(tile);
            TextView lb = text(ADD.equals(all.get(i)) ? t("Add", "إضافة") : label(all.get(i)), 500, 28, Ui.IVORY);
            lb.setGravity(Gravity.CENTER);
            put(f, lb, start + i * (tw + gap) - 20, ty + th + 26, tw + 40, -2);
            lb.setAlpha(0f);
            tileLabels.add(lb);
        }
        swap(f);
        drawHome();
        applyMood();
        if (prefs.getBoolean("specialDays", true) && Store.birthdayToday(prefs, active)) celebrateOnce(Season.BIRTHDAY, active);
        Weather.refresh(prefs, () -> h.post(() -> { if (screen == HOME && app < dock.size() && DAY.equals(dock.get(app))) refreshHero(); }));
        askListings();
    }

    /** A short message over the current screen that fades by itself. */
    private void toast(String msg) {
        if (layer.getChildCount() == 0) return;
        FrameLayout screenView = (FrameLayout) layer.getChildAt(0);
        TextView pill = text(msg, 600, 34, 0xFF0B0F17);
        pill.setPadding(40, 18, 40, 20);
        pill.setBackground(Ui.round(Ui.IVORY, 44));
        screenView.addView(pill, new FrameLayout.LayoutParams(-2, -2));
        at(pill, (Ui.W - Ui.measureWidth(pill)) / 2f, 150);
        pill.setAlpha(0f);
        pill.setTranslationY(-16);
        pill.animate().alpha(1f).translationY(0).setStartDelay(400).setDuration(400).start();
        h.postDelayed(() -> pill.animate().alpha(0f).setDuration(600).withEndAction(() -> screenView.removeView(pill)).start(), 6000);
    }

    private void refreshMe() {
        if (meImg != null) meImg.setImageBitmap(avatar(active));
    }

    /** Continue Watching needs the TV's permission once; the TV asks the family itself. */
    private void askListings() {
        String perm = "android.permission.READ_TV_LISTINGS";
        if (checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED || prefs.getBoolean("askedListings", false)) return;
        prefs.edit().putBoolean("askedListings", true).apply();
        try {
            requestPermissions(new String[] {perm}, 1);
        } catch (Exception ignored) {
            // Not a TV build: Continue Watching stays off.
        }
    }

    private View tile(String pkg, int w) {
        FrameLayout t = new FrameLayout(this);
        Ui.clip(t, 28);
        if (ADD.equals(pkg)) {
            t.setBackground(Ui.round(0x24FFFFFF, 28));
            TextView plus = text("+", 300, 90, Ui.IVORY);
            plus.setGravity(Gravity.CENTER);
            t.addView(plus, new FrameLayout.LayoutParams(-1, -1));
        } else {
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageBitmap(banner(pkg));
            t.addView(img, new FrameLayout.LayoutParams(-1, -1));
        }
        View ring = new View(this);
        android.graphics.drawable.GradientDrawable g = Ui.round(0, 28);
        g.setStroke(6, Color.WHITE);
        ring.setBackground(g);
        ring.setVisibility(View.INVISIBLE);
        t.addView(ring, new FrameLayout.LayoutParams(-1, -1));
        return t;
    }

    private void drawHome() {
        // Slide the row just enough to keep the focused tile on screen, with room for its zoom.
        if (app < tiles.size()) {
            int left = ((FrameLayout.LayoutParams) tiles.get(app).getLayoutParams()).leftMargin, pad = 110;
            if (left + dockShift < pad) dockShift = pad - left;
            else if (left + tileW + dockShift > Ui.W - pad) dockShift = Ui.W - pad - tileW - left;
            int first = ((FrameLayout.LayoutParams) tiles.get(0).getLayoutParams()).leftMargin;
            int last = ((FrameLayout.LayoutParams) tiles.get(tiles.size() - 1).getLayoutParams()).leftMargin;
            int min = Math.min(first, last), max = Math.max(first, last) + tileW;
            if (max - min <= Ui.W - 168) dockShift = 0;
        }
        for (int i = 0; i < tiles.size(); i++) {
            View t = tiles.get(i);
            boolean on = row == 0 && i == app;
            float s = on ? (overlay == MOVE ? 1.16f : 1.1f) : 1f;
            t.animate().scaleX(s).scaleY(s).translationY(on ? -10 : 0).translationX(dockShift).setDuration(200).setInterpolator(new DecelerateInterpolator()).start();
            ((FrameLayout) t).getChildAt(((FrameLayout) t).getChildCount() - 1).setVisibility(on ? View.VISIBLE : View.INVISIBLE);
            // No shadow under the see-through "+" tile: it would show through as a dark inner box.
            if (i < tileLabels.size()) tileLabels.get(i).animate().alpha(on ? 1f : 0f).translationY(on ? 6 : 0).translationX(dockShift).setDuration(200).start();
        }
        for (int i = 0; i < topItems.size(); i++) {
            View v = topItems.get(i);
            boolean on = row == -1 && i == top;
            if (v instanceof IconView) ((IconView) v).setOn(on);
            else if (i == 0) v.setBackground(on ? Ui.round(0x26FFFFFF, 50) : null);
            else {
                TextView p = (TextView) v;
                p.setBackground(Ui.round(on ? Ui.IVORY : 0x1FFFFFFF, 40));
                p.setTextColor(on ? 0xFF0B0F17 : Ui.IVORY);
            }
        }
        refreshHero();
    }

    /** What each app shares (Spotify's song, Continue Watching), by app and person: {when asked, answer}. */
    private final Map<String, Object[]> lookups = new HashMap<>();

    /**
     * The last known answer straight away, and a fresh one fetched off the screen's thread at most every
     * 20 seconds: asking the TV's listings while moving between tiles made every step wait.
     */
    private Object lookup(String pkg) {
        String key = pkg + "|" + active;
        long now = android.os.SystemClock.uptimeMillis();
        Object[] slot = lookups.get(key);
        if (slot == null || now - (Long) slot[0] > 20_000) {
            if (slot == null) lookups.put(key, slot = new Object[] {now, null});
            else slot[0] = now;
            final Object[] s = slot;
            final int who = active;
            new Thread(() -> {
                Object v = SPOTIFY.equals(pkg) ? Watch.song(this, pkg) : Watch.resume(this, prefs, pkg, who);
                h.post(() -> {
                    boolean changed = !describe(s[1]).equals(describe(v));
                    s[1] = v;
                    if (changed && screen == HOME && overlay == NONE && app < dock.size() && pkg.equals(dock.get(app))) refreshHero();
                });
            }, "lookup").start();
        }
        return slot[1];
    }

    private static String describe(Object o) {
        if (o instanceof Watch.Song) return ((Watch.Song) o).title + ((Watch.Song) o).playing;
        if (o instanceof Watch.Item) return ((Watch.Item) o).title + ((Watch.Item) o).detail + ((Watch.Item) o).progress;
        return "";
    }

    private void refreshHero() {
        if (screen != HOME || heroTitle == null) return;
        String pkg = app < dock.size() ? dock.get(app) : ADD;
        boolean own = ADD.equals(pkg) || DAY.equals(pkg) || MEM.equals(pkg) || ADH.equals(pkg) || AIRPLAY.equals(pkg);
        Object found = own ? null : lookup(pkg);
        Watch.Song song = found instanceof Watch.Song ? (Watch.Song) found : null;
        resume = found instanceof Watch.Item ? (Watch.Item) found : null;
        if (ADH.equals(pkg)) {
            int sec = Adhkar.sectionNow(prefs);
            heroLabel.setText("");
            heroTitle.setText(label(ADH));
            heroSub.setText(Adhkar.SECTIONS_AR[sec] + "  ·  " + Adhkar.ALL[sec].length + t(" adhkar", " ذكراً"));
            heroGlow.setColor(0xFF2E9C8F);
            backdrop(null, null, 0);
        } else if (MEM.equals(pkg)) {
            int n = Memories.all(this, prefs).size(), today = Memories.onThisDay(this, prefs);
            heroLabel.setText(today > 0 ? t("On this day", "في مثل هذا اليوم") : "");
            heroLabel.setTextColor(Ui.IVORY);
            heroTitle.setText(label(MEM));
            heroSub.setText(n == 0 ? t("Add your photos from the phone page", "أضيفوا صوركم من صفحة الهاتف")
                    : today > 0 ? (ar() ? today + " صور من هذا اليوم في سنوات مضت" : today + (today == 1 ? " photo" : " photos") + " from this day in other years")
                    : (ar() ? n + " صورة" : n + (n == 1 ? " photo" : " photos")));
            banner(pkg);
            heroGlow.setColor(colors.get(MEM));
            backdrop("mem" + n, banner(pkg), 0.45f);
        } else if (DAY.equals(pkg)) {
            double[] nx = Prayer.next(prefs);
            org.json.JSONObject wx = Weather.cached(prefs);
            org.json.JSONObject cur = wx == null ? null : wx.optJSONObject("current");
            heroLabel.setText(Prayer.hijriText(prefs, ar()));
            heroLabel.setTextColor(Ui.DIM);
            heroTitle.setText(label(DAY));
            heroSub.setText(dayLine(nx)
                    + (cur == null ? "" : "  ·  " + Math.round(cur.optDouble("temperature_2m")) + "°  " + Weather.describe(cur.optInt("weather_code"), ar())));
            heroGlow.setColor(palette()[1]);
            backdrop(null, null, 0);
        } else if (song != null) {
            banner(pkg);
            heroLabel.setText(song.playing ? t("Now playing", "يُشغَّل الآن") : t("Paused", "متوقف مؤقتاً"));
            heroLabel.setTextColor(lighten(colors.get(pkg)));
            heroTitle.setText(song.title);
            heroSub.setText((song.artist == null ? "" : song.artist + "  ·  ") + label(pkg));
            heroGlow.setColor(colors.get(pkg));
            backdrop("song" + song.title, song.art != null ? song.art : banner(pkg), 0.42f);
        } else if (resume != null) {
            banner(pkg);
            heroLabel.setText(t("Continue watching", "متابعة المشاهدة"));
            heroLabel.setTextColor(lighten(colors.get(pkg)));
            heroTitle.setText(resume.title);
            heroSub.setText((resume.detail == null ? "" : resume.detail + "  ·  ") + label(pkg)
                    + (resume.progress > 0 ? "  ·  " + resume.progress + "%" : ""));
            heroGlow.setColor(colors.get(pkg));
            backdrop(pkg, banner(pkg), 0.34f);
            String want = pkg;
            Watch.poster(this, resume.poster, h, b -> {
                if (screen == HOME && app < dock.size() && want.equals(dock.get(app))) backdrop("poster" + want, b, 0.5f);
            });
        } else if (ADD.equals(pkg)) {
            heroLabel.setText("");
            heroTitle.setText(t("Add an app", "أضف تطبيقاً"));
            heroSub.setText(t("Choose from the apps on this TV", "اختر من تطبيقات هذا التلفاز"));
            heroGlow.setColor(palette()[1]);
            backdrop(null, null, 0);
        } else {
            banner(pkg);
            heroLabel.setText("");
            heroLabel.setTextColor(Ui.alpha(colors.get(pkg), 1f) == 0 ? Ui.IVORY : lighten(colors.get(pkg)));
            heroTitle.setText(label(pkg));
            long last = prefs.getLong("recent_" + id(active) + "_" + pkg, 0);
            heroSub.setText(last == 0 ? t("Press OK to open", "اضغط موافق للفتح") : t("Opened ", "فُتح ") + ago(last));
            if (AIRPLAY.equals(pkg)) heroSub.setText(t("iPad: Control Centre  ›  Screen Mirroring  ›  " + Family.tvName(),
                    "الآيباد: مركز التحكم  ›  محاكاة الشاشة  ›  " + Family.tvName()));
            heroGlow.setColor(colors.get(pkg));
            backdrop(pkg, banner(pkg), 0.34f);
        }
        heroTitle.setAlpha(0f);
        heroTitle.setTranslationY(16);
        heroTitle.animate().alpha(1f).translationY(0).setDuration(260).start();
    }

    private int lighten(int c) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        hsv[1] = Math.min(hsv[1], 0.55f);
        hsv[2] = 1f;
        return Color.HSVToColor(hsv);
    }

    private String ago(long t) {
        long m = (System.currentTimeMillis() - t) / 60_000;
        if (m < 2) return t("just now", "الآن");
        if (m < 60) return ar() ? "منذ " + m + " دقيقة" : m + " minutes ago";
        long hrs = m / 60;
        if (hrs < 24) return ar() ? "منذ " + hrs + " ساعة" : hrs + (hrs == 1 ? " hour ago" : " hours ago");
        long d = hrs / 24;
        return ar() ? "منذ " + d + " يوم" : d + (d == 1 ? " day ago" : " days ago");
    }

    private void homeKey(int dx, int dy, boolean ok, boolean back) {
        int wasRow = row, wasApp = app, wasTop = top;
        if (dy < 0 && row == 0) row = -1;
        else if (dy > 0 && row == -1) row = 0;
        if (dx != 0) {
            if (row == 0) app = Math.max(0, Math.min(tiles.size() - 1, app + dx));
            else top = Math.max(0, Math.min(topItems.size() - 1, top + dx));
        }
        // A soft tick for every move, and a muted bump at the end of a row.
        if (dx != 0 || dy != 0) snd.play(row != wasRow || app != wasApp || top != wasTop ? "focus" : "edge");
        if (back) {
            snd.play("back");
            showWho();
            return;
        }
        if (ok) {
            if (row == -1) {
                if (top == 0) showWho();
                else if (top == 1) {
                    prefs.edit().putInt("mood", (prefs.getInt("mood", 0) + 1) % moodCount()).apply();
                    snd.play("toggle");
                    applyMood();
                    showHome();
                    row = -1;
                    top = 1;
                } else if (top == 2) showInputs();
                else if (top == 3) showNight();
                else if (top == 4) openSettings("sound");
                else startSafe(new Intent(Settings.ACTION_SETTINGS));
                drawHome();
                return;
            }
            if (app >= dock.size()) showPick();
            else if (DAY.equals(dock.get(app))) showDay();
            else if (MEM.equals(dock.get(app))) showMemories();
            else if (ADH.equals(dock.get(app))) showAdhkar();
            else launch(dock.get(app));
            return;
        }
        drawHome();
    }

    private void launch(String pkg) {
        snd.play("open");
        prefs.edit().putLong("recent_" + id(active) + "_" + pkg, System.currentTimeMillis()).apply();
        overlay = NOTE;
        over.removeAllViews();
        FrameLayout f = new FrameLayout(this);
        f.setBackgroundColor(Color.BLACK);
        ImageView img = new ImageView(this);
        img.setImageBitmap(banner(pkg));
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        f.addView(img, new FrameLayout.LayoutParams(560, 315));
        at(img, 680, 310);
        if (AIRPLAY.equals(pkg)) {
            TextView m = text(t("On the iPad or iPhone: Control Centre  ›  Screen Mirroring  ›  " + Family.tvName(),
                    "على الآيباد أو الآيفون: مركز التحكم  ›  محاكاة الشاشة  ›  " + Family.tvName()), 400, 34, Ui.DIM);
            m.setGravity(Gravity.CENTER);
            f.addView(m, new FrameLayout.LayoutParams(Ui.W, -2));
            at(m, 0, 680);
        } else if (ASKS.contains(pkg)) {
            TextView m = text(ar() ? name(active) + "، اختر ملفك إذا طلب التطبيق" : name(active) + ", choose your profile if the app asks", 400, 34, Ui.DIM);
            m.setGravity(Gravity.CENTER);
            f.addView(m, new FrameLayout.LayoutParams(Ui.W, -2));
            at(m, 0, 680);
        }
        over.addView(f, new FrameLayout.LayoutParams(Ui.W, Ui.H));
        f.setAlpha(0f);
        f.animate().alpha(1f).setDuration(220).start();
        Watch.Item cont = resume != null && pkg.equals(resume.pkg) ? resume : null;
        h.postDelayed(() -> {
            PackageManager pm = getPackageManager();
            // Straight back into the episode when the app shared one; otherwise the app's own start.
            Intent i = cont != null ? Watch.intent(cont) : null;
            if (i == null) i = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) i = pm.getLaunchIntentForPackage(pkg);
            startSafe(i);
            h.postDelayed(() -> { over.removeAllViews(); overlay = NONE; }, 800);
        }, 1100);
    }

    private void startSafe(Intent i) {
        if (i == null) return;
        try {
            startActivity(i);
        } catch (Exception ignored) {
            // The app was removed or cannot open; Home stays usable.
        }
    }

    // ---------- holding OK ----------

    private void onLong() {
        if (screen == WHO && overlay == NONE && !moodFocus) {
            // Holding OK on a person: remove them, or add someone new (from the phone, where typing is easy).
            showList(PERSON, IDS.length > 1 ? new String[] {t("Remove ", "إزالة ") + name(who), t("Add a person", "إضافة شخص"), t("Cancel", "إلغاء")}
                    : new String[] {t("Add a person", "إضافة شخص"), t("Cancel", "إلغاء")}, name(who));
            return;
        }
        if (screen == HOME && overlay == NONE && row == 0 && app < dock.size() && !DAY.equals(dock.get(app)) && !MEM.equals(dock.get(app)) && !ADH.equals(dock.get(app))) showList(EDIT,
                new String[] {t("Move", "نقل"), t("Hide from Home", "إخفاء من الشاشة"), t("Cancel", "إلغاء")}, label(dock.get(app)));
        else if (screen == HOME && overlay == NONE && row == -1 && top == 5) openSettings("family");
        else ok();
    }

    // ---------- small menus over the screen ----------

    private void showList(int kind, String[] items, String title) {
        snd.play("menu");
        overlay = kind;
        osel = 0;
        over.removeAllViews();
        overRows.clear();
        FrameLayout f = new FrameLayout(this);
        f.setBackgroundColor(0xCC05080F);
        // Short titles are big; a sentence wraps onto up to three lines at a calmer size.
        boolean longTitle = title.length() > 40;
        TextView tt = text(title, longTitle ? 600 : 700, longTitle ? 42 : 56, Ui.IVORY);
        tt.setSingleLine(false);
        tt.setMaxLines(3);
        tt.setLineSpacing(10, 1f);
        tt.setGravity(Gravity.CENTER);
        f.addView(tt, new FrameLayout.LayoutParams(1440, -2));
        int ty = Math.max(120, 420 - items.length * 46 - (longTitle ? 60 : 0));
        at(tt, 240, ty);
        tt.measure(View.MeasureSpec.makeMeasureSpec(1440, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        int rowsY = ty + Math.max(120, tt.getMeasuredHeight() + 56);
        for (int i = 0; i < items.length; i++) {
            TextView r = text(items[i], 500, 36, Ui.IVORY);
            r.setGravity(Gravity.CENTER);
            f.addView(r, new FrameLayout.LayoutParams(720, 86));
            at(r, 600, rowsY + i * 98);
            overRows.add(r);
        }
        over.addView(f, new FrameLayout.LayoutParams(Ui.W, Ui.H));
        f.setAlpha(0f);
        f.animate().alpha(1f).setDuration(180).start();
        drawList();
    }

    private void drawList() {
        for (int i = 0; i < overRows.size(); i++) {
            TextView r = overRows.get(i);
            boolean on = i == osel;
            r.setBackground(Ui.round(on ? Ui.IVORY : 0x14FFFFFF, 43));
            r.setTextColor(on ? 0xFF0B0F17 : Ui.IVORY);
        }
    }

    private void closeOverlay() {
        overlay = NONE;
        over.removeAllViews();
    }

    private void listKey(int dy, boolean ok, boolean back) {
        if (back) {
            snd.play("back");
            closeOverlay();
            return;
        }
        if (dy != 0) {
            int was = osel;
            osel = Math.max(0, Math.min(overRows.size() - 1, osel + dy));
            snd.play(osel != was ? "focus" : "edge");
            drawList();
            return;
        }
        if (!ok) return;
        int kind = overlay;
        closeOverlay();
        if (kind == EDIT) {
            if (osel == 0) {
                overlay = MOVE;
                showMoveHint();
            } else if (osel == 1) {
                dock.remove(app);
                saveDock();
                showHome();
            }
        } else if (kind == PICK) {
            dock.add(pickList.get(osel));
            saveDock();
            showHome();
            app = dock.size() - 1;
            drawHome();
        } else if (kind == PERSON) {
            String pick = osel < overRows.size() ? overRows.get(osel).getText().toString() : "";
            if (pick.equals(t("Add a person", "إضافة شخص"))) showPhone();
            else if (pick.startsWith(t("Remove ", "إزالة "))) showList(CONFIRM, new String[] {t("Remove", "إزالة"), t("Cancel", "إلغاء")},
                    t("Remove ", "إزالة ") + name(who) + t("? Their picture, apps and settings will be deleted.", "؟ ستُحذف صورته وتطبيقاته وإعداداته."));
        } else if (kind == CONFIRM) {
            if (osel == 0 && Family.remove(this, who)) {
                avatars.clear();
                soft.clear();
                who = Math.max(0, who - 1);
                active = Math.min(active, IDS.length - 1);
                snd.play("back");
                showWho();
            }
        } else if (kind == INPUTS) {
            if (osel < pickList.size() && AIRPLAY.equals(pickList.get(osel))) launch(AIRPLAY);
            else if (osel < pickList.size()) {
                snd.play("open");
                startSafe(new Intent(Intent.ACTION_VIEW, android.media.tv.TvContract.buildChannelUriForPassthroughInput(pickList.get(osel))));
            }
        } else if (kind == NIGHT) {
            int[] mins = {30, 60, 90};
            if (osel < 3) {
                Helper.schedule(mins[osel]);
                snd.play("night");
            } else Helper.cancel();
        }
    }

    private void showMoveHint() {
        TextView hint = text(t("Left and right to move  ·  OK when done", "يمين ويسار للنقل  ·  موافق للإنهاء"), 500, 28, Ui.IVORY);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(30, 14, 30, 16);
        hint.setBackground(Ui.round(0x99000000, 40));
        over.addView(hint, new FrameLayout.LayoutParams(-2, -2));
        at(hint, (Ui.W - Ui.measureWidth(hint)) / 2f, 720);
        drawHome();
    }

    private void moveKey(int dx, boolean ok, boolean back) {
        if (dx != 0) {
            int to = app + dx;
            // Apps move among themselves; the three built-in tiles stay at the end.
            if (to >= 0 && to < dock.size() - 3) {
                String pkg = dock.remove(app);
                dock.add(to, pkg);
                saveDock();
                int keep = to;
                showHome();
                overlay = MOVE;
                app = keep;
                showMoveHint();
            }
            return;
        }
        if (ok || back) {
            closeOverlay();
            drawHome();
        }
    }

    private void showPick() {
        pickList.clear();
        List<String> labels = new ArrayList<>();
        List<ResolveInfo> found = new ArrayList<>(getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        found.addAll(getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        for (ResolveInfo ri : found) {
            String pkg = ri.activityInfo.packageName;
            if (dock.contains(pkg) || pkg.equals(getPackageName()) || pickList.contains(pkg)) continue;
            if ((ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0 && !pkg.startsWith("com.google.android.youtube")) continue;
            pickList.add(pkg);
            labels.add(label(pkg));
        }
        if (pickList.isEmpty()) {
            showList(NOTE, new String[] {t("OK", "حسناً")}, t("Every app is already on Home", "كل التطبيقات موجودة"));
            return;
        }
        showList(PICK, labels.subList(0, Math.min(7, labels.size())).toArray(new String[0]), t("Add an app", "أضف تطبيقاً"));
    }

    /** HDMI and the other sockets, with the names the family gave them in the TV's own settings. */
    private void showInputs() {
        pickList.clear();
        List<String> labels = new ArrayList<>();
        try {
            android.media.tv.TvInputManager tim = (android.media.tv.TvInputManager) getSystemService(TV_INPUT_SERVICE);
            for (android.media.tv.TvInputInfo info : tim.getTvInputList()) {
                if (!info.isPassthroughInput() || info.isHidden(this)) continue;
                CharSequence name = info.loadCustomLabel(this);
                if (name == null || name.length() == 0) name = info.loadLabel(this);
                pickList.add(info.getId());
                labels.add(name.toString());
            }
        } catch (Exception ignored) {
            // No TV inputs on this device.
        }
        // iPad and iPhone mirroring, once the free AirPlay receiver is installed on the TV.
        if (launchable(AIRPLAY)) {
            pickList.add(0, AIRPLAY);
            labels.add(0, t("iPad / iPhone screen (AirPlay)", "شاشة الآيباد / الآيفون (AirPlay)"));
        }
        if (pickList.isEmpty()) {
            showList(NOTE, new String[] {t("OK", "حسناً")}, t("No inputs found on this TV", "لا توجد مداخل في هذا التلفاز"));
            return;
        }
        showList(INPUTS, labels.subList(0, Math.min(7, labels.size())).toArray(new String[0]), t("Inputs", "المداخل"));
    }

    // ---------- today ----------

    private void showDay() {
        snd.play("open");
        screen = DAYS;
        dayPage = 0;
        backdrop(null, null, 0);
        drawDay(true);
        Weather.refresh(prefs, () -> h.post(() -> { if (screen == DAYS && overlay == NONE) drawDay(false); }));
    }

    private void drawDay(boolean fade) {
        FrameLayout f = day.build(dayPage);
        if (fade) swap(f);
        else {
            layer.removeAllViews();
            layer.addView(f, new FrameLayout.LayoutParams(Ui.W, Ui.H));
        }
    }

    private void dayKey(int dx, boolean back) {
        if (back) {
            snd.play("back");
            homeOn(DAY);
            return;
        }
        int to = Math.max(0, Math.min(DayScreen.PAGES - 1, dayPage + dx));
        if (to != dayPage) {
            dayPage = to;
            snd.play("page");
            drawDay(true);
        }
    }

    private void showPhone() {
        overlay = PHONE;
        over.removeAllViews();
        FrameLayout f = new FrameLayout(this);
        f.setBackgroundColor(0xE605080F);
        TextView tt = text(t("Change pictures, dates and notes from your phone", "غيّر الصور والتواريخ والرسائل من هاتفك"), 700, 48, Ui.IVORY);
        tt.setGravity(Gravity.CENTER);
        f.addView(tt, new FrameLayout.LayoutParams(Ui.W, -2));
        at(tt, 0, 260);
        day.phoneCard(f, 660, 420);
        TextView note = text(t("The phone must be on the same Wi-Fi as the TV", "يجب أن يكون الهاتف على نفس شبكة الواي فاي"), 400, 28, Ui.DIM);
        note.setGravity(Gravity.CENTER);
        f.addView(note, new FrameLayout.LayoutParams(Ui.W, -2));
        at(note, 0, 820);
        over.addView(f, new FrameLayout.LayoutParams(Ui.W, Ui.H));
        f.setAlpha(0f);
        f.animate().alpha(1f).setDuration(200).start();
    }

    private void showNight() {
        long end = Helper.endsAt();
        String title = !Helper.running() ? t("Good night needs its helper turned on", "تصبح على خير يحتاج تفعيل المساعد")
                : end > 0 ? t("TV turns off at ", "يُطفأ التلفاز الساعة ") + new SimpleDateFormat("h:mm", Locale.US).format(new Date(end)) + " " + (new Date(end).getHours() < 12 ? t("AM", "ص") : t("PM", "م"))
                : t("Good night", "تصبح على خير");
        if (!Helper.running()) {
            showList(NOTE, new String[] {t("OK", "حسناً")}, title);
            return;
        }
        showList(NIGHT, new String[] {t("In 30 minutes", "بعد 30 دقيقة"), t("In 60 minutes", "بعد 60 دقيقة"),
                t("In 90 minutes", "بعد 90 دقيقة"), t("Turn off the timer", "إيقاف المؤقت")}, title);
    }

    // ---------- settings ----------

    private void openSettings(String which) {
        snd.play("menu");
        menu = which;
        screen = SETTINGS;
        set = 0;
        closeOverlay();
        drawSettings();
    }

    private String[][] settingsRows() {
        if ("family".equals(menu)) {
            return new String[][] {
                {t("Language for ", "لغة ") + name(active), ar() ? "العربية" : "English"},
                {t("Mood", "الأجواء"), ar() ? MOOD_AR[prefs.getInt("mood", 0)] : MOOD_EN[prefs.getInt("mood", 0)]},
                {t("Picture for ", "صورة ") + name(active), picName(pic(active))},
                {t("Sound", "الصوت"), "›"},
                {t("Phone page", "صفحة الهاتف"), "›"},
                {t("Birthdays, Ramadan and Eid colours", "ألوان الأعياد ورمضان وأعياد الميلاد"), prefs.getBoolean("specialDays", true) ? t("On", "تشغيل") : t("Off", "إيقاف")},
                {t("Prayer reminders", "تنبيهات الصلاة"), !prefs.getBoolean("prayerNotes", true) ? t("Off", "إيقاف")
                        : Helper.running() ? t("On", "تشغيل") : t("Needs the helper", "يحتاج المساعد")},
                {t("Home button opens ", "زر الرئيسية يفتح ") + t(Family.BRAND_EN, Family.BRAND_AR), !prefs.getBoolean("homeRedirect", false) ? t("Off", "إيقاف")
                        : Helper.running() ? t("On", "تشغيل") : t("Needs the helper", "يحتاج المساعد")},
                {t("Return to the original TV", "العودة إلى التلفاز الأصلي"), ""},
            };
        }
        int vol = prefs.getInt("vol", 7);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 10; i++) bar.append(i < vol ? "▮" : "▯");
        String on = t("On", "تشغيل"), off = t("Off", "إيقاف");
        return new String[][] {
            {t("Sound style", "نمط الأصوات"), "‹  " + (ar() ? Sounds.STYLE_AR : Sounds.STYLE_EN)[Sounds.styleIndex(prefs)] + "  ›"},
            {t("Sound effects", "المؤثرات الصوتية"), prefs.getBoolean("snd", true) ? on : off},
            {t("Volume", "مستوى الصوت"), bar.toString()},
            {t("Quiet hours", "ساعات الهدوء"), prefs.getBoolean("quiet", true) ? on : off},
            {t("Quiet from", "الهدوء من"), Prayer.hour12(prefs.getInt("qs", 22), ar())},
            {t("Quiet until", "الهدوء حتى"), Prayer.hour12(prefs.getInt("qe", 7), ar())},
            {t("Hear the opening", "استمع إلى لحن البدء"), "▶"},
            {t("Hear choosing a person", "استمع إلى صوت الاختيار"), "▶"},
            {t("Hear the birthday melody", "استمع إلى لحن عيد الميلاد"), "▶"},
            {t("Hear Good night", "استمع إلى تصبح على خير"), "▶"},
        };
    }

    private String picName(String p) {
        return "letter".equals(p) ? t("First letter", "الحرف الأول") : "photo".equals(p) ? t("Photo from phone", "صورة من الهاتف") : t("Avatar", "الشخصية");
    }

    /** Avatar, letter, then photo when one has been sent from a phone. */
    private void nextPic(int step) {
        List<String> modes = new ArrayList<>(Arrays.asList(PICS));
        if (!photoFile(active).exists()) modes.remove("photo");
        int i = modes.indexOf(pic(active));
        prefs.edit().putString("pic_" + id(active), modes.get((i + (step == 0 ? 1 : step) + modes.size()) % modes.size())).apply();
    }

    private void drawSettings() {
        FrameLayout f = new FrameLayout(this);
        View dim = new View(this);
        dim.setBackgroundColor(0xB305080F);
        f.addView(dim, new FrameLayout.LayoutParams(Ui.W, Ui.H));
        TextView title = text("family".equals(menu) ? t("Family settings", "إعدادات العائلة") : t("Sound", "الصوت"), 700, 72, Ui.IVORY);
        put(f, title, 160, 96, -2, -2);
        rows.clear();
        String[][] r = settingsRows();
        for (int i = 0; i < r.length; i++) {
            FrameLayout line = new FrameLayout(this);
            line.setPadding(34, 0, 34, 0);
            TextView a = text(r[i][0], 500, 34, Ui.IVORY);
            TextView b = text(r[i][1], 400, 30, Ui.DIM);
            a.setGravity(Gravity.CENTER_VERTICAL | (ar() ? Gravity.RIGHT : Gravity.LEFT));
            b.setGravity(Gravity.CENTER_VERTICAL | (ar() ? Gravity.LEFT : Gravity.RIGHT));
            line.addView(a, new FrameLayout.LayoutParams(-1, -1));
            line.addView(b, new FrameLayout.LayoutParams(-1, -1));
            put(f, line, 160, 230 + i * 84, 1600, 76);
            rows.add(new TextView[] {a, b});
        }
        layer.removeAllViews();
        layer.addView(f, new FrameLayout.LayoutParams(Ui.W, Ui.H));
        for (int i = 0; i < rows.size(); i++) {
            boolean on = i == set;
            View line = (View) rows.get(i)[0].getParent();
            line.setBackground(Ui.round(on ? Ui.IVORY : 0x0FFFFFFF, 18));
            rows.get(i)[0].setTextColor(on ? 0xFF0B0F17 : Ui.IVORY);
            rows.get(i)[1].setTextColor(on ? 0xB30B0F17 : Ui.DIM);
        }
    }

    private void settingsKey(int dx, int dy, boolean ok, boolean back) {
        int n = settingsRows().length;
        if (back) {
            snd.play("back");
            if ("sound".equals(menu) && prefs.getBoolean("fromFamily", false)) {
                prefs.edit().putBoolean("fromFamily", false).apply();
                menu = "family";
                set = 3;
                drawSettings();
            } else showHome();
            return;
        }
        if (dy != 0) {
            set = (set + dy + n) % n;
            snd.play("focus");
        } else if ((ok || dx != 0) && !("sound".equals(menu) && set == 0)) snd.play("toggle");
        int step = ar() ? -dx : dx;
        SharedPreferences.Editor e = prefs.edit();
        if ("family".equals(menu)) {
            if (set == 0 && (ok || dx != 0)) {
                e.putString("lang_" + id(active), ar() ? "en" : "ar").apply();
            } else if (set == 1 && (ok || dx != 0)) {
                e.putInt("mood", (prefs.getInt("mood", 0) + (step == 0 ? 1 : step) + moodCount()) % moodCount()).apply();
                applyMood();
            } else if (set == 2 && (ok || dx != 0)) {
                nextPic(step);
            } else if (set == 3 && ok) {
                e.putBoolean("fromFamily", true).apply();
                menu = "sound";
                set = 0;
            } else if (set == 4 && ok) {
                showPhone();
                return;
            } else if (set == 5 && (ok || dx != 0)) {
                e.putBoolean("specialDays", !prefs.getBoolean("specialDays", true)).apply();
                applyMood();
            } else if (set == 6 && (ok || dx != 0)) {
                // Adhan and after-prayer notices; switching them on shows one straight away.
                boolean on = !prefs.getBoolean("prayerNotes", true);
                e.putBoolean("prayerNotes", on).apply();
                if (on) {
                    prefs.edit().putBoolean("helperOff", false).apply();
                    repairHelper();
                    Helper.preview();
                }
            } else if (set == 7 && (ok || dx != 0)) {
                boolean on = !prefs.getBoolean("homeRedirect", false);
                e.putBoolean("homeRedirect", on).apply();
                if (on) prefs.edit().putBoolean("helperOff", false).apply();
                if (on && !repairHelper() && !Helper.running()) {
                    showList(NOTE, new String[] {t("Open Accessibility", "فتح إمكانية الوصول"), t("Close", "إغلاق")},
                            t("Turn on \"" + Family.BRAND_EN + " helper\" in Accessibility, then come back.",
                              "فعّل \"" + Family.BRAND_EN + " helper\" في إمكانية الوصول ثم عد."));
                    overlay = NOTE;
                    prefs.edit().putBoolean("accessMenu", true).apply();
                    return;
                }
            } else if (set == 8 && ok) {
                // The way back: the Home button belongs to Google TV again straight away, and the helper is switched off.
                e.putBoolean("homeRedirect", false).apply();
                helperOff();
                showList(NOTE, new String[] {t("Open app settings", "فتح إعدادات التطبيق"), t("Close", "إغلاق")},
                        t("Done: the Home button now opens your normal TV. To remove " + Family.BRAND_EN + " completely, uninstall it in the app settings.",
                          "تم: زر الرئيسية يفتح تلفازك العادي الآن. لإزالة " + Family.BRAND_AR + " تماماً، ألغِ تثبيته من إعدادات التطبيق."));
                overlay = NOTE;
                prefs.edit().putBoolean("returnMenu", true).apply();
                return;
            }
        } else {
            // Row 0 is the style: left and right change it and play its opening, so the family hears it at once.
            if (set == 0 && (ok || dx != 0)) {
                int styles = Sounds.STYLES.length;
                snd.setStyle(Sounds.STYLES[(Sounds.styleIndex(prefs) + (step == 0 ? 1 : step) + styles) % styles]);
                snd.play("sig", true);
            }
            int row = set - 1;
            if (row == 0 && (ok || dx != 0)) e.putBoolean("snd", !prefs.getBoolean("snd", true)).apply();
            if (row == 1 && dx != 0) {
                e.putInt("vol", Math.max(0, Math.min(10, prefs.getInt("vol", 7) + step))).apply();
                snd.play("focus", true);
            }
            if (row == 2 && (ok || dx != 0)) e.putBoolean("quiet", !prefs.getBoolean("quiet", true)).apply();
            if (row == 3 && dx != 0) e.putInt("qs", (prefs.getInt("qs", 22) + step + 24) % 24).apply();
            if (row == 4 && dx != 0) e.putInt("qe", (prefs.getInt("qe", 7) + step + 24) % 24).apply();
            if (ok && row >= 5) snd.play(new String[] {"sig", "pick", "bday", "night"}[row - 5], true);
        }
        drawSettings();
    }

    // ---------- idle clock ----------

    private MemoriesScreen memClock;

    private void showClock() {
        int before = screen;
        screen = CLOCK;
        if (!Memories.all(this, prefs).isEmpty()) {
            // Memory clock: the family's photos play behind the time while nobody uses the TV.
            memClock = new MemoriesScreen(this, true);
            FrameLayout m = memClock.view();
            memClock.tickClock();
            over.removeAllViews();
            over.addView(m, new FrameLayout.LayoutParams(Ui.W, Ui.H));
            m.setAlpha(0f);
            m.animate().alpha(1f).setDuration(1500).start();
            layer.animate().alpha(0f).setDuration(1500).start();
            m.setTag(before);
            memClock.start();
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (screen != CLOCK || memClock == null) return;
                    memClock.tickClock();
                    h.postDelayed(this, 20_000);
                }
            }, 20_000);
            return;
        }
        FrameLayout f = new FrameLayout(this);
        f.setBackgroundColor(0x66000000);
        View l = logo(30, 34);
        f.addView(l, new FrameLayout.LayoutParams(-2, -2));
        at(l, (Ui.W - Ui.measureWidth(l)) / 2f, 250);
        l.setAlpha(0.85f);
        TextView big = Ui.text(this, time(), Ui.font(this, "Montserrat.ttf", 200), 230, Ui.IVORY);
        big.setGravity(Gravity.CENTER);
        f.addView(big, new FrameLayout.LayoutParams(Ui.W, -2));
        at(big, 0, 330);
        TextView d = text(clockLine(), 400, 40, Ui.DIM);
        d.setGravity(Gravity.CENTER);
        f.addView(d, new FrameLayout.LayoutParams(Ui.W, -2));
        at(d, 0, 640);
        over.removeAllViews();
        over.addView(f, new FrameLayout.LayoutParams(Ui.W, Ui.H));
        f.setAlpha(0f);
        f.animate().alpha(1f).setDuration(1200).start();
        layer.animate().alpha(0f).setDuration(1200).start();
        f.setTag(before);
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (screen != CLOCK) return;
                big.setText(time());
                // AM/PM and the date change too (at midnight and noon), so refresh them with the time.
                d.setText(clockLine());
                h.postDelayed(this, 20_000);
            }
        }, 20_000);
    }

    /** The line under the idle clock: "AM  ·  Saturday 7 November". */
    String clockLine() {
        return ampm() + "  ·  " + new SimpleDateFormat("EEEE d MMMM", ar() ? new Locale("ar") : Locale.UK).format(new Date());
    }

    private void hideClock() {
        View f = over.getChildAt(0);
        if (memClock != null) {
            memClock.stop();
            memClock = null;
        }
        screen = f != null && f.getTag() instanceof Integer ? (Integer) f.getTag() : WHO;
        over.removeAllViews();
        layer.animate().alpha(1f).setDuration(300).start();
    }

    // ---------- keys ----------

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean center = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
        boolean handled = center || code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_DPAD_LEFT
                || code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
        if (!handled) return super.dispatchKeyEvent(e);
        if (e.getAction() == KeyEvent.ACTION_DOWN) poke();
        if (screen == CLOCK) {
            if (e.getAction() == KeyEvent.ACTION_UP) hideClock();
            return true;
        }
        if (center) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.isLongPress() && !longFired) {
                // Android's own long-press signal, as soon as it comes.
                h.removeCallbacks(longPress);
                longPress.run();
            } else if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                longFired = false;
                h.postDelayed(longPress, LONG_MS);
            } else if (e.getAction() == KeyEvent.ACTION_UP) {
                h.removeCallbacks(longPress);
                if (!longFired) ok();
            }
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        boolean back = code == KeyEvent.KEYCODE_BACK;
        int dx = code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 0;
        if (ar()) dx = -dx;
        int dy = code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 0;
        route(dx, dy, false, back);
        return true;
    }

    private void ok() {
        route(0, 0, true, false);
    }

    private void route(int dx, int dy, boolean ok, boolean back) {
        if (overlay == MOVE) {
            moveKey(dx, ok, back);
            return;
        }
        if (overlay == CELEBRATE) {
            if (ok || back) closeCelebrate();
            return;
        }
        if (overlay == PHONE) {
            if (ok || back) {
                closeOverlay();
                if (screen == SETTINGS) drawSettings();
            }
            return;
        }
        if (overlay == NOTE) {
            if (ok && osel == 0 && prefs.getBoolean("returnMenu", false)) {
                startSafe(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
            }
            if (ok && osel == 0 && prefs.getBoolean("accessMenu", false)) startSafe(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            if (ok || back) {
                prefs.edit().putBoolean("returnMenu", false).putBoolean("accessMenu", false).apply();
                closeOverlay();
                if (screen == SETTINGS) drawSettings();
            } else if (dy != 0) listKey(dy, false, false);
            return;
        }
        if (overlay != NONE) {
            listKey(dy, ok, back);
            return;
        }
        if (screen == INTRO) {
            if (ok || back) showWho();
        } else if (screen == WHO) whoKey(dx, dy, ok, back);
        else if (screen == HOME) homeKey(dx, dy, ok, back);
        else if (screen == SETTINGS) settingsKey(dx, dy, ok, back);
        else if (screen == DAYS) dayKey(dx, back);
        else if (screen == MEMS) memKey(dx, ok, back);
        else if (screen == ADHK) adhKey(dx, dy, ok, back);
    }
}
