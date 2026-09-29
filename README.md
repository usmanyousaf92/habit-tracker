# Habit Tracker — Android

An Android wrapper around the single-file Habit Tracker SPA. The entire web app
is one self-contained HTML file at
`app/src/main/assets/public/index.html` — open it in any desktop browser and it
runs exactly as it does on the phone.

You do **not** need Android Studio, the Android SDK, or Java on your machine.
GitHub's runners already have all of it; push this repo and they hand you back
an installable APK.

---

## Get an APK in about five minutes

1. **Create an empty GitHub repo** (private is fine).

2. **Push this folder into it:**

   ```bash
   cd habit-tracker-android
   git init -b main
   git add .
   git commit -m "Habit Tracker: Android WebView host"
   git remote add origin git@github.com:<you>/<repo>.git
   git push -u origin main
   ```

3. **Watch it build.** Open the repo's **Actions** tab. The *Build APK* workflow
   starts on push (roughly 3–5 minutes for a cold run, under 2 once Gradle's
   cache warms up).

4. **Download it.** When the run goes green, scroll to **Artifacts** at the
   bottom of the run summary and grab `habit-tracker-debug-apk`. It's a zip;
   the `.apk` is inside.

If you'd rather not wait for a push, the workflow also has a **Run workflow**
button (`workflow_dispatch`).

## Install it on your phone

Move the `.apk` to the device and tap it. Android will ask you to allow
installs from whatever app is opening it — Files, Drive, Chrome — because the
debug APK isn't signed by a Play Store identity. That's expected for
sideloading and is a one-time per-source toggle.

With a USB cable and platform-tools instead:

```bash
adb install -r habit-tracker-debug.apk
```

---

## Signing a release build

The debug APK is fine for your own device but can't be shared widely or
published. For a release build, generate a keystore **once** and keep it
somewhere safe — lose it and you can never update the app on the Play Store.

```bash
keytool -genkeypair -v \
  -keystore habit-tracker-release.jks \
  -alias habit-tracker \
  -keyalg RSA -keysize 4096 -validity 10000
```

Then add four repository secrets under **Settings → Secrets and variables →
Actions**:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 habit-tracker-release.jks` (macOS: `base64 -i …`) |
| `KEYSTORE_PASSWORD` | the store password you chose |
| `KEY_ALIAS` | `habit-tracker` |
| `KEY_PASSWORD` | the key password you chose |

With those present the workflow additionally produces
`habit-tracker-release-apk` and `habit-tracker-release-aab`. The `.aab` is what
the Play Console wants; the `.apk` is for direct distribution. Without the
secrets, nothing breaks — the workflow just builds debug and says so in the log.

**Never commit the `.jks` file.** `.gitignore` already blocks `*.jks`,
`*.keystore` and `keystore.properties`.

---

## What's actually in here

```
app/src/main/
  assets/public/index.html      the whole web app, untouched
  java/app/habittracker/
    MainActivity.java           ~250 lines: the WebView host
  res/
    drawable/ic_launcher_*.xml  adaptive icon, vector, from the app's sprout mark
    mipmap-*/ic_launcher*.png   legacy raster icons (Android 7.x only)
    values/, values-night/      colours + themes mirroring the CSS tokens
    xml/                        backup rules, so habits survive a device restore
.github/workflows/build-apk.yml the CI build
tools/make_icons.py             regenerates the legacy PNGs
tools/inline_fonts.py           bundles the webfonts at build time
```

One dependency: `androidx.webkit`. No Capacitor, no Cordova, no Kotlin, no
AppCompat. `compileSdk`/`targetSdk` 35, `minSdk` 24 (Android 7.0+).

### Native touches worth knowing about

- **Storage works properly.** The page is served over
  `https://appassets.androidplatform.net/` via `WebViewAssetLoader` rather than
  `file://`. A `file://` page has a null origin, which makes `localStorage`
  unreliable across WebView versions — and `localStorage` is where your habits
  and theme preference live.
- **The status bar follows the in-app theme.** A small shim wraps the page's own
  `setTheme()` and reports the new value to native, which repaints the system
  bar areas. `Window.setStatusBarColor()` is a no-op when targeting API 35, so
  the root view's background does the colouring instead.
- **Hardware back does the right thing.** It closes an open modal first, then
  walks the SPA's own navigation stack, and only exits the app from Home.
- **It skips the Welcome screen after the first launch.** The web app always
  boots to Welcome, which gets old fast. Native jumps to Home once Welcome has
  been seen. Remove `BOOT_SCRIPT` from `MainActivity` if you'd rather it didn't.
- **Fonts are bundled when possible.** CI tries to inline the Quicksand and
  Nunito woff2 files as data URIs so the typography holds with no network. If
  Google Fonts is unreachable the step is skipped and the CDN `<link>` stays —
  the app still works, it just falls back to system fonts while offline.

### Deliberate choices you might want to reverse

- `settings.setTextZoom(100)` in `MainActivity` ignores the system font-size
  setting. The layout is a fixed 390×844 design, so scaled text would break it —
  but that is an accessibility trade-off. Delete the line to honour the OS
  setting.
- The activity is locked to portrait in `AndroidManifest.xml`
  (`android:screenOrientation="portrait"`). Remove that attribute for landscape
  and large-screen support.
- `minifyEnabled false` on release. The app is one HTML asset plus a little
  Java, so shrinking gains nothing and only adds a failure mode.

---

## Changing the app

Edit `app/src/main/assets/public/index.html` and push. That's the whole loop —
the native shell never needs touching for a UI change.

To rename the app, edit `app_name` in `app/src/main/res/values/strings.xml`.
To change the package ID, update `namespace` and `applicationId` in
`app/build.gradle` and the `package` line plus directory in
`app/src/main/java/app/habittracker/`. Do this **before** any Play Store upload;
it can't be changed afterwards.

To regenerate the launcher icons after editing the sprout artwork:

```bash
python3 tools/make_icons.py   # needs Pillow
```

## Building locally instead

You'll need JDK 17 and either Android Studio or the command-line SDK tools.
There's no `gradlew` in the repo — the Gradle wrapper needs a binary `.jar`
that couldn't be fetched when this project was generated. Create it once:

```bash
gradle wrapper --gradle-version 8.11.1
./gradlew :app:assembleDebug
```

Or skip the wrapper and use a system Gradle 8.9+ directly:

```bash
gradle :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.
