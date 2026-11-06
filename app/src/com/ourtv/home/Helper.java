package com.ourtv.home;

import android.accessibilityservice.AccessibilityService;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.os.UserManager;
import android.content.SharedPreferences;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * Good night: dims the whole screen over the last minute, even above Netflix, then puts the TV to
 * sleep. Only an accessibility service may draw over other apps and turn the screen off without
 * root. It also brings Our TV forward when Google TV's home screen appears, if the family turned
 * that on. It only looks at which app's window opened, reads nothing on screen and handles no keys.
 */
public class Helper extends AccessibilityService {
    static Helper instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private View dim;
    private long endAt;

    static boolean running() {
        return instance != null;
    }

    static long endsAt() {
        return instance == null ? 0 : instance.endAt;
    }

    static void schedule(int minutes) {
        if (instance == null) return;
        instance.cancelTimer();
        instance.endAt = System.currentTimeMillis() + minutes * 60_000L;
        instance.handler.postDelayed(instance::startDim, Math.max(0, minutes * 60_000L - 60_000L));
    }

    static void cancel() {
        if (instance != null) instance.cancelTimer();
    }

    private void cancelTimer() {
        handler.removeCallbacksAndMessages(null);
        endAt = 0;
        removeDim();
    }

    private void startDim() {
        Sounds.get(this).play("night");
        dim = new View(this);
        dim.setBackgroundColor(Color.BLACK);
        dim.setAlpha(0f);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(dim, lp);
        ValueAnimator a = ValueAnimator.ofFloat(0f, 0.7f);
        a.setDuration(58_000);
        a.addUpdateListener(x -> {
            if (dim != null) dim.setAlpha((float) x.getAnimatedValue());
        });
        a.start();
        handler.postDelayed(this::sleepNow, 60_000);
    }

    private void sleepNow() {
        endAt = 0;
        removeDim();
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    private void removeDim() {
        if (dim == null) return;
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(dim);
        } catch (Exception ignored) {
            // Already gone.
        }
        dim = null;
    }

    // ---------- prayer notices ----------

    /** At each adhan, and again when the after-prayer adhkar are due: a small notice over whatever is on screen. */
    private final Runnable prayerTick = this::prayerEvent;
    /** Its own clock, so cancelling a Good night timer never cancels the prayer times. */
    private final Handler prayers = new Handler(Looper.getMainLooper());
    private View notice;
    private final Runnable hideNotice = this::removeNotice;

    private SharedPreferences prefsOrNull() {
        UserManager users = (UserManager) getSystemService(USER_SERVICE);
        if (users == null || !users.isUserUnlocked()) return null;
        return getSharedPreferences(Family.store(this), MODE_PRIVATE);
    }

