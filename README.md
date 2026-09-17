# Haze Index

An Android app that shows the current haze / air quality index for Southeast Asian cities.
**Swipe down on the main screen and it goes out to the internet and pulls a fresh reading** —
there is no background cache being re-served, every pull is a live HTTP request.

## Download

Pre-built APKs are in [`dist/`](dist/):

| File | Notes |
| --- | --- |
| `dist/HazeIndex-1.2.apk` | Release build, ~4.7 MB — install this one |
| `dist/HazeIndex-1.2-debug.apk` | Debug build, same app with debug symbols |

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
| `Cities.kt` | Location catalogue, the device "city", and the distance maths |

## Known limitation

The build machine's network policy blocks every one of these APIs, so **no live response was
ever fetched during development**. To cover that, each parser is a pure function over a
response body and every one is unit tested against a captured response shape — success and
failure, including a bad aqicn.org token, an IQAir `incorrect_api_key`, the NEA v1 and v2
shapes, and Open-Meteo with and without its `current` block. `./gradlew :app:testDebugUnitTest`
runs 22 of these. That is a good deal stronger than "written from the docs", but it is still
not the same as a real response: if a field moves, `AirQualityParsers.kt` is the one place to
adjust, and each parser fails with a message the UI shows rather than crashing.

Likewise there is no GPS or geocoder on that machine, so the follow-the-device path is unit
tested (distance maths, the Singapore bounding box, the stable device-city id) but has never
met a real position fix.
