/* 八字 Bazi Four Pillars chart — pillars, ten gods, five elements,
   10-year luck pillars (大運), yearly luck (流年) and feng shui tips.
   Uses the vendored lunar-javascript engine (window.Solar / Lunar). */
(function () {
  'use strict';

  /* ---------- reference tables ---------- */

  // Heavenly stems: element + polarity (1 = yang, 0 = yin)
  var GAN = {
    '甲': { el: '木', yang: 1, en: 'Jia',  desc: 'towering tree — upright, principled, a natural leader who dislikes bending' },
    '乙': { el: '木', yang: 0, en: 'Yi',   desc: 'climbing vine — adaptable, gentle, persistent, wins by flexibility' },
    '丙': { el: '火', yang: 1, en: 'Bing', desc: 'blazing sun — warm, generous, expressive, born to be seen' },
    '丁': { el: '火', yang: 0, en: 'Ding', desc: 'lamp flame — focused, thoughtful, quietly passionate, illuminating others' },
    '戊': { el: '土', yang: 1, en: 'Wu',   desc: 'mountain earth — steady, reliable, protective, slow to change course' },
    '己': { el: '土', yang: 0, en: 'Ji',   desc: 'garden soil — nurturing, tolerant, resourceful, cultivating growth in others' },
    '庚': { el: '金', yang: 1, en: 'Geng', desc: 'raw metal blade — decisive, tough, justice-minded, thrives on challenge' },
    '辛': { el: '金', yang: 0, en: 'Xin',  desc: 'fine jewellery — refined, precise, image-conscious, sharp beneath the polish' },
    '壬': { el: '水', yang: 1, en: 'Ren',  desc: 'great river — clever, far-flowing, ambitious, hard to contain' },
    '癸': { el: '水', yang: 0, en: 'Gui',  desc: 'morning dew — intuitive, subtle, imaginative, nourishing quietly' }
  };

  // Earthly branches: element, zodiac, double-hour
  var ZHI = {
    '子': { el: '水', animal: '鼠 Rat',     en: 'Zi' },
    '丑': { el: '土', animal: '牛 Ox',      en: 'Chou' },
    '寅': { el: '木', animal: '虎 Tiger',   en: 'Yin' },
    '卯': { el: '木', animal: '兔 Rabbit',  en: 'Mao' },
    '辰': { el: '土', animal: '龍 Dragon',  en: 'Chen' },
    '巳': { el: '火', animal: '蛇 Snake',   en: 'Si' },
    '午': { el: '火', animal: '馬 Horse',   en: 'Wu' },
    '未': { el: '土', animal: '羊 Goat',    en: 'Wei' },
    '申': { el: '金', animal: '猴 Monkey',  en: 'Shen' },
    '酉': { el: '金', animal: '雞 Rooster', en: 'You' },
    '戌': { el: '土', animal: '狗 Dog',     en: 'Xu' },
    '亥': { el: '水', animal: '豬 Pig',     en: 'Hai' }
  };

  // Six clashes (六沖) between branches
  var CLASH = { '子': '午', '午': '子', '丑': '未', '未': '丑', '寅': '申', '申': '寅',
                '卯': '酉', '酉': '卯', '辰': '戌', '戌': '辰', '巳': '亥', '亥': '巳' };

  // Generation cycle 相生: element -> element it produces
  var FEEDS = { '木': '火', '火': '土', '土': '金', '金': '水', '水': '木' };
  // Control cycle 相剋: element -> element it controls
  var CONTROLS = { '木': '土', '土': '水', '水': '火', '火': '金', '金': '木' };

  var EL_EN = { '木': 'Wood', '火': 'Fire', '土': 'Earth', '金': 'Metal', '水': 'Water' };
  var EL_KEYS = ['木', '火', '土', '金', '水'];
  var EL_CLASS = { '木': 'wood', '火': 'fire', '土': 'earth', '金': 'metal', '水': 'water' };

  // Feng shui correspondences per element
  var EL_FS = {
    '木': { colors: '綠、青 green & teal', dir: '東、東南 East & Southeast', nums: '3, 4', boost: 'living plants, wooden furniture, fresh flowers in the east' },
    '火': { colors: '紅、紫 red, orange & purple', dir: '南 South', nums: '9', boost: 'warm lighting, candles, a touch of red décor in the south' },
    '土': { colors: '黃、啡 yellow, beige & brown', dir: '東北、西南 Northeast & Southwest', nums: '2, 5, 8', boost: 'ceramics, crystals, stones, earthenware in the centre' },
    '金': { colors: '白、金 white, gold & silver', dir: '西、西北 West & Northwest', nums: '6, 7', boost: 'metal ornaments, a brass bowl or wind chime in the west' },
    '水': { colors: '黑、藍 black & blue', dir: '北 North', nums: '1', boost: 'a small water feature, aquarium or blue art in the north' }
  };

  // Ten gods — traditional chars + English
  var SHISHEN_EN = {
    '比肩': 'Friend', '劫財': 'Rob Wealth', '食神': 'Eating God', '傷官': 'Hurting Officer',
    '偏財': 'Indirect Wealth', '正財': 'Direct Wealth', '七殺': 'Seven Killings', '正官': 'Direct Officer',
    '偏印': 'Indirect Resource', '正印': 'Direct Resource', '日主': 'Day Master'
  };

  // lunar.js emits simplified chars for a few names — normalise to traditional
  var TO_TRAD = { '财': '財', '伤': '傷', '杀': '殺', '长': '長', '带': '帶', '临': '臨', '绝': '絕', '养': '養', '禄': '祿',
                  '炉': '爐', '剑': '劍', '锋': '鋒', '头': '頭', '涧': '澗', '蜡': '蠟', '杨': '楊', '雳': '靂', '灯': '燈', '驿': '驛', '钗': '釵', '钏': '釧' };
  function trad(s) {
    return String(s).split('').map(function (c) { return TO_TRAD[c] || c; }).join('');
  }

  var DISHI_EN = {
    '長生': 'Birth', '沐浴': 'Bath', '冠帶': 'Youth', '臨官': 'Coming of Age', '帝旺': 'Peak',
    '衰': 'Decline', '病': 'Sickness', '死': 'Death', '墓': 'Tomb', '絕': 'Severed', '胎': 'Conception', '養': 'Nurture'
  };

  /* ---------- helpers ---------- */

  function $(id) { return document.getElementById(id); }

  function esc(s) {
    return String(s).replace(/[&<>"]/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c];
    });
  }

  // Ten god of a stem relative to the day master stem
  function tenGod(dm, gan) {
    if (gan === dm) return '比肩';
    var a = GAN[dm], b = GAN[gan];
    var same = a.yang === b.yang;
    if (b.el === a.el) return same ? '比肩' : '劫財';
    if (FEEDS[a.el] === b.el) return same ? '食神' : '傷官';
    if (FEEDS[b.el] === a.el) return same ? '偏印' : '正印';
    if (CONTROLS[a.el] === b.el) return same ? '偏財' : '正財';
    return same ? '七殺' : '正官'; // b controls a
  }

  function ganSpan(g) {
    return '<b class="el-' + EL_CLASS[GAN[g].el] + '">' + g + '</b>';
  }
  function zhiSpan(z) {
    return '<b class="el-' + EL_CLASS[ZHI[z].el] + '">' + z + '</b>';
  }
  function elTag(el) {
    return '<span class="el-tag el-' + EL_CLASS[el] + '">' + el + ' ' + EL_EN[el] + '</span>';
  }

  /* ---------- chart computation ---------- */

  function computeChart(y, m, d, hh, mm, gender, hourUnknown) {
    var solar = Solar.fromYmdHms(y, m, d, hourUnknown ? 12 : hh, hourUnknown ? 0 : mm, 0);
    var lunar = solar.getLunar();
    var ec = lunar.getEightChar();

    var dm = ec.getDayGan();

    var pillars = [
      { key: 'year',  zh: '年柱', gan: ec.getYearGan(),  zhi: ec.getYearZhi(),  hide: ec.getYearHideGan(),  nayin: trad(ec.getYearNaYin()),  dishi: trad(ec.getYearDiShi()) },
      { key: 'month', zh: '月柱', gan: ec.getMonthGan(), zhi: ec.getMonthZhi(), hide: ec.getMonthHideGan(), nayin: trad(ec.getMonthNaYin()), dishi: trad(ec.getMonthDiShi()) },
      { key: 'day',   zh: '日柱', gan: ec.getDayGan(),   zhi: ec.getDayZhi(),   hide: ec.getDayHideGan(),   nayin: trad(ec.getDayNaYin()),   dishi: trad(ec.getDayDiShi()) },
      { key: 'hour',  zh: '時柱', gan: ec.getTimeGan(),  zhi: ec.getTimeZhi(),  hide: ec.getTimeHideGan(),  nayin: trad(ec.getTimeNaYin()),  dishi: trad(ec.getTimeDiShi()) }
    ];
    if (hourUnknown) pillars[3].unknown = true;

    // Five-element tally: visible stems 1.0 each; branch hidden stems
    // main qi 1.0, others 0.5. Month branch (the season) counts double.
    var tally = { '木': 0, '火': 0, '土': 0, '金': 0, '水': 0 };
    var support = 0, total = 0;
    var dmEl = GAN[dm].el;
    var helpers = {}; // elements that support the day master
    helpers[dmEl] = true;
    for (var k in FEEDS) { if (FEEDS[k] === dmEl) helpers[k] = true; }

    pillars.forEach(function (p) {
      if (p.unknown) return;
      var seasonBoost = p.key === 'month' ? 2 : 1;
      var units = [{ el: GAN[p.gan].el, w: 1 }];
      p.hide.forEach(function (hg, i) {
        units.push({ el: GAN[hg].el, w: (i === 0 ? 1 : 0.5) * seasonBoost });
      });
      units.forEach(function (u) {
        tally[u.el] += u.w;
        total += u.w;
        if (helpers[u.el]) support += u.w;
      });
    });

    var ratio = total ? support / total : 0.5;
    var strong = ratio >= 0.5;

    // Favorable elements (喜用神, simplified school):
    // weak day master → resource + peer; strong → drain / wealth / control.
    var resourceEl = null;
    for (var e in FEEDS) { if (FEEDS[e] === dmEl) resourceEl = e; }
    var outputEl = FEEDS[dmEl];
    var wealthEl = CONTROLS[dmEl];
    var officerEl = null;
    for (var e2 in CONTROLS) { if (CONTROLS[e2] === dmEl) officerEl = e2; }

    var favorable = strong ? [outputEl, wealthEl, officerEl] : [resourceEl, dmEl];
    var unfavorable = strong ? [resourceEl, dmEl] : [outputEl, wealthEl, officerEl];

    return {
      solar: solar, lunar: lunar, ec: ec, dm: dm, dmEl: dmEl,
      pillars: pillars, tally: tally, total: total, ratio: ratio, strong: strong,
      favorable: favorable, unfavorable: unfavorable,
      gender: gender, hourUnknown: hourUnknown,
      yearZhi: ec.getYearZhi()
    };
  }

  /* ---------- rendering ---------- */

  var chart = null;       // current computed chart
  var daYunList = [];     // luck pillars
  var selectedYun = -1;

  function renderAll() {
    $('results').hidden = false;
    renderChartTable();
    renderDayMaster();
    renderElements();
    renderLuck();
    renderFengShui(new Date().getFullYear());
    $('results').scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  function renderChartTable() {
    var c = chart;
    var lunar = c.lunar;
    $('chart-birth-line').textContent =
      c.solar.toYmd() + (c.hourUnknown ? ' (hour unknown)' : ' ' + pad2(c.solar.getHour()) + ':' + pad2(c.solar.getMinute())) +
      ' · 農曆 ' + lunar.getYearInChinese() + '年' + lunar.getMonthInChinese() + '月' + lunar.getDayInChinese() +
      ' · ' + (c.gender === 1 ? '男命 Male chart' : '女命 Female chart');

    var rows = {
      god: ['<th scope="row">十神<span>Ten God</span></th>'],
      gan: ['<th scope="row">天干<span>Stem</span></th>'],
      zhi: ['<th scope="row">地支<span>Branch</span></th>'],
      hide: ['<th scope="row">藏干<span>Hidden Stems</span></th>'],
      nayin: ['<th scope="row">納音<span>Na Yin</span></th>'],
      dishi: ['<th scope="row">十二長生<span>Life Stage</span></th>']
    };

    c.pillars.forEach(function (p) {
      if (p.unknown) {
        rows.god.push('<td class="unk">—</td>');
        rows.gan.push('<td class="unk">？</td>');
        rows.zhi.push('<td class="unk">？</td>');
        rows.hide.push('<td class="unk">—</td>');
        rows.nayin.push('<td class="unk">—</td>');
        rows.dishi.push('<td class="unk">—</td>');
        return;
      }
      var god = p.key === 'day' ? '日主' : tenGod(c.dm, p.gan);
      rows.god.push('<td><span class="god' + (p.key === 'day' ? ' god--dm' : '') + '">' + god + '<i>' + SHISHEN_EN[god] + '</i></span></td>');
      rows.gan.push('<td class="big">' + ganSpan(p.gan) + '<span class="sub">' + GAN[p.gan].en + ' · ' + GAN[p.gan].el + '</span></td>');
      rows.zhi.push('<td class="big">' + zhiSpan(p.zhi) + '<span class="sub">' + ZHI[p.zhi].animal + '</span></td>');
      rows.hide.push('<td><div class="hides">' + p.hide.map(function (hg) {
        var g = tenGod(c.dm, hg);
        return '<span class="hide-item">' + ganSpan(hg) + '<i>' + g + '</i></span>';
      }).join('') + '</div></td>');
      rows.nayin.push('<td class="nayin">' + esc(p.nayin) + '</td>');
      rows.dishi.push('<td class="dishi">' + p.dishi + '<span class="sub">' + (DISHI_EN[p.dishi] || '') + '</span></td>');
    });

    $('chart-body').innerHTML =
      '<tr>' + rows.god.join('') + '</tr>' +
      '<tr>' + rows.gan.join('') + '</tr>' +
      '<tr>' + rows.zhi.join('') + '</tr>' +
      '<tr>' + rows.hide.join('') + '</tr>' +
      '<tr>' + rows.nayin.join('') + '</tr>' +
      '<tr>' + rows.dishi.join('') + '</tr>';

    var ec = chart.ec;
    $('chart-extra').innerHTML =
      '命宮 Life Palace <b>' + esc(ec.getMingGong()) + '</b> · 身宮 Body Palace <b>' + esc(ec.getShenGong()) + '</b> · 胎元 Conception <b>' + esc(ec.getTaiYuan()) + '</b>' +
      ' · 生肖 Zodiac <b>' + ZHI[chart.yearZhi].animal + '</b>';
  }

  function renderDayMaster() {
    var c = chart;
    var info = GAN[c.dm];
    $('dm-box').innerHTML =
      '<div class="dm__char el-' + EL_CLASS[c.dmEl] + '">' + c.dm + '</div>' +
      '<div class="dm__body">' +
        '<p class="dm__title">' + c.dm + ' ' + info.en + ' — ' + (info.yang ? '陽' : '陰') + c.dmEl + ' ' + (info.yang ? 'Yang' : 'Yin') + ' ' + EL_EN[c.dmEl] + '</p>' +
        '<p class="dm__desc">' + esc(info.desc) + '.</p>' +
        '<p class="dm__strength">日主' + (c.strong ? '偏強' : '偏弱') + ' — your Day Master reads as <b>' + (c.strong ? 'strong' : 'weak') + '</b> (' + Math.round(c.ratio * 100) + '% of the chart supports it).</p>' +
      '</div>';
  }

  function renderElements() {
    var c = chart;
    var max = 0;
    EL_KEYS.forEach(function (e) { if (c.tally[e] > max) max = c.tally[e]; });
    $('wx-bars').innerHTML = EL_KEYS.map(function (e) {
      var v = c.tally[e];
      var pct = max ? Math.max(4, Math.round(v / max * 100)) : 4;
      return '<div class="wx-row">' +
        '<span class="wx-label el-' + EL_CLASS[e] + '">' + e + ' <i>' + EL_EN[e] + '</i></span>' +
        '<span class="wx-track"><span class="wx-fill el-bg-' + EL_CLASS[e] + '" style="width:' + pct + '%"></span></span>' +
        '<span class="wx-val">' + (Math.round(v * 10) / 10) + '</span>' +
      '</div>';
    }).join('');

    $('wx-verdict').innerHTML =
      '<p><b>喜用神 Favorable:</b> ' + c.favorable.map(elTag).join(' ') + '</p>' +
      '<p><b>忌神 Unfavorable:</b> ' + c.unfavorable.map(elTag).join(' ') + '</p>' +
      '<p class="wx-note">A ' + (c.strong ? 'strong' : 'weak') + ' Day Master welcomes elements that ' +
      (c.strong ? 'drain and discipline it, so its power finds an outlet' : 'feed and stand beside it, so it is not overwhelmed') + '.</p>';
  }

  function renderLuck() {
    var c = chart;
    var yun = c.ec.getYun(c.gender);
    var start = yun.getStartSolar();
    $('luck-start').textContent =
      '起運 Luck cycle begins ' + yun.getStartYear() + ' year(s) ' + yun.getStartMonth() + ' month(s) ' + yun.getStartDay() +
      ' day(s) after birth — around ' + start.toYmd() + '. ' +
      (c.gender === 1 ? 'Male' : 'Female') + ' chart runs ' + (isForward(c) ? 'forward 順行' : 'backward 逆行') + ' through the cycle.';

    daYunList = yun.getDaYun();
    var nowYear = new Date().getFullYear();

    selectedYun = -1;
    var html = daYunList.map(function (d, i) {
      var gz = d.getGanZhi();
      var startY = d.getStartYear(), endY = d.getEndYear();
      var current = nowYear >= startY && nowYear <= endY;
      if (current) selectedYun = i;
      var inner;
      if (!gz) {
        inner = '<span class="yun__gz yun__gz--early">童限</span><span class="yun__god">Early Years</span>';
      } else {
        var g = gz.charAt(0), z = gz.charAt(1);
        var god = tenGod(c.dm, g);
        inner = '<span class="yun__gz">' + ganSpan(g) + zhiSpan(z) + '</span>' +
                '<span class="yun__god">' + god + ' ' + SHISHEN_EN[god] + '</span>';
      }
      return '<button type="button" role="tab" class="yun" data-i="' + i + '" aria-selected="' + current + '">' +
        '<span class="yun__age">' + d.getStartAge() + '–' + d.getEndAge() + ' 歲</span>' +
        inner +
        '<span class="yun__years">' + startY + '–' + endY + '</span>' +
        (current ? '<span class="yun__now">現行 NOW</span>' : '') +
      '</button>';
    }).join('');
    if (selectedYun < 0) selectedYun = daYunList.length > 1 ? 1 : 0;
    $('luck-strip').innerHTML = html;

    Array.prototype.forEach.call($('luck-strip').children, function (btn) {
      btn.addEventListener('click', function () {
        selectYun(parseInt(btn.dataset.i, 10));
      });
    });
    selectYun(selectedYun);
  }

  function isForward(c) {
    // Yang-year male and yin-year female run forward
    var yangYear = GAN[c.ec.getYearGan()].yang === 1;
    return (c.gender === 1) === yangYear;
  }

  function selectYun(i) {
    selectedYun = i;
    Array.prototype.forEach.call($('luck-strip').children, function (btn) {
      var on = parseInt(btn.dataset.i, 10) === i;
      btn.setAttribute('aria-selected', String(on));
      btn.classList.toggle('yun--sel', on);
    });
    renderYears(daYunList[i]);
  }

  function renderYears(dy) {
    var c = chart;
    var gz = dy.getGanZhi();
    $('years-decade').textContent = gz
      ? ' · ' + gz + ' 大運 ' + dy.getStartYear() + '–' + dy.getEndYear()
      : ' · 童限 Early Years ' + dy.getStartYear() + '–' + dy.getEndYear();

    var nowYear = new Date().getFullYear();
    var list = dy.getLiuNian();
    $('years').innerHTML = list.map(function (ln) {
      var ygz = ln.getGanZhi();
      var g = ygz.charAt(0), z = ygz.charAt(1);
      var r = rateYear(c, g, z);
      var god = tenGod(c.dm, g);
      return '<div class="ynode' + (ln.getYear() === nowYear ? ' ynode--now' : '') + '">' +
        '<span class="ynode__year">' + ln.getYear() + '</span>' +
        '<span class="ynode__gz">' + ganSpan(g) + zhiSpan(z) + '</span>' +
        '<span class="ynode__animal">' + ZHI[z].animal + '</span>' +
        '<span class="ynode__god">' + god + ' ' + SHISHEN_EN[god] + '</span>' +
        '<span class="yrate yrate--' + r.cls + '">' + r.zh + ' ' + r.en + '</span>' +
        (r.taisui ? '<span class="ynode__taisui">' + r.taisui + '</span>' : '') +
        '<span class="ynode__age">' + ln.getAge() + ' 歲</span>' +
      '</div>';
    }).join('');
  }

  // Score a year's stem+branch against favorable/unfavorable elements,
  // plus Tai Sui interactions with the birth-year branch.
  function rateYear(c, g, z) {
    var score = 0;
    [GAN[g].el, ZHI[z].el].forEach(function (e) {
      if (c.favorable.indexOf(e) >= 0) score += 1;
      if (c.unfavorable.indexOf(e) >= 0) score -= 1;
    });
    var taisui = '';
    if (z === c.yearZhi) { score -= 1; taisui = '值太歲 Tai Sui year'; }
    else if (CLASH[z] === c.yearZhi) { score -= 1; taisui = '沖太歲 Clashes Tai Sui'; }
    if (score >= 2) return { cls: 'best', zh: '大吉', en: 'Excellent', taisui: taisui };
    if (score === 1) return { cls: 'good', zh: '吉', en: 'Good', taisui: taisui };
    if (score === 0) return { cls: 'flat', zh: '平', en: 'Steady', taisui: taisui };
    if (score === -1) return { cls: 'care', zh: '慎', en: 'Take care', taisui: taisui };
    return { cls: 'bad', zh: '凶', en: 'Challenging', taisui: taisui };
  }

  function renderFengShui(year) {
    var c = chart;
    // Year pillar of the given calendar year (Lichun-based via lunar.js)
    var mid = Solar.fromYmdHms(year, 7, 1, 12, 0, 0).getLunar();
    var ygz = mid.getYearInGanZhiByLiChun();
    var g = ygz.charAt(0), z = ygz.charAt(1);
    $('fs-year-note').textContent = ' · ' + year + ' ' + ygz + ' ' + ZHI[z].animal.split(' ')[1] + ' Year';

    var cards = c.favorable.map(function (e, i) {
      var fs = EL_FS[e];
      return '<div class="fs-card">' +
        '<h3>' + elTag(e) + (i === 0 ? '<span class="fs-primary">主用神 primary</span>' : '') + '</h3>' +
        '<p><b>幸運色 Colors:</b> ' + fs.colors + '</p>' +
        '<p><b>吉方 Directions:</b> ' + fs.dir + '</p>' +
        '<p><b>幸運數字 Numbers:</b> ' + fs.nums + '</p>' +
        '<p><b>開運 Enhancer:</b> ' + fs.boost + '.</p>' +
      '</div>';
    }).join('');
    $('fs-grid').innerHTML = cards;

    var r = rateYear(c, g, z);
    var msg = '<p>今年流年 <b>' + ygz + '</b> (' + GAN[g].el + '+' + ZHI[z].el + ') rates <b class="yrate yrate--' + r.cls + '">' + r.zh + ' ' + r.en + '</b> for your chart.</p>';
    if (z === c.yearZhi) {
      msg += '<p class="fs-warn">⚠ 犯太歲 — this year\'s branch matches your birth-year branch (' + c.yearZhi + '). Tradition advises keeping a low profile, honouring Tai Sui, and wearing your favorable colors as a remedy.</p>';
    } else if (CLASH[z] === c.yearZhi) {
      msg += '<p class="fs-warn">⚠ 沖太歲 — this year\'s branch (' + z + ') clashes your birth-year branch (' + c.yearZhi + '). Avoid major gambles; travel and steady routines help ride out the turbulence.</p>';
    } else {
      msg += '<p>No Tai Sui affliction this year — a clear runway to activate your lucky directions above.</p>';
    }
    msg += '<p class="fs-avoid"><b>避忌 Ease off:</b> ' + c.unfavorable.map(function (e) {
      return EL_FS[e].colors.split(' ')[0] + ' (' + EL_EN[e] + ')';
    }).join(', ') + ' — keep these colors as accents, not themes.</p>';
    $('fs-taisui').innerHTML = msg;
  }

  function pad2(n) { return (n < 10 ? '0' : '') + n; }

  /* ---------- form wiring ---------- */

  var STORE_KEY = 'bazi-birth';

  function onSubmit(ev) {
    if (ev) ev.preventDefault();
    var dateStr = $('birth-date').value;
    if (!dateStr) return;
    var timeStr = $('birth-time').value || '12:00';
    var unknown = $('hour-unknown').checked;
    var gender = parseInt(document.querySelector('input[name="gender"]:checked').value, 10);
    var dp = dateStr.split('-').map(Number);
    var tp = timeStr.split(':').map(Number);
    try {
      chart = computeChart(dp[0], dp[1], dp[2], tp[0], tp[1], gender, unknown);
    } catch (e) {
      alert('Could not compute a chart for that date: ' + e.message);
      return;
    }
    try {
      localStorage.setItem(STORE_KEY, JSON.stringify({ d: dateStr, t: timeStr, u: unknown, g: gender }));
    } catch (e) { /* private mode — fine */ }
    renderAll();
  }

  function restore() {
    var saved = null;
    try { saved = JSON.parse(localStorage.getItem(STORE_KEY)); } catch (e) { /* ignore */ }
    if (!saved || !saved.d) return;
    $('birth-date').value = saved.d;
    $('birth-time').value = saved.t || '12:00';
    $('hour-unknown').checked = !!saved.u;
    var radio = document.querySelector('input[name="gender"][value="' + (saved.g === 0 ? 0 : 1) + '"]');
    if (radio) radio.checked = true;
    onSubmit();
  }

  $('birth-form').addEventListener('submit', onSubmit);
  $('hour-unknown').addEventListener('change', function () {
    $('birth-time').disabled = this.checked;
  });
  restore();
})();
