# 通勝 Tong Shu Daily Almanac

A daily Chinese almanac (通勝 / 老黃曆) web app, styled as a tear-off calendar leaf.
For any date it shows, fully bilingual (中文 + English):

- **Lunar date & pillars** — 农历 date, year/month/day 干支 pillars, zodiac year, Na Yin element
- **Day Officer (建除十二神)** — rendered as a cinnabar seal, with its meaning
- **宜 / 忌 Daily activities** — what the almanac favors and warns against, every term translated
- **沖煞 Clash & Sha** — the clashing zodiac sign and the Sha direction
- **時辰 Auspicious hours** — all twelve double-hours ruled 吉/凶 by the hour gods, current hour highlighted
- **吉神方位 Lucky directions** — positions of the Gods of Joy, Fortune and Wealth
- **節氣 Solar terms** — the current term and countdown to the next
- **彭祖百忌 Peng Zu's taboos** — the day's two taboo couplets, translated
- **擇日 Auspicious day finder** — pick an activity (wedding, opening a business, moving house, …) and see the next favorable days

Navigate with the ◀ ▶ buttons, the date picker, the **Today** button, or the arrow keys.

## 八字 Bazi chart (`bazi.html`)

A companion page casts a full **Four Pillars of Destiny** chart from a birth
date, time and gender, fully bilingual:

- **The four pillars** — year/month/day/hour stems & branches with 十神 Ten
  Gods, 藏干 hidden stems, 納音 Na Yin and the 十二長生 life stages, plus the
  Life/Body palaces and conception pillar
- **日主 Day Master** — the day stem's nature and a strong/weak reading
- **五行 Five-element balance** — weighted element bars and the derived
  favorable (喜用神) and unfavorable (忌神) elements
- **大運 10-year luck pillars** — the whole luck cycle with start age, current
  decade highlighted; tap any decade to expand it
- **流年 Yearly luck** — each year of the chosen decade rated 大吉/吉/平/慎/凶
  against the favorable elements, with 值/沖太歲 Tai Sui flags
- **開運風水 Good-luck feng shui** — lucky colors, directions, numbers and
  room-by-room enhancers for the favorable elements, plus this year's Tai Sui
  advice

Birth details are kept in `localStorage` on the device only.

## Running it

It is a fully static site with no build step:

```sh
# any static server works, e.g.
python3 -m http.server 8000
# then open http://localhost:8000
```

Or publish the repository with GitHub Pages as-is.

## Android app

There are two Android builds in this repo:

### Play Store build (`android/`, Capacitor)

A standard Capacitor + Gradle project targeting SDK 36, used to produce the
`.aab` app bundle that Google Play requires. With [Node.js](https://nodejs.org)
and [Android Studio](https://developer.android.com/studio) installed:

```sh
npm install
npm run sync          # assembles www/ and syncs it into android/
```

Then open the `android/` folder in Android Studio, let Gradle sync, and use
**Build → Generate Signed App Bundle** with your upload keystore. After any
change to the web app, run `npm run sync` again before rebuilding.

### Sideload APK (`android-sideload/`)

A ready-to-install APK is at
[`dist/tongshu-sideload.apk`](dist/tongshu-sideload.apk) — a thin WebView
shell around the same web app, working fully offline. Copy it to your phone,
allow "install unknown apps" when prompted, and open it (Android 6.0+).

To rebuild it without the Android SDK (open-source toolchain only):

```sh
sudo apt-get install aapt apksigner zipalign dalvik-exchange default-jdk
cd android-sideload && ./build.sh
```

Both builds sign with locally generated keys. Android only allows *updating*
an app signed with the same key — keep your keystore safe and backed up.

## How it works

All calendrical math — lunar conversion, sexagenary cycle, solar terms, the
建除 officers, 宜/忌 tables, hour gods — comes from the vendored, MIT-licensed
[lunar-javascript](https://github.com/6tail/lunar-javascript) engine
(`js/vendor/lunar.js`). `js/i18n.js` adds an English gloss for every term the
engine emits; `js/app.js` renders the daily leaf and the day finder;
`js/bazi.js` renders the Bazi chart page, using the engine's `EightChar`
(pillars, hidden stems, Na Yin) and `Yun` (luck-pillar cycle) APIs together
with its own simplified strength/favorable-element heuristics.

Dates use the device's local time zone. Traditional almanacs are computed for
China Standard Time, so day boundaries can differ by one day for users far
from UTC+8.

The Tong Shu is cultural heritage — enjoy its guidance as tradition, not fact.
