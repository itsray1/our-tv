# Our TV · تلفازنا

A calm, Apple TV–style home for a family's Google TV, in English and Arabic.
It runs as an ordinary Android TV app next to Google TV, which stays installed and untouched,
so the normal TV is always one setting away.

Built for a TCL Google TV (Android 11, 32-bit Realtek chip) and drawn with native Android views,
so it stays smooth on modest hardware. No Gradle, no third-party UI libraries.

## What it does

**Profiles**
- One to eight people, added, renamed and removed from the phone page (or removed by holding OK on the TV).
- Each has an avatar, the first letter of their name, or a photo sent from a phone.
- Each person chooses English or Arabic (full right-to-left layout).
- Opens on whoever used the TV last.

**Home**
- A row of large app tiles; the area above changes with the focused app. The row slides when it is longer than the screen.
- Continue Watching from apps that share it (Netflix, YouTube, Shahid…), shown only to the person who watched it.
- Spotify's current song, artist and album art.
- Each person can move, hide and add apps.
- Moods for the background: Aurora, Sunset, Graphite, Auto (day and night) and Photo (your own picture).
- Optional: the remote's Home button opens Our TV instead of Google TV.

**Built-in apps** (at the end of the row)
- **Today**: next prayer on the sun's path across the day, weather and a five-day forecast, countdowns to birthdays, Ramadan and Eid, notes from phones.
- **Memories**: the family's photos as a slow, cinematic slideshow, with "On this day" first. When the TV sits idle, the photos play behind the clock.
- **Adhkar**: morning, evening, after-prayer and before-sleep adhkar in large Arabic type, with a counter. It opens on the right set for the time of day.
- **iPad & iPhone**: screen mirroring through a free AirPlay receiver (installed separately, see below).

**Around the day**
- Adhan notices at each prayer time, and an after-prayer adhkar reminder, shown over any app.
- Birthday, Eid and Ramadan screens, and special colours on those days.
- Good night: dims the screen and turns the TV off after 30, 60 or 90 minutes.
- Idle clock, 12-hour time with AM/PM, quiet hours that make sounds soft.
- Four sound styles (Glass, Piano, Marimba, Minimal), each with sounds for every move.

**Phone page**
Scan the QR code on the TV, enter the PIN, and from any phone on the home Wi-Fi you can manage people
and pictures, add birthdays and occasions, send notes, upload memories and a background photo,
adjust prayer times and the Hijri date, and preview the celebrations on the TV.
Nothing leaves the home network.

## Make it yours

The public code has no family in it. Everything personal goes in `app/private/`, which git ignores
and the build lays over the public files:

| File | What it holds |
|---|---|
| `app/private/assets/family.json` | The first list of people, the home city for prayer times and weather, and optionally your own name on screen. Start from `app/assets/family.example.json`. |
| `app/private/assets/avatars/avatar-<id>.png` | Optional avatar pictures, 512×512. Without them each person gets their initial. |
| `app/private/res/drawable/banner.png` | Optional launcher banner, 320×180. |
| `app/private/res/values/strings.xml` | Optional app name in the TV's app list. |

`family.json` can also hold `app_id` and `store`, so that a TV which already has the app installed
under another id updates in place and keeps its settings.

Photos, memories, notes and dates added from phones are stored only on the TV.

## Project structure