    /** Arms the next adhan or after-prayer moment (Fajr, Dhuhr, Asr, Maghrib, Isha; sunrise is not a prayer). */
    void schedulePrayer() {
        prayers.removeCallbacks(prayerTick);
        SharedPreferences prefs = prefsOrNull();
        if (prefs == null) return;
        long now = System.currentTimeMillis(), next = Long.MAX_VALUE;
        for (int day = 0; day < 2 && next == Long.MAX_VALUE; day++) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_MONTH, day);
            double[] t = Prayer.times(c, prefs);
            for (int i : new int[] {0, 2, 3, 4, 5}) {
                for (double at : new double[] {t[i], t[i] + Adhkar.AFTER_MIN / 60.0}) {
                    long ms = midnight(c) + Math.round(at * 3_600_000);
                    if (ms > now + 1000 && ms < next) next = ms;
                }
            }
        }
        if (next != Long.MAX_VALUE) prayers.postDelayed(prayerTick, next - now);
    }

    private static long midnight(Calendar day) {
        Calendar c = (Calendar) day.clone();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private void prayerEvent() {
        SharedPreferences prefs = prefsOrNull();
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        if (prefs != null && prefs.getBoolean("prayerNotes", true) && pm != null && pm.isInteractive()) {
            Calendar now = Calendar.getInstance();
            double h = now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60.0;
            double[] t = Prayer.times(now, prefs);
            int which = -1;
            boolean after = false;
            for (int i : new int[] {0, 2, 3, 4, 5}) {
                if (Math.abs(h - t[i]) < 3 / 60.0) which = i;
                if (Math.abs(h - (t[i] + Adhkar.AFTER_MIN / 60.0)) < 3 / 60.0) {
                    which = i;
                    after = true;
                }
            }
            if (which >= 0) showNotice(which, after, Prayer.hmA(t[which], true));
        }
        schedulePrayer();
    }

    /** A sample right away, so the family sees what the reminders look like when they switch them on. */
    static void preview() {
        if (instance == null) return;
        SharedPreferences prefs = instance.prefsOrNull();
        if (prefs == null) return;
        double[] n = Prayer.next(prefs);
        instance.showNotice((int) n[0], false, Prayer.hmA(n[2], true));
    }

    private void showNotice(int prayer, boolean after, String time) {
        removeNotice();
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setGravity(android.view.Gravity.CENTER);
        box.setPadding(56, 26, 56, 30);
        box.setBackground(Ui.round(0xE60B1220, 48));
        // The time is isolated as left-to-right, or Arabic text flow splits "5:57" around the colon.
        String title = after ? "أذكار بعد الصلاة" : "أذان " + Prayer.AR[prayer] + "  ·  ⁦" + time + "⁩";
        String sub = after ? "تقبل الله صلاتكم  ·  الأذكار في تطبيق أذكار" : "حان الآن وقت صلاة " + Prayer.AR[prayer];
        android.widget.TextView a = Ui.text(this, after ? title : "أذان " + Prayer.AR[prayer], Ui.font(this, "Alexandria.ttf", 600), 44, 0xFFFFFFFF);
        android.widget.TextView b = Ui.text(this, sub, Ui.font(this, "Alexandria.ttf", 400), 28, 0xCCFFFFFF);
        a.setGravity(android.view.Gravity.CENTER);
        b.setGravity(android.view.Gravity.CENTER);
        a.setTextDirection(View.TEXT_DIRECTION_RTL);
        b.setTextDirection(View.TEXT_DIRECTION_RTL);
        if (after) box.addView(a);
        else {
            // Name and time side by side; the time in its own view so it is never measured short.
            android.widget.LinearLayout line = new android.widget.LinearLayout(this);
            line.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            line.setGravity(android.view.Gravity.CENTER_VERTICAL);
            line.addView(a);
            android.widget.TextView tm = Ui.text(this, time, Ui.font(this, "Montserrat.ttf", 400), 40, 0xFFFFFFFF);
            tm.setTextDirection(View.TEXT_DIRECTION_LTR);
            android.widget.LinearLayout.LayoutParams tp = new android.widget.LinearLayout.LayoutParams(-2, -2);
            tp.setMarginStart(26);
            line.addView(tm, tp);
            box.addView(line);
        }
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = 8;
        box.addView(b, lp);
        WindowManager.LayoutParams wl = new WindowManager.LayoutParams(-2, -2,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        wl.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
        wl.y = 48;
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(box, wl);
        } catch (Exception e) {
            return;
        }
        notice = box;
        box.setAlpha(0f);
        box.setTranslationY(-30);
        box.animate().alpha(1f).translationY(0).setDuration(600).start();
        Sounds.get(this).play("note");
        prayers.postDelayed(hideNotice, after ? 15_000 : 25_000);
    }

    private void removeNotice() {
        prayers.removeCallbacks(hideNotice);
        if (notice == null) return;
        View v = notice;
        notice = null;
        v.animate().alpha(0f).setDuration(500).withEndAction(() -> {
            try {
                ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(v);
            } catch (Exception ignored) {
                // Already gone.
            }
        }).start();
    }

    // ---------- Home button ----------

    /**
     * The TV's own home screen (Google TV's launcherx on this TV), found from Android rather than assumed.
     * It stays installed and enabled; Our TV only steps in front of it.
     */
    private String stock = "com.google.android.apps.tv.launcherx";
    private final Set<String> stockHome = new HashSet<>();

    /** On only when the family switched it on in Our TV's settings, and never before the TV has finished starting. */
    private boolean redirectOn() {
        UserManager users = (UserManager) getSystemService(USER_SERVICE);
        if (users == null || !users.isUserUnlocked()) return false;
        return getSharedPreferences(Family.store(this), MODE_PRIVATE).getBoolean("homeRedirect", false);
    }

    /**
     * Several quick Home presses become one opening, but the last press always wins: a time-based skip
     * left the TV on Google TV's home when Home was pressed twice.
     */
    private final Runnable open = this::openNow;

    private void openOurTv() {
        handler.removeCallbacks(open);
        handler.post(open);
    }

    private void openNow() {
        // Started like an app, not as Android's Home, so the remote's Netflix and YouTube buttons keep working.
        try {
            startActivity(new Intent(this, MainActivity.class).putExtra("fromHome", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        } catch (Exception ignored) {
            // If it cannot open, the normal Google TV home simply stays.
        }
    }

    /** The app whose window came up last, so waking the screen on Google TV's home can bring Our TV instead. */
    private String lastPkg = "";
    private boolean afterChooser;
    private android.content.BroadcastReceiver screenOn, unlocked;

    /** At power-on Google TV's home is already showing before this helper starts, so no window change would come. */
    private void openAtStart() {
        MainActivity.wakeAirPlay(this);
        if (SystemClock.elapsedRealtime() < 180_000 && redirectOn()) handler.postDelayed(this::openOurTv, 1500);
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
        Family.load(this);
        UserManager users = (UserManager) getSystemService(USER_SERVICE);
        if (users != null && users.isUserUnlocked()) {
            openAtStart();
            schedulePrayer();
        }
        else {
            unlocked = new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(android.content.Context c, Intent i) {
                    openAtStart();
                    schedulePrayer();
                }
            };
            registerReceiver(unlocked, new android.content.IntentFilter(Intent.ACTION_USER_UNLOCKED));
        }
        screenOn = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context c, Intent i) {
                // Waking up: TCL puts Google TV's home up first. Opening Our TV the moment the screen comes on,
                // rather than after that home has drawn, keeps the old screen from showing.
                if (redirectOn()) openNow();
                // Timers can drift while the TV sleeps; re-arm the next prayer from the real clock.
                schedulePrayer();
            }
        };
        registerReceiver(screenOn, new android.content.IntentFilter(Intent.ACTION_SCREEN_ON));
    }

    /**
     * Finds the TV's own home. Only after the TV has fully started: before that, Android answers with its
     * temporary start-up screen (Settings' FallbackHome), and the real home would never be recognised.
     */
    private boolean findStock() {
        if (!stockHome.isEmpty()) return true;
        UserManager users = (UserManager) getSystemService(USER_SERVICE);
        if (users == null || !users.isUserUnlocked()) return false;
        int all = PackageManager.MATCH_DIRECT_BOOT_AWARE | PackageManager.MATCH_DIRECT_BOOT_UNAWARE;
        ResolveInfo current = getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY | all);
        String p = current == null ? "" : current.activityInfo.packageName;
        if (!p.isEmpty() && !"android".equals(p) && !getPackageName().equals(p) && !p.endsWith(".settings")) stock = p;
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(stock);
        for (ResolveInfo ri : getPackageManager().queryIntentActivities(home, PackageManager.MATCH_DISABLED_COMPONENTS | all)) {
            stockHome.add(ri.activityInfo.name);
        }
        android.util.Log.i("OurTV", "home screen is " + stock + " " + stockHome);
        return !stockHome.isEmpty();
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        for (android.content.BroadcastReceiver r : new android.content.BroadcastReceiver[] {screenOn, unlocked}) {
            if (r == null) continue;
            try {
                unregisterReceiver(r);
            } catch (RuntimeException ignored) {
                // Already unregistered.
            }
        }
        screenOn = unlocked = null;
        cancelTimer();
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName(), cls = event.getClassName();
        if (pkg != null) lastPkg = pkg.toString();
        if (!findStock() || pkg == null || cls == null || !stock.contentEquals(pkg)) return;
        String c = cls.toString();
        // Google TV's "Who's watching?" chooser (and its account check) sits on top of its home at power-on.
        // When it closes, the home underneath only reports a plain layout window, not its own name.
        if (c.contains(".profile.")) {
            afterChooser = true;
            return;
        }
        boolean home = stockHome.contains(c) || afterChooser;
        afterChooser = false;
        if (home && redirectOn()) openOurTv();
    }

    @Override
    public void onInterrupt() {
    }
}
