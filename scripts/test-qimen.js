#!/usr/bin/env node
/*
 * Tests for js/qimen-engine.js.
 *   1. Full comparison against a hand-derived reference chart
 *      (2026-08-30 15:30 → 陰遁七局, 處暑下元, 丙子日 丙申時, 甲午旬).
 *   2. Structural invariants swept over a year of hours.
 * Run: node scripts/test-qimen.js
 */
'use strict';
const path = require('path');
const Q = require(path.join(__dirname, '..', 'js', 'qimen-engine.js'));

let failures = 0;
function eq(actual, expected, label) {
  const a = JSON.stringify(actual);
  const e = JSON.stringify(expected);
  if (a !== e) {
    failures++;
    console.error(`FAIL ${label}: expected ${e}, got ${a}`);
  }
}

// ---------- 1. hand-derived reference chart ----------
const c = Q.compute(new Date(2026, 7, 30, 15, 30, 0));
eq(c.pillars.day, '丙子', 'day pillar');
eq(c.pillars.time, '丙申', 'hour pillar');
eq(c.isYang, false, 'yin escape');
eq(c.ju, 7, 'ju 7 (處暑下元)');
eq(c.yuan, 2, 'lower yuan');
eq(c.fuTou, '甲戌', 'leader day');
eq(c.xun, '甲午', 'xun');
eq(c.xunYi, '辛', 'hidden yi');
eq(c.xunKong, '辰巳', 'void branches');
eq(c.zhiFu, { star: '天輔', homePalace: 4, palace: 9 }, 'chief');
eq(c.zhiShi, { door: '杜門', homePalace: 4, palace: 2 }, 'envoy');

// earth plate for yin ju 7: 戊7 己6 庚5 辛4 壬3 癸2 丁1 丙9 乙8
const earthExp = { 7: '戊', 6: '己', 5: '庚', 4: '辛', 3: '壬', 2: '癸', 1: '丁', 9: '丙', 8: '乙' };
for (const p of Object.keys(earthExp)) eq(c.palaces[p].earth, earthExp[p], `earth ${p}`);

// heaven plate: 天輔 anchors at 9 carrying 辛; ring follows
const heavenExp = {
  9: { stars: ['天輔'], stems: ['辛'] },
  2: { stars: ['天英'], stems: ['丙'] },
  7: { stars: ['天芮', '天禽'], stems: ['癸', '庚'] },
  6: { stars: ['天柱'], stems: ['戊'] },
  1: { stars: ['天心'], stems: ['己'] },
  8: { stars: ['天蓬'], stems: ['丁'] },
  3: { stars: ['天任'], stems: ['乙'] },
  4: { stars: ['天沖'], stems: ['壬'] },
  5: { stars: [], stems: [] }
};
for (const p of Object.keys(heavenExp)) {
  eq(c.palaces[p].stars, heavenExp[p].stars, `stars ${p}`);
  eq(c.palaces[p].heaven, heavenExp[p].stems, `heaven stems ${p}`);
}

// doors: 杜門 at 2, ring order onward
const doorExp = { 2: '杜門', 7: '景門', 6: '死門', 1: '驚門', 8: '開門', 3: '休門', 4: '生門', 9: '傷門', 5: null };
for (const p of Object.keys(doorExp)) eq(c.palaces[p].door, doorExp[p], `door ${p}`);

// gods: yin escape runs counterclockwise from the chief at 9
const godExp = { 9: '值符', 4: '螣蛇', 3: '太陰', 8: '六合', 1: '白虎', 6: '玄武', 7: '九地', 2: '九天', 5: null };
for (const p of Object.keys(godExp)) eq(c.palaces[p].god, godExp[p], `god ${p}`);

eq(c.palaces[4].flags.kong, true, 'void at 4 (辰巳)');
eq(c.palaces[8].flags.horse, true, 'horse at 8 (申→寅)');
eq(c.palaces[9].flags.xing, true, '辛 punishment at 9');
eq(c.palaces[6].flags.tomb, true, '戊 tomb at 6');
eq(c.palaces[7].flags.menPo, true, '景門(火) confined in 兌(金)');
eq(c.palaces[7].patterns.some(p => p.name === '伏宮格'), true, '庚加戊 at 7');
eq(c.fuYin, false, 'not fu yin');
eq(c.fanYin, false, 'not fan yin');

