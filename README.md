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

## Running it

It is a fully static site with no build step:

```sh
# any static server works, e.g.
python3 -m http.server 8000
# then open http://localhost:8000
```

Or publish the repository with GitHub Pages as-is.

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
