/*
 * Qimen Dunjia (奇門遁甲) hourly chart engine — 時家奇門 · 轉盤 (rotating plate).
 *
 * Builds the full nine-palace chart for any moment:
 *   - yin/yang escape (陰遁/陽遁) and ju number (局數) via the 拆補 method,
 *     using the exact solar-term times from the vendored lunar.js engine
 *   - earth plate (地盤): three nobles & six yi (三奇六儀)
 *   - heaven plate (天盤): nine stars rotated so the Chief (值符) rides
 *     the hour-stem palace, each star carrying its home stem
 *   - eight doors (八門) led by the Envoy (值使), eight gods (八神)
 *   - void branches (空亡), the travelling horse (驛馬), tomb (入墓),
 *     punishment (擊刑), door confinement (門迫)
 *   - classic stem-pair formations (格局) incl. 青龍返首 / 飛鳥跌穴 etc.
 *
 * Pure calculation, no DOM. Works in the browser (window.QimenEngine)
 * and under node (module.exports) for the test suite. Depends on the
 * globals `Solar` from js/vendor/lunar.js.
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) {
    module.exports = factory(require('./vendor/lunar.js').Solar);
  } else {
    root.QimenEngine = factory(root.Solar);
  }
})(typeof self !== 'undefined' ? self : this, function (Solar) {
  'use strict';

  var STEMS = ['甲', '乙', '丙', '丁', '戊', '己', '庚', '辛', '壬', '癸'];
  var BRANCHES = ['子', '丑', '寅', '卯', '辰', '巳', '午', '未', '申', '酉', '戌', '亥'];

  // Nine palaces in Luoshu numbering. Ring is the clockwise circuit of the
  // eight outer palaces starting at Kan 1 (north).
  var RING = [1, 8, 3, 4, 9, 2, 7, 6];
  var OPPOSITE = { 1: 9, 9: 1, 2: 8, 8: 2, 3: 7, 7: 3, 4: 6, 6: 4, 5: 5 };
  var ORIG_STAR = { 1: '天蓬', 2: '天芮', 3: '天沖', 4: '天輔', 5: '天禽', 6: '天心', 7: '天柱', 8: '天任', 9: '天英' };
  var ORIG_DOOR = { 1: '休門', 2: '死門', 3: '傷門', 4: '杜門', 6: '開門', 7: '驚門', 8: '生門', 9: '景門' };
  // Fixed circular order of stars / doors along RING (their home seats).
  var STAR_RING = ['天蓬', '天任', '天沖', '天輔', '天英', '天芮', '天柱', '天心'];
  var DOOR_RING = ['休門', '生門', '傷門', '杜門', '景門', '死門', '驚門', '開門'];
  var GODS = ['值符', '螣蛇', '太陰', '六合', '白虎', '玄武', '九地', '九天'];

  // Order in which the six yi and three nobles are laid on the earth plate.
  var QIYI = ['戊', '己', '庚', '辛', '壬', '癸', '丁', '丙', '乙'];
  // Each ten-day xun hides its leader stem 甲 behind one yi (旬首遁儀).
  var XUN_YI = { '甲子': '戊', '甲戌': '己', '甲申': '庚', '甲午': '辛', '甲辰': '壬', '甲寅': '癸' };

  // Ju numbers per solar term for upper/middle/lower yuan (上/中/下元).
  // Terms from 冬至 through 芒種 are yang escape; 夏至 through 大雪 yin.
  var JU_TABLE = {
    '冬至': [1, 7, 4], '小寒': [2, 8, 5], '大寒': [3, 9, 6],
    '立春': [8, 5, 2], '雨水': [9, 6, 3], '驚蟄': [1, 7, 4],
    '春分': [3, 9, 6], '清明': [4, 1, 7], '穀雨': [5, 2, 8],
    '立夏': [4, 1, 7], '小滿': [5, 2, 8], '芒種': [6, 3, 9],
    '夏至': [9, 3, 6], '小暑': [8, 2, 5], '大暑': [7, 1, 4],
    '立秋': [2, 5, 8], '處暑': [1, 4, 7], '白露': [9, 3, 6],
    '秋分': [7, 1, 4], '寒露': [6, 9, 3], '霜降': [5, 8, 2],
    '立冬': [6, 9, 3], '小雪': [5, 8, 2], '大雪': [4, 7, 1]
  };
  var YANG_TERMS = { '冬至': 1, '小寒': 1, '大寒': 1, '立春': 1, '雨水': 1, '驚蟄': 1, '春分': 1, '清明': 1, '穀雨': 1, '立夏': 1, '小滿': 1, '芒種': 1 };
  // lunar.js emits simplified-character term names; normalise to the keys above.
  var TERM_ALIAS = { '惊蛰': '驚蟄', '谷雨': '穀雨', '小满': '小滿', '芒种': '芒種', '处暑': '處暑', '大雪': '大雪', 'DONG_ZHI': '冬至' };

  // Palace holding each earthly branch (for void/horse/tomb marking).
  var BRANCH_PALACE = { '子': 1, '丑': 8, '寅': 8, '卯': 3, '辰': 4, '巳': 4, '午': 9, '未': 2, '申': 2, '酉': 7, '戌': 6, '亥': 6 };
  // Travelling horse branch by hour-branch trine: 申子辰→寅, 寅午戌→申, 巳酉丑→亥, 亥卯未→巳.
  var HORSE = { '申': '寅', '子': '寅', '辰': '寅', '寅': '申', '午': '申', '戌': '申', '巳': '亥', '酉': '亥', '丑': '亥', '亥': '巳', '卯': '巳', '未': '巳' };
  // Tomb palace of each stem (十干入墓): fire/yang-earth 戌→乾6, metal/yin-earth 丑→艮8,
  // water 辰→巽4, yin-water 未→坤2, per the standard growth-cycle derivation.
  var TOMB = { '乙': 6, '丙': 6, '戊': 6, '丁': 8, '己': 8, '庚': 8, '辛': 4, '壬': 4, '癸': 2 };
  // Six-yi punishment palaces (六儀擊刑).
  var YI_XING = { '戊': 3, '己': 2, '庚': 8, '辛': 9, '壬': 4, '癸': 4 };

  var DOOR_ELEM = { '休門': '水', '生門': '土', '傷門': '木', '杜門': '木', '景門': '火', '死門': '土', '驚門': '金', '開門': '金' };
  var PALACE_ELEM = { 1: '水', 2: '土', 3: '木', 4: '木', 5: '土', 6: '金', 7: '金', 8: '土', 9: '火' };
  var CONQUERS = { '木': '土', '土': '水', '水': '火', '火': '金', '金': '木' };

  var PALACE_INFO = {
    1: { name: '坎', dir: 'N', zh: '北' }, 2: { name: '坤', dir: 'SW', zh: '西南' },
    3: { name: '震', dir: 'E', zh: '東' }, 4: { name: '巽', dir: 'SE', zh: '東南' },
    5: { name: '中', dir: 'C', zh: '中' }, 6: { name: '乾', dir: 'NW', zh: '西北' },
    7: { name: '兌', dir: 'W', zh: '西' }, 8: { name: '艮', dir: 'NE', zh: '東北' },
    9: { name: '離', dir: 'S', zh: '南' }
  };

  // Classic stem-pair formations, keyed heavenStem+earthStem.
  // quality: 2 great auspicious, 1 auspicious, -1 inauspicious, -2 very inauspicious.
  var PATTERNS = {
    '戊丙': { name: '青龍返首', quality: 2 }, '丙戊': { name: '飛鳥跌穴', quality: 2 },
    '乙辛': { name: '青龍逃走', quality: -2 }, '辛乙': { name: '白虎猖狂', quality: -2 },
    '丙庚': { name: '熒入太白', quality: -1 }, '庚丙': { name: '太白入熒', quality: -1 },
    '丁癸': { name: '朱雀投江', quality: -2 }, '癸丁': { name: '螣蛇夭矯', quality: -1 },
    '庚癸': { name: '大格', quality: -1 }, '庚壬': { name: '小格', quality: -1 },
    '庚己': { name: '刑格', quality: -1 }, '庚庚': { name: '戰格', quality: -1 },
    '庚戊': { name: '伏宮格', quality: -1 }, '戊庚': { name: '飛宮格', quality: -1 },
    '乙庚': { name: '日奇被刑', quality: -1 }, '辛壬': { name: '凶蛇入獄', quality: -1 },
    '壬辛': { name: '螣蛇相纏', quality: -1 }, '壬癸': { name: '幼女姦淫', quality: -1 },
    '癸壬': { name: '沖天奔地', quality: 0 }, '癸癸': { name: '天網四張', quality: -2 },
    '乙丙': { name: '奇儀順遂', quality: 1 }, '乙丁': { name: '奇儀相佐', quality: 1 },
    '丙丁': { name: '星隨月轉', quality: 1 }, '丁丙': { name: '星奇朱雀', quality: 1 },
    '丙乙': { name: '日月並行', quality: 1 }, '丁乙': { name: '奇儀相合', quality: 1 }
  };

  var GOOD_DOORS = { '開門': 1, '休門': 1, '生門': 1 };
  var BAD_DOORS = { '死門': 1, '驚門': 1, '傷門': 1 };
  var GOOD_STARS = { '天心': 1, '天任': 1, '天輔': 1, '天禽': 1 };
  var BAD_STARS = { '天蓬': 1, '天芮': 1, '天柱': 1 };
  var GOOD_GODS = { '值符': 1, '九天': 1, '九地': 1, '太陰': 1, '六合': 1 };

  function wrap9(n) { n = ((n - 1) % 9 + 9) % 9 + 1; return n; }
  function ganZhiIndex(gz) {
    var g = STEMS.indexOf(gz.charAt(0));
    var z = BRANCHES.indexOf(gz.charAt(1));
    for (var i = 0; i < 60; i++) { if (i % 10 === g && i % 12 === z) return i; }
    return -1;
  }
  function normTerm(name) { return TERM_ALIAS[name] || name; }

  // 拆補: walk back from the day pillar to its leader day (符頭, stem 甲 or 己);
  // the leader's branch picks the yuan: 子午卯酉 upper, 寅申巳亥 middle, 辰戌丑未 lower.
  function findYuan(dayGz) {
    var idx = ganZhiIndex(dayGz);
    var back = STEMS.indexOf(dayGz.charAt(0)) % 5;
    var fuTou = (idx - back + 60) % 60;
    var branch = BRANCHES[fuTou % 12];
    var yuan;
    if ('子午卯酉'.indexOf(branch) >= 0) yuan = 0;
    else if ('寅申巳亥'.indexOf(branch) >= 0) yuan = 1;
    else yuan = 2;
    return { yuan: yuan, fuTou: STEMS[fuTou % 10] + branch };
  }

  function compute(date) {
    var solar = Solar.fromDate(date);
    var lunar = solar.getLunar();

    // Day pillar with the 23:00 boundary (夜子時 counts to the next day),
    // matching how lunar.js derives the hour stem.
    var dayGz = lunar.getDayInGanZhiExact();
    var timeGz = lunar.getTimeInGanZhi();
    var timeGan = timeGz.charAt(0);
    var timeZhi = timeGz.charAt(1);

    var prevJq = lunar.getPrevJieQi();
    var nextJq = lunar.getNextJieQi();
    var term = normTerm(prevJq.getName());
    var isYang = !!YANG_TERMS[term];
    var yuanInfo = findYuan(dayGz);
    var ju = JU_TABLE[term][yuanInfo.yuan];

    // ---- earth plate ----
    var earth = {};        // palace -> stem
    var stemPalace = {};   // stem -> palace
    for (var k = 0; k < 9; k++) {
      var p = wrap9(isYang ? ju + k : ju - k);
      earth[p] = QIYI[k];
      stemPalace[QIYI[k]] = p;
    }

    // ---- chief (值符) and envoy (值使) ----
    var xun = lunar.getTimeXun();
    var xunYi = XUN_YI[xun];
    var fuPalace = stemPalace[xunYi];                       // home palace this hour-xun
    var zhiFuStar = ORIG_STAR[fuPalace];                    // 5 -> 天禽 (rides 坤2)
    var zhiShiDoor = fuPalace === 5 ? ORIG_DOOR[2] : ORIG_DOOR[fuPalace];

    // Chief follows the hour stem (甲 hides behind its xun yi).
    var hourStemForFu = timeGan === '甲' ? xunYi : timeGan;
    var fuTarget = stemPalace[hourStemForFu];
    var fuTargetSeat = fuTarget === 5 ? 2 : fuTarget;       // center lodges in 坤2

    // ---- heaven plate: rotate the star ring ----
    var starAt = {};       // palace -> array of star names
    var heaven = {};       // palace -> array of heaven stems
    var homeSeat = fuPalace === 5 ? 2 : fuPalace;
    var r0 = RING.indexOf(homeSeat);
    var t0 = RING.indexOf(fuTargetSeat);
    for (var i = 0; i < 8; i++) {
      var pal = RING[(t0 + i) % 8];
      var star = STAR_RING[(r0 + i) % 8];
      starAt[pal] = [star];
      // each star carries the earth stem of its home seat;
      // 天禽 rides along with 天芮, carrying the center stem
      heaven[pal] = [earth[RING[STAR_RING.indexOf(star)]]];
      if (star === '天芮') {
        starAt[pal].push('天禽');
        heaven[pal].push(earth[5]);
      }
    }
    starAt[5] = []; heaven[5] = [];

    // ---- envoy door position: advance one palace per hour from the xun start ----
    var steps = (ganZhiIndex(timeGz) - ganZhiIndex(xun) + 60) % 60;
    var shiTarget = wrap9(isYang ? fuPalace + steps : fuPalace - steps);
    var shiSeat = shiTarget === 5 ? 2 : shiTarget;
    var doorAt = {};
    var d0 = DOOR_RING.indexOf(zhiShiDoor);
    var dt0 = RING.indexOf(shiSeat);
    for (var di = 0; di < 8; di++) {
      doorAt[RING[(dt0 + di) % 8]] = DOOR_RING[(d0 + di) % 8];
    }
    doorAt[5] = null;

    // ---- eight gods from the chief's heaven seat ----
    var godAt = {};
    var g0 = RING.indexOf(fuTargetSeat);
    for (var gi = 0; gi < 8; gi++) {
      var gp = isYang ? RING[(g0 + gi) % 8] : RING[(g0 - gi + 8) % 8];
      godAt[gp] = GODS[gi];
    }
    godAt[5] = null;

    // ---- markers ----
    var kong = lunar.getTimeXunKong();                      // two void branches
    var kongPalaces = {};
    for (var ki = 0; ki < kong.length; ki++) kongPalaces[BRANCH_PALACE[kong.charAt(ki)]] = true;
    var horseBranch = HORSE[timeZhi];
    var horsePalace = BRANCH_PALACE[horseBranch];

    var fuYin = fuTargetSeat === homeSeat;                  // heaven plate at rest
    var fanYin = fuTargetSeat === OPPOSITE[homeSeat];       // heaven plate flipped

    // ---- assemble palaces ----
    var palaces = {};
    for (var pn = 1; pn <= 9; pn++) {
      var info = PALACE_INFO[pn];
      var hStems = heaven[pn] || [];
      var flags = {
        kong: !!kongPalaces[pn],
        horse: pn === horsePalace,
        menPo: false, xing: false, tomb: false
      };
      var patterns = [];
      var door = doorAt[pn];
      if (door && CONQUERS[DOOR_ELEM[door]] === PALACE_ELEM[pn]) flags.menPo = true;
      for (var hi = 0; hi < hStems.length; hi++) {
        var hs = hStems[hi];
        if (YI_XING[hs] === pn) flags.xing = true;
        if (TOMB[hs] === pn) flags.tomb = true;
        var pat = PATTERNS[hs + earth[pn]];
        if (pat) patterns.push(pat);
        else if (hs === earth[pn] && pn !== 5) patterns.push({ name: '伏吟', quality: -1 });
        // 庚 meeting the day stem: hidden/flying confrontation
        if (hs === '庚' && earth[pn] === dayGz.charAt(0) && hs !== earth[pn]) patterns.push({ name: '伏干格', quality: -1 });
        if (hs === dayGz.charAt(0) && earth[pn] === '庚' && hs !== earth[pn]) patterns.push({ name: '飛干格', quality: -1 });
      }
      // 玉女守門: 丁 riding the palace where the envoy door sits
      if (door && pn === shiSeat && hStems.indexOf('丁') >= 0) patterns.push({ name: '玉女守門', quality: 1 });
      // three nobles meeting a good door
      if (door && GOOD_DOORS[door]) {
        for (var qi = 0; qi < hStems.length; qi++) {
          if ('乙丙丁'.indexOf(hStems[qi]) >= 0) { patterns.push({ name: '三奇得門', quality: 1 }); break; }
        }
      }

      // score
      var score = 0;
      if (door) score += GOOD_DOORS[door] ? 2 : (BAD_DOORS[door] ? -2 : 0);
      var stars = starAt[pn] || [];
      for (var si = 0; si < stars.length; si++) score += GOOD_STARS[stars[si]] ? 1 : (BAD_STARS[stars[si]] ? -1 : 0);
      var god = godAt[pn];
      if (god) score += GOOD_GODS[god] ? 1 : -1;
      for (var xi = 0; xi < patterns.length; xi++) score += patterns[xi].quality;
      if (flags.menPo) score -= 2;
      if (flags.xing) score -= 1;
      if (flags.tomb) score -= 1;
      if (flags.kong) score -= 1;
      if (flags.horse) score += 0; // horse = movement, neutral by itself

      palaces[pn] = {
        palace: pn, name: info.name, dir: info.dir, dirZh: info.zh,
        earth: earth[pn], heaven: hStems, stars: stars,
        door: door, god: god, flags: flags, patterns: patterns, score: score
      };
    }

    return {
      solar: solar, lunar: lunar,
      pillars: {
        year: lunar.getYearInGanZhiExact(), month: lunar.getMonthInGanZhiExact(),
        day: dayGz, time: timeGz
      },
      term: { name: term, time: prevJq.getSolar().toYmdHms(), next: normTerm(nextJq.getName()), nextTime: nextJq.getSolar().toYmdHms() },
      isYang: isYang, ju: ju, yuan: yuanInfo.yuan, fuTou: yuanInfo.fuTou,
      xun: xun, xunYi: xunYi, xunKong: kong,
      horseBranch: horseBranch, horsePalace: horsePalace,
      zhiFu: { star: zhiFuStar, homePalace: fuPalace, palace: fuTargetSeat },
      zhiShi: { door: zhiShiDoor, homePalace: fuPalace, palace: shiSeat },
      fuYin: fuYin, fanYin: fanYin,
      palaces: palaces
    };
  }

  // Boundaries of the current double-hour (時辰): odd clock hours 23,1,…,21.
  function hourRange(date) {
    var d = new Date(date.getTime());
    var h = d.getHours();
    var startH = h % 2 === 0 ? h - 1 : h;
    var start = new Date(d.getFullYear(), d.getMonth(), d.getDate(), startH, 0, 0, 0);
    if (h === 0) start = new Date(d.getFullYear(), d.getMonth(), d.getDate() - 1, 23, 0, 0, 0);
    var end = new Date(start.getTime() + 2 * 3600 * 1000);
    return { start: start, end: end };
  }

  return {
    compute: compute,
    hourRange: hourRange,
    RING: RING,
    PALACE_INFO: PALACE_INFO,
    STEMS: STEMS,
    BRANCHES: BRANCHES
  };
});
