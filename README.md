# Haze Index

An Android app that shows the current haze / air quality index for Southeast Asian cities.
**Swipe down on the main screen and it goes out to the internet and pulls a fresh reading** —
there is no background cache being re-served, every pull is a live HTTP request.

## Download

Pre-built APKs are in [`dist/`](dist/):

| File | Notes |
| --- | --- |
| `dist/HazeIndex-1.5.apk` | Release build, ~5.5 MB — install this one |
| `dist/HazeIndex-1.5-debug.apk` | Debug build, same app with debug symbols |

Install on the phone: copy the APK across (or download it from GitHub on the device), open it,
and allow "install from unknown sources" when Android asks. Android 7.0 (API 24) or newer.

Both APKs are signed with the standard Android **debug** keystore so they install without extra
steps. That key is fine for sideloading and testing; swap in a real keystore in
`app/build.gradle.kts` before publishing to a store.

## What it shows

- **Headline index** — Singapore's official **PSI (24-hour)** from NEA, or the AQI from the
  nearest monitoring station — colour-coded Good → Hazardous, with the matching health advice,
  and a line saying whether the number was **measured at a station or modelled** (see
  [Data sources](#data-sources-and-why-accuracy-varies)).
- **24-hour PM2.5 trend** — one bar per hour, each coloured by how bad that hour was, so you can
  see whether the haze is building or clearing.
- **Current pollutants** — PM2.5, PM10, ozone, NO₂, SO₂ and CO.
- **PSI by region** for Singapore (north / south / east / west / central).
- **Last saved reading** when the phone is offline, clearly labelled as cached.

### Haze map

The map icon in the toolbar opens the readings as a picture rather than a single number.

- **The chosen location is pinned in a card across the top** — its index, band and whether
  that number was measured or modelled — and marked on the map with a highlighted pin, so it
  never gets lost among the others.
- **Pan or zoom and the area reloads.** Whatever is in view is fetched for that viewport
  (debounced, so a drag costs one request, not one per frame).
- **With an aqicn.org token**: every real monitoring station in view, each a pin showing its
  own index, coloured by band.
- **Without one**: a 5×5 lattice is sampled across the viewport in a *single* Open-Meteo
  request and drawn as translucent cells — a modelled heat map of where the haze is sitting.
- **Singapore's five official PSI regions** are layered on whenever they are in view, keyless,
  and they always render as pins because they are measured.
- Tap any pin or cell for its name, index, band, whether it was measured or modelled, and when.
- A colour legend runs along the bottom, with buttons to recentre on the chosen location and
  to refresh.

Tiles come from OpenStreetMap through [osmdroid](https://github.com/osmdroid/osmdroid) — no
API key, no Play Services, and the tile cache lives in the app's own cache directory. Tiles are
colour-inverted to match the dark UI.

### Local reading that follows the device

"Follow my location" in the toolbar switches the app to the device's own position, and it
keeps following it:

- The first fix comes from the cached position when that is under five minutes old, otherwise
  the app asks for a live one (network provider first — it fixes fast indoors and is accurate
  enough for an air quality grid cell) and falls back to a stale fix rather than showing nothing.
- While the app is on screen it listens for position updates and **re-fetches automatically once
  you have moved 3 km**, rate-limited to one automatic refresh every 5 minutes. Updates stop the
  moment the app goes to the background, so it does not sit on the radio.
- The place name comes from the device geocoder, so the header reads "Bedok" rather than
  a pair of coordinates.
- **Inside Singapore the local reading still uses the official NEA PSI**, including the regional
  breakdown, so it matches what local advisories quote. Elsewhere it uses US AQI.
- Follow mode is remembered across restarts, and the last known place is shown straight away
  while the new fix is acquired.
- Only `ACCESS_COARSE_LOCATION` is requested, and it is optional — deny it, or never turn follow
  mode on, and everything else still works. If location is switched off system-wide the app says
  so and offers a shortcut to the settings screen. Picking a city from the list turns following
  back off and returns to that city.

Fixed locations: Singapore, Kuala Lumpur, Johor Bahru, George Town, Kuching, Kota Kinabalu,
Jakarta, Pekanbaru, Palembang, Pontianak, Bangkok, Chiang Mai, Manila, Bandar Seri Begawan,
Hanoi, Ho Chi Minh City, Phnom Penh.

## Data sources, and why accuracy varies

Not all air quality numbers are the same kind of number. This matters more than it sounds:

| Source | Kind | Key | Notes |
| --- | --- | --- | --- |
| **NEA / data.gov.sg** | Measured | none | Singapore's official ground network. The PSI local advisories quote. Used automatically anywhere in Singapore. |
| **aqicn.org (WAQI)** | Measured | free token | Nearest real monitoring station, worldwide. Aggregates the national networks — NEA, Malaysia's DOE, Thailand's PCD, US embassy monitors. |
| **IQAir AirVisual** | Measured | free key | Nearest station's US AQI and dominant pollutant. The free tier does not include concentrations. |
| **Open-Meteo** | **Modelled** | none | CAMS output on a ~11 km grid. Keyless and global, so it is the fallback — but it is a *simulation*, and during a haze episode it can sit well off what a station down the road is measuring. Also supplies the 24-hour trend line. |

The app picks the best available and **says on screen which one it used**: either
"Measured · Bedok, 2.1 km away · driven by PM2.5", or "Modelled · ~11 km weather-model
grid, not a station".

### Getting a station key (recommended)

Out of the box, with no key, the app uses the model — except in Singapore, where the
official NEA PSI needs no key. For measured readings everywhere else, open **Data source**
in the toolbar overflow and paste one free key:

- **aqicn.org** — https://aqicn.org/data-platform/token/ (instant, email only). This is the
  one to get; coverage across Southeast Asia is good.
- **IQAir** — https://www.iqair.com/air-pollution-data-api (free "Community" tier).

The same screen lets you force a specific source instead of letting the app choose. Keys are
stored in the app's own private `SharedPreferences` and are only ever sent to the service
they belong to.

### How a reading is assembled

1. In Singapore the NEA PSI takes the headline, with the five-region breakdown.
2. Otherwise a station reading (aqicn.org, then IQAir) takes the headline.
3. Open-Meteo fills what is left — always the hourly trend, and the concentrations for
   IQAir, which does not publish them.
4. With no key and no station, the model stands alone and the screen says so.

A configured source that fails is never swapped out silently: the reason ("aqicn.org token
is not valid", "Rate limit reached for this API key") appears in the banner.

Note that aqicn.org publishes **per-pollutant AQI sub-indices**, not concentrations, so when
that source is in use the pollutant tiles are labelled `AQI` rather than `µg/m³`.

## Edge-to-edge

Targeting SDK 35 means Android 15 lays the app out behind the status and navigation bars, so
every piece of chrome asks for the insets it needs (`Insets.kt`): the toolbar and the map's
header card move clear of the status bar and any notch, the scrolling content and the map's
bottom card clear the navigation bar, and the map itself stays deliberately full bleed beneath
them. The system bars are forced to light icons because the app is dark whatever the device
theme is.

## Launcher icon

The gold "Haze Index — property of Synapse Asia Sdn Bhd" seal, generated from the supplied
artwork into the full icon set:

- `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.png` — the emblem cut to a disc with transparent
  corners, so it sits correctly in any launcher rather than as a black square.
- `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_foreground.png` + `drawable/ic_launcher_background.xml`
  — the adaptive icon for Android 8+. The emblem is 70dp on the 108dp canvas, inside the 72dp
  circle every mask is guaranteed to show, so no mask clips the ring.
- The background layer is solid black to match the emblem's own backdrop, so the two adaptive
  layers blend under parallax.

The ring text is legible from xhdpi (96px) upward; at mdpi (48px) the seal reads as a mark
rather than as words — inherent to any text-bearing circular badge at that size.

## Building from source

```bash
export ANDROID_HOME=/path/to/android-sdk   # needs platform 35 + build-tools 35.0.0
./gradlew :app:assembleRelease             # -> app/build/outputs/apk/release/
./gradlew :app:testDebugUnitTest           # unit tests for the index scales and timestamp parsing
```

JDK 17 or newer, Gradle wrapper included (8.11.1), AGP 8.7.3, Kotlin 2.0.21.

## Code map

| File | Role |
| --- | --- |
| `MainActivity.kt` | UI, swipe-to-refresh wiring, city picker, permission flow |
| `HazeViewModel.kt` | Refresh state machine and follow-the-device tracking; survives rotation |
| `DeviceLocation.kt` | Position fixes, foreground position updates, reverse geocoding |
| `HazeRepository.kt` | The network calls, the source-priority chain and the merge rules |
| `AirQualityParsers.kt` | One parser per feed, pure functions over a response body |
| `Settings.kt` | API keys and the preferred source |
| `ReportCache.kt` | Last reading persisted to `SharedPreferences` |
| `TrendView.kt` | Hand-drawn 24-hour PM2.5 bar chart (no charting dependency) |
| `Model.kt` | Report model plus the PSI / US AQI / PM2.5 band thresholds |
| `Cities.kt` | Location catalogue, the device "city", distance maths and `MapBounds` |
| `MapActivity.kt` | The map screen: overlays, viewport reloads, pin taps |
| `MapViewModel.kt` | Map state: the chosen location's reading plus the area readings |
| `MarkerIcons.kt` | Map pins drawn at runtime, coloured by band |

## Known limitation

The build machine's network policy blocks every one of these APIs, so **no live response was
ever fetched during development**. To cover that, each parser is a pure function over a
response body and every one is unit tested against a captured response shape — success and
failure, including a bad aqicn.org token, an IQAir `incorrect_api_key`, the NEA v1 and v2
shapes, and Open-Meteo with and without its `current` block. `./gradlew :app:testDebugUnitTest`
runs 37 of these, the map's station-bounds and modelled-grid feeds included, plus four
Robolectric tests that start the real activities and assert the window-inset handling. That is a good deal stronger than "written from the docs", but it is still
not the same as a real response: if a field moves, `AirQualityParsers.kt` is the one place to
adjust, and each parser fails with a message the UI shows rather than crashing.

Likewise there is no GPS, geocoder or display on that machine, so the follow-the-device path
and the map are unit tested where they can be (distance maths, the Singapore bounding box, the
stable device-city id, the viewport lattice and cell sizing) but neither has met a real position
fix or drawn a real tile.
