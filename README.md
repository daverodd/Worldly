# Currency Exchange widget (standalone Android app)

A native replacement for your KWGT preset "Currency Exchange". No KWGT, no Sun Surveyor, no
other app needed. Install the APK, add the widget, done.

## Get the APK (pick one)

**A. GitHub Actions (no installs on your side)**
1. Create a new *private* repo on GitHub and upload the contents of this folder.
   (`keys.properties` is git-ignored on purpose; see "API keys" below.)
2. Open the repo's **Actions** tab, run **Build APK** (it also runs on every push).
3. When it finishes, download the artifact **CurrencyWidget-debug-apk** and unzip it:
   `app-debug.apk` is your app.

**B. Android Studio**
Open this folder, wait for Gradle sync, then **Build > Build APK(s)**.
Or from a terminal: `./gradlew assembleDebug` (needs JDK 17+ and the Android SDK).
Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Install & set up
1. Copy the APK to your phone and open it (allow "install unknown apps" when asked).
2. Open **Currency Exchange** once. Check the cities / currencies, tap **Save & refresh**.
3. Tap **Use my location for weather** (or type a home city) so the top bar shows weather and sun times.
4. Tap **Add widget to home screen** (or long-press home screen > Widgets).

## What was replicated

| KWGT preset | This app |
|---|---|
| 4 world clocks (day, HH:mm, city) | Same; timezone looked up from the city name (Geoapify), updates every minute |
| Flag backgrounds + yellow/navy day/night strip | Same flags (google/region-flags); day/night now computed on the phone (no API) |
| Feels-like and humidity (Tomorrow.io) | Same API and fields |
| Sunrise/sunset from the Sun Surveyor app | Computed offline from your location (accurate to about 2 min) |
| 4 rows: `USD/DKK/EUR/RON > COP` market rate | Same source (fawazahmed0 currency-api, with a backup mirror) |
| Visa rate with fee (global "visa fee" = 2) | Same Visa endpoint; fee editable in the app |
| Carbon+ fonts, colors, corner radii, layout | Same fonts bundled; layout measured from your preset |

## Differences you should know about
- **Visa number is now visible.** In the preset the text color followed the wallpaper and was
  invisible in the thumbnail; here it is white, next to the Visa logo (moved slightly left to fit).
- **Wallpaper-based colors (Material You "accent") are fixed** to the dark navy/grey from the
  preview. Easy to change in `Renderer.java` (`fillRound` calls).
- **Tapping the widget opens the settings screen** (the preset opened the Clock app).
- The Visa API's direction (`fromCurr`/`toCurr`) is ambiguous, so the app shows whichever
  reading (X or 1/X) is closest to the market rate. If Visa blocks the request, the Visa
  number is simply hidden and the logo remains.
- Weather needs a location. Android doesn't let widgets read GPS in the background, so the app
  stores the location when you tap the button (re-tap if you travel), or uses your "home city".

## API keys
Your two keys (Geoapify, Tomorrow.io) were taken from the preset and put in `keys.properties`
so your local builds embed them. Anyone can extract keys from an APK, so don't share the APK
publicly without rotating them. For GitHub builds, either add repo secrets named
`GEOAPIFY_KEY` and `TOMORROW_KEY` (Settings > Secrets and variables > Actions), or just
paste the keys into the app's settings screen after installing.

## How it updates
- Clocks and day/night: redrawn every minute (alarm chain, no permission needed).
- Rates and weather: refreshed about every 30 minutes by Android's job scheduler, only when a
  network is available; last values are cached and shown while offline.
- Flags are downloaded once and stored on the device.

## Code map (`app/src/main/java/app/currencyexchange/widget/`)
`Renderer` draws everything onto one bitmap; `Fetcher` does all network calls; `Store` holds settings
and cache; `SunCalc` offline sun times; `WidgetProvider` + `RefreshJobService` handle updates;
`MainActivity` is the settings screen; `Util`, `Net`, `Flags` are helpers.

## Customizing
- Cities/currencies/fee/keys: in the app.
- More than 4 rows/columns, colors, sizes: `Renderer.java` (units are the same 750-wide grid as the original preset).
