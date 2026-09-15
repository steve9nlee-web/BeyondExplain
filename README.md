# Penang Stalls Nearby

An Android app that, on open, detects where you are and lists Penang food stalls
around you, grouped into distance rings: **within 100 m**, **100–200 m**, and
onward through 200–500 m, 500 m–1 km, and beyond.

Download: [`dist/penang-stalls-nearby-v1.1.apk`](dist/penang-stalls-nearby-v1.1.apk)

## What it does

1. On launch it asks for location permission, then listens to every available
   location source at once — the fused provider and the platform GPS and
   network providers — keeping whichever fix is most accurate and returning
   early once it is good enough to separate a 100 m ring from a 200 m one.
2. It measures the great-circle distance from you to every stall in its
   catalogue and drops anything outside your search radius (default 3 km,
   adjustable from 200 m to 10 km in Settings).
3. Results are sorted nearest-first and grouped under the distance-ring headers.
4. Each stall has two actions: **Directions** (hands the coordinates to your maps
   app) and **On Penang Foodie** (opens that stall's search on the Penang Foodie
   Facebook page). A button at the bottom opens the page itself.

## Location accuracy

Distance rings of 100 m and 200 m are only as good as the position fix behind
them, so the app is explicit about how good that fix is. The summary card shows
your coordinates with a **± accuracy figure** and which provider produced it, and
the list keeps re-sorting as the fix sharpens.

Two things commonly make the position look wrong, and the app now calls out both:

- **"Approximate" location permission.** On Android 12 and newer, the permission
  dialog offers Precise or Approximate. Approximate deliberately returns a
  position rounded to roughly a 1–3 km area — no amount of waiting improves it,
  and the near rings cannot work at that scale. The app detects this and shows a
  banner with a button to upgrade to precise, falling back to the app's settings
  page if Android will no longer show the dialog.
- **An unsettled fix.** The first position a phone offers is normally a
  cell-tower or wifi estimate, hundreds of metres out; GPS takes several seconds
  to lock and needs a view of the sky. The app spends up to 20 seconds
  converging, shows the accuracy improving while it does, and warns when the
  final fix is still worse than ±50 m.

A settled outdoor GPS lock is typically ±5–10 m. Indoors, or in the shophouse
streets of George Town, ±30–60 m is more realistic.

Separately, **the bundled stall coordinates are approximate**, so even a perfect
fix will not put you exactly at a stall front — see the next section. A feed URL
with surveyed coordinates is the fix for that half of the problem.

## About the Penang Foodie source — read this

The app **links out** to <https://www.facebook.com/penangfoodie>; it does not
pull the stall list from it. That is a deliberate limitation, not an oversight:

- Facebook's Graph API only exposes another page's posts under **Page Public
  Content Access**, a permission that requires app review and a business
  relationship. It is not available to an arbitrary app reading a page it
  doesn't own.
- Scraping the page's HTML breaks Facebook's terms of service, and the markup is
  obfuscated and login-walled in a way that would break constantly anyway.
- Even with access, Facebook posts are prose and photos. They carry no
  coordinates, so they cannot be turned into distance rings without a separate
  geocoding and curation step.

So the app ships with a hand-compiled catalogue of 46 well-known Penang hawker
spots in `app/src/main/assets/stalls.json`, and every stall links through to the
Penang Foodie page so you can read what they've posted about it.

**The coordinates in the bundled file are approximate — accurate to roughly the
right street, not the right stall front.** They are fine for demonstrating the
distance rings and for finding your way to the right corner, but verify them
before depending on them.

### Using your own stall data

The real path to accurate, up-to-date data is a feed you control. In **Settings**,
paste an `https` URL serving the JSON format in
[`docs/stall-feed-format.md`](docs/stall-feed-format.md). The app fetches it on
refresh and uses it in place of the bundled catalogue, falling back to the
bundled one if the fetch fails. You can populate that feed however you like —
by hand, from your own Page's API access, or from a geocoding pass over a
curated list.

## Installing the APK

The APK is signed with the standard Android **debug key**, so it installs
without a Play Store account but is not suitable for distribution:

1. Copy `dist/penang-stalls-nearby-v1.1.apk` to your phone.
2. Enable "install unknown apps" for whichever app you are opening it from.
3. Tap it to install, open **Penang Stalls**, and allow location access.

Requires Android 7.0 (API 24) or newer. Before publishing anywhere, replace the
release `signingConfig` in `app/build.gradle.kts` with a real keystore.

## Building from source

```bash
export ANDROID_SDK_ROOT=/path/to/android-sdk   # needs platform 35, build-tools 35.0.0
./gradlew testDebugUnitTest      # unit tests
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
```

Kotlin 2.0.21, Jetpack Compose (Material 3), AGP 8.7.3, minSdk 24 / targetSdk 35.

## Layout

```
app/src/main/java/com/beyondexplain/penangstalls/
  MainActivity.kt              launcher activity, permission request
  data/Stall.kt                stall model + distance rings
  data/Fix.kt                  location fix + which fix beats which
  data/Geo.kt                  haversine distance
  data/StallCatalog.kt         feed parser (skips bad rows, keeps the rest)
  data/StallRepository.kt      remote feed with bundled fallback, distance maths
  data/AppSettings.kt          radius + feed URL persistence
  data/FacebookLinks.kt        Penang Foodie and maps deep links
  location/LocationProvider.kt converges on the most accurate fix available
  ui/                          Compose screen, view model, theme
app/src/main/assets/stalls.json  bundled catalogue
```