```
app/
  AndroidManifest.xml
  src/com/ourtv/home/
    MainActivity.java   Opening, profiles, Home, settings, idle clock, key handling
    Family.java         The people (add, rename, remove), home city and name on screen
    Ui.java             Design system: fonts, moods, shapes
    GlowView.java       The slowly drifting background glow
    DayScreen.java      Today: prayer, weather, countdowns, notes
    SunPath.java        The day drawn as the sun's path with the prayer times
    Prayer.java         Prayer times calculated on the TV, Hijri dates
    Weather.java        Open-Meteo forecast (free, no key), cached
    Store.java          Dates, notes and countdowns
    Memories.java       The photo library on the TV
    MemoriesScreen.java The slideshow and the memory clock
    Adhkar.java         Adhkar texts and which set is due now
    AdhkarScreen.java   One dhikr at a time with a counter
    Celebrate.java      Birthday, Eid and Ramadan screens
    Season.java         Special days and their colours
    Bokeh.java          Soft drifting lights for celebrations
    Watch.java          Continue Watching and Spotify's current song
    Sounds.java         Sound styles and quiet hours
    Helper.java         Accessibility helper: Home button, Good night, prayer notices
    Listener.java       Lets the app read what is playing (never reads notifications)
    PhoneServer.java    The phone page on the home network, behind a PIN
    Qr.java             QR code for the phone page
    IconView.java, HeroGlow.java  Drawn icons and the tile glow
  res/                  Banner, strings, accessibility description
  assets/
    phone.html          The phone page
    sync.html           A flash-and-beep page for checking mirroring sound sync
    family.example.json Template for app/private/assets/family.json
    fonts/              Sora, Montserrat, Alexandria (SIL Open Font License)
    sfx/<style>/        The four sound styles
  libs/                 ZXing core (Apache 2.0), for the QR code
tools/
  build.ps1             Builds and signs the APK without Gradle
  make_sound_styles.py  Generates every sound in all four styles
  make_sounds.py        The original sound set
```

## Building

Requirements on Windows:
- Android SDK with build-tools 35.0.0 and platform android-36 (from Android Studio)
- The JDK bundled with Android Studio
- Python 3 (used to add the compiled code to the APK)

Create a signing key once:

```powershell
keytool -genkeypair -keystore ourtv.keystore -alias ourtv -keyalg RSA -keysize 2048 -validity 10000
```

Then build:

```powershell
powershell -ExecutionPolicy Bypass -File tools\build.ps1 -work build -ks ourtv.keystore -ksPass <your password>
```

The APK is written to `build\ourtv.apk`.

To regenerate the sounds: `python tools\make_sound_styles.py out` and copy each style folder into `app/assets/sfx/`.

## Installing on the TV

Turn on developer options and network debugging on the TV, then:

```powershell
adb connect <tv-address>:5555
adb install -r build\ourtv.apk
```

Optional, each with its own permission (replace `com.ourtv.home` with your `app_id` if you set one):

| Feature | How to allow it |
|---|---|
| Home button, Good night, prayer notices | Turn on **Our TV helper** in Settings › Accessibility |
| The helper switches itself back on if Android drops it | `adb shell pm grant com.ourtv.home android.permission.WRITE_SECURE_SETTINGS` |
| TCL TVs keep the helper running | `adb shell appops set com.ourtv.home APP_AUTO_START allow` |
| Continue Watching | Allow the TV listings permission when asked |
| Spotify's current song | `adb shell cmd notification allow_listener com.ourtv.home/com.ourtv.home.Listener` |
| iPad and iPhone mirroring | Install [AirPlay Server](https://github.com/jqssun/android-airplay-server) (GPL-3.0, from F-Droid) |
| The mirror opens by itself when a device connects | `adb shell appops set io.github.jqssun.airplay SYSTEM_ALERT_WINDOW allow` |
| TCL TVs keep the AirPlay receiver ready | `adb shell appops set io.github.jqssun.airplay APP_AUTO_START allow` and `... APP_ASSOC_START allow`, then `adb shell appops write-settings`. Our TV then wakes it whenever it opens. |

Do not force-stop the app on Android 11: that also switches its accessibility helper off.

## Going back to the normal TV

At any time:
- In Our TV, hold OK on the gear for Family settings and choose **Return to the original TV**, or
- Uninstall the app.

Google TV is never disabled or changed, so it is exactly as it was.

## Credits

- Fonts: [Sora](https://fonts.google.com/specimen/Sora), [Montserrat](https://fonts.google.com/specimen/Montserrat), [Alexandria](https://fonts.google.com/specimen/Alexandria), SIL Open Font License 1.1
- [ZXing](https://github.com/zxing/zxing), Apache License 2.0
- Weather from [Open-Meteo](https://open-meteo.com)
- Home-button redirect approach learned from [CleanYourTV](https://github.com/Gathin23/cleanyourtv)
- Adhkar from Hisn al-Muslim
