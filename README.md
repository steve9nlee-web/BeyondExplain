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

## 奇門時盤 Qi Men Hourly Chart

A second app in this repo ([`qimen.html`](qimen.html)) casts the live
**Qimen Dunjia (奇門遁甲)** chart for the current Chinese double-hour (時辰)
in the classic nine-palace (九宮) layout, fully bilingual:

- **Hourly 9-box chart** — earth & heaven stems, nine stars (九星), eight
  doors (八門) and eight gods (八神) in every palace, drawn south-up
- **Cast by the book** — 時家奇門, rotating-plate school (轉盤), ju number by
  the 拆補 method from exact solar-term times; Chief (值符) and Envoy (值使)
  highlighted
- **Live** — recasts itself automatically the moment the double-hour turns,
  with a countdown to the next chart; browse other hours with ◀ ▶ or the
  date-time picker
- **Formations & omens** — classic stem-pair patterns (青龍返首, 飛鳥跌穴,
  白虎猖狂, …), void (空亡), horse (馬星), tomb (入墓), punishment (擊刑),
  door confinement (門迫), fu-yin/fan-yin, each palace rated 大吉…大凶
- **奇門兵法 Warcraft of the hour** — the Chief's seat, the direction to act
  toward, "back to Life, strike at Death" (背生擊死), directions to avoid,
  movement timing, and an eight-door table routing everyday affairs
  (wealth, talks, exams, hiding, lawsuits…) to their directions
- **Tap any palace** for a full bilingual reading of its god, star, door,
  stems and formations

Its sideload APK is [`dist/qimen-sideload.apk`](dist/qimen-sideload.apk)
(rebuild with `cd android-qimen && ./build.sh`), and the engine has a test
suite: `node scripts/test-qimen.js`.

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

### Sideload APKs (`android-sideload/`, `android-qimen/`)

Ready-to-install APKs live in `dist/` — thin WebView shells around the web
apps, working fully offline. Copy one to your phone, allow "install unknown
apps" when prompted, and open it (Android 6.0+):

- [`dist/tongshu-sideload.apk`](dist/tongshu-sideload.apk) — the Tong Shu almanac
- [`dist/qimen-sideload.apk`](dist/qimen-sideload.apk) — the Qi Men hourly chart

To rebuild them without the Android SDK (open-source toolchain only):

```sh
sudo apt-get install aapt apksigner zipalign dalvik-exchange default-jdk
cd android-sideload && ./build.sh   # Tong Shu
cd android-qimen && ./build.sh      # Qi Men
```

Both builds sign with locally generated keys. Android only allows *updating*
an app signed with the same key — keep your keystore safe and backed up.

## How it works

All calendrical math — lunar conversion, sexagenary cycle, solar terms, the
建除 officers, 宜/忌 tables, hour gods — comes from the vendored, MIT-licensed
[lunar-javascript](https://github.com/6tail/lunar-javascript) engine
(`js/vendor/lunar.js`). `js/i18n.js` adds an English gloss for every term the
engine emits; `js/app.js` renders the daily leaf and the day finder.

Dates use the device's local time zone. Traditional almanacs are computed for
China Standard Time, so day boundaries can differ by one day for users far
from UTC+8.

The Tong Shu is cultural heritage — enjoy its guidance as tradition, not fact.