// ---------- 2. invariants over a year of hours ----------
const QIYI = ['戊', '己', '庚', '辛', '壬', '癸', '丁', '丙', '乙'];
const start = new Date(2026, 0, 1, 0, 30, 0);
for (let i = 0; i < 366 * 12; i += 7) { // every 14 hours across 2026
  const d = new Date(start.getTime() + i * 2 * 3600 * 1000);
  const r = Q.compute(d);
  const label = d.toISOString();

  // earth plate is a permutation of the nine stems
  const earthStems = [];
  for (let p = 1; p <= 9; p++) earthStems.push(r.palaces[p].earth);
  eq([...earthStems].sort().join(''), [...QIYI].sort().join(''), `${label} earth permutation`);

  // adjacency: earth stems follow QIYI order around ju
  const base = r.ju;
  for (let k = 0; k < 9; k++) {
    const p = ((r.isYang ? base + k : base - k) % 9 + 8) % 9 + 1;
    eq(r.palaces[p].earth, QIYI[k], `${label} earth order ${k}`);
  }

  // heaven stems and stars: each outer palace exactly one star (plus 禽 rider)
  const allStars = [], allDoors = [], allGods = [], allHeaven = [];
  for (const p of Q.RING) {
    const pal = r.palaces[p];
    allStars.push(...pal.stars);
    allDoors.push(pal.door);
    allGods.push(pal.god);
    allHeaven.push(...pal.heaven);
    eq(pal.stars.length, pal.stars.includes('天芮') ? 2 : 1, `${label} star count ${p}`);
    // star carries its home-seat earth stem
    const home = Q.RING[['天蓬', '天任', '天沖', '天輔', '天英', '天芮', '天柱', '天心'].indexOf(pal.stars[0])];
    eq(pal.heaven[0], r.palaces[home].earth, `${label} heaven stem ${p}`);
  }
  eq(allStars.sort().join(','), '天任,天心,天沖,天柱,天英,天芮,天蓬,天輔,天禽'.split(',').sort().join(','), `${label} all stars`);
  eq(allDoors.sort().join(','), '休門,傷門,開門,景門,杜門,死門,生門,驚門'.split(',').sort().join(','), `${label} all doors`);
  eq(allGods.sort().join(','), '九地,九天,值符,六合,太陰,玄武,白虎,螣蛇'.split(',').sort().join(','), `${label} all gods`);
  eq(allHeaven.sort().join(''), [...QIYI].sort().join(''), `${label} heaven permutation`);

  // chief star sits at its reported palace; envoy door at its palace
  eq(r.palaces[r.zhiFu.palace].stars.includes(r.zhiFu.star === '天禽' ? '天芮' : r.zhiFu.star), true, `${label} chief placed`);
  eq(r.palaces[r.zhiShi.palace].door, r.zhiShi.door, `${label} envoy placed`);
  // god 值符 accompanies the chief's palace
  eq(r.palaces[r.zhiFu.palace].god, '值符', `${label} god zhifu`);

  // xun-start hour must be a perfect fu-yin chart (heaven equals earth)
  if (r.pillars.time === r.xun) {
    eq(r.fuYin, true, `${label} xun start fu yin`);
    for (const p of Q.RING) eq(r.palaces[p].heaven[0], r.palaces[p].earth, `${label} fuyin stem ${p}`);
  }

  // ju number must match the term table bounds
  eq(r.ju >= 1 && r.ju <= 9, true, `${label} ju range`);
}

// ---------- 3. yang-escape spot checks ----------
// 冬至 2025-12-21 23:03 CST; 2025-12-25 is 甲辰日 → leader 甲辰 (下元? 辰=lower).
const y = Q.compute(new Date(2025, 11, 25, 12, 30, 0));
eq(y.isYang, true, 'yang escape after 冬至');
eq(y.term.name, '冬至', 'term is 冬至');
// yang escape gods run clockwise from the chief
const g0 = Q.RING.indexOf(y.zhiFu.palace);
eq(y.palaces[Q.RING[(g0 + 1) % 8]].god, '螣蛇', 'yang gods clockwise');

if (failures === 0) {
  console.log('All Qimen engine tests passed.');
} else {
  console.error(`${failures} failure(s).`);
  process.exit(1);
}
