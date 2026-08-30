/*
 * Qi Men Hourly Chart — UI. Renders the nine-palace plate for the
 * viewed moment, keeps it live (auto-recasts when the double-hour
 * turns), and derives the warcraft/strategy panel from the chart.
 */
(function () {
  'use strict';

  var GRID_ROWS = [[4, 9, 2], [3, 5, 7], [8, 1, 6]]; // south on top
  var $ = function (id) { return document.getElementById(id); };

  var state = {
    viewDate: new Date(),
    live: true,
    chart: null,
    selected: null
  };

  function dirLabel(p) { return p.dirZh + ' ' + p.dir; }

  function verdict(score) {
    if (score >= 4) return { zh: '大吉', en: 'Very auspicious', cls: 'v-great' };
    if (score >= 2) return { zh: '吉', en: 'Auspicious', cls: 'v-good' };
    if (score >= 0) return { zh: '平', en: 'Neutral', cls: 'v-flat' };
    if (score >= -2) return { zh: '凶', en: 'Adverse', cls: 'v-bad' };
    return { zh: '大凶', en: 'Very adverse', cls: 'v-awful' };
  }

  function two(n) { return (n < 10 ? '0' : '') + n; }
  function fmtDate(d) {
    return d.getFullYear() + '-' + two(d.getMonth() + 1) + '-' + two(d.getDate());
  }
  function fmtTime(d) { return two(d.getHours()) + ':' + two(d.getMinutes()); }
  function fmtHM(d) { return two(d.getHours()) + ':' + two(d.getMinutes()); }

  // ---------- rendering ----------

  function render() {
    var c = QimenEngine.compute(state.viewDate);
    state.chart = c;

    $('clock-date').textContent = fmtDate(state.viewDate);
    $('clock-time').textContent = fmtTime(state.viewDate);
    $('live-badge').hidden = !state.live;

    $('p-year').textContent = c.pillars.year;
    $('p-month').textContent = c.pillars.month;
    $('p-day').textContent = c.pillars.day;
    $('p-time').textContent = c.pillars.time;

    var dun = c.isYang ? '陽遁' : '陰遁';
    var yuanZh = ['上元', '中元', '下元'][c.yuan];
    $('ju-line').innerHTML =
      '<b>' + c.term.name + yuanZh + ' · ' + dun + c.ju + '局</b>' +
      ' <span class="soft">' + (c.isYang ? 'Yang' : 'Yin') + ' Escape, Chart ' + c.ju +
      ' · ' + c.term.name + ' ' + yuanZh + '</span>' +
      '<br><span class="soft">旬首 ' + c.xun + '（遁' + c.xunYi + '） · 旬空 ' + c.xunKong +
      ' · 馬星在' + QimenEngine.PALACE_INFO[c.horsePalace].zh + '</span>';

    var fu = c.palaces[c.zhiFu.palace];
    var shi = c.palaces[c.zhiShi.palace];
    $('fu-line').innerHTML =
      '值符 <b>' + c.zhiFu.star + '</b> 落' + fu.name + wPal(c.zhiFu.palace) + '宮（' + dirLabel(fu) + '）　' +
      '值使 <b>' + c.zhiShi.door + '</b> 落' + shi.name + wPal(c.zhiShi.palace) + '宮（' + dirLabel(shi) + '）' +
      '<br><span class="soft">Chief ' + c.zhiFu.star + ' in ' + dirLabel(fu) +
      ' · Envoy ' + c.zhiShi.door + ' in ' + dirLabel(shi) + '</span>' +
      (c.fuYin ? '<br><b class="warn">伏吟局 Hidden-Groan chart — 宜守不宜動 hold, don’t move.</b>' : '') +
      (c.fanYin ? '<br><b class="warn">反吟局 Overturned chart — 宜退不宜進 withdraw, don’t advance.</b>' : '');

    var range = QimenEngine.hourRange(state.viewDate);
    $('hour-line').innerHTML =
      '本盤時辰 ' + c.pillars.time.charAt(1) + '時 ' + fmtHM(range.start) + '–' + fmtHM(range.end) +
      '　<span class="soft" id="countdown"></span>';

    renderPlate(c);
    renderStrategy(c);
    renderDoorUses(c);
    if (state.selected) renderDetail(state.selected);
    updateCountdown();
  }

  function wPal(n) { return '一二三四五六七八九'.charAt(n - 1); }

  function badge(txt, cls) { return '<span class="badge ' + (cls || '') + '">' + txt + '</span>'; }

  function renderPlate(c) {
    var html = '';
    GRID_ROWS.forEach(function (row) {
      row.forEach(function (pn) {
        var p = c.palaces[pn];
        var v = verdict(p.score);
        var cls = ['cell', v.cls];
        if (pn === c.zhiFu.palace) cls.push('cell--fu');
        if (pn === c.zhiShi.palace) cls.push('cell--shi');
        if (pn === 5) cls.push('cell--center');
        if (state.selected === pn) cls.push('cell--sel');

        var badges = '';
        if (p.flags.kong) badges += badge('空', 'b-flat');
        if (p.flags.horse) badges += badge('馬', 'b-move');
        if (p.flags.tomb) badges += badge('墓', 'b-bad');
        if (p.flags.xing) badges += badge('刑', 'b-bad');
        if (p.flags.menPo) badges += badge('迫', 'b-bad');
        p.patterns.forEach(function (pat) {
          badges += badge(pat.name, pat.quality > 0 ? 'b-good' : (pat.quality < 0 ? 'b-bad' : 'b-flat'));
        });

        if (pn === 5) {
          html += '<button type="button" class="' + cls.join(' ') + '" data-p="5">' +
            '<span class="cell__pal">中五</span>' +
            '<span class="cell__center-stem">' + p.earth + '</span>' +
            '<span class="cell__center-note">寄坤二宮<br>lodges in SW</span>' +
            '</button>';
          return;
        }

        html += '<button type="button" class="' + cls.join(' ') + '" data-p="' + pn + '">' +
          '<span class="cell__pal">' + p.name + wPal(pn) + ' · ' + dirLabel(p) + '</span>' +
          '<span class="cell__god">' + (p.god || '') + '</span>' +
          '<span class="cell__line"><span class="cell__star">' + p.stars.join('') + '</span>' +
            '<b class="cell__stem">' + p.heaven.join('') + '</b></span>' +
          '<span class="cell__line"><span class="cell__door">' + (p.door || '') + '</span>' +
            '<b class="cell__stem cell__stem--earth">' + p.earth + '</b></span>' +
          '<span class="cell__badges">' + badges + '</span>' +
          '<span class="cell__verdict">' + v.zh + '</span>' +
          '</button>';
      });
    });
    $('plate').innerHTML = html;
    Array.prototype.forEach.call($('plate').querySelectorAll('.cell'), function (el) {
      el.addEventListener('click', function () {
        state.selected = parseInt(el.getAttribute('data-p'), 10);
        render();
        $('detail').scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      });
    });
  }

  function line(zh, en) {
    return '<p class="d-zh">' + zh + '</p><p class="d-en">' + en + '</p>';
  }

  function renderDetail(pn) {
    var c = state.chart;
    var p = c.palaces[pn];
    var el = $('detail');
    el.hidden = false;
    var v = verdict(p.score);
    var html = '<h2><span class="h-zh">' + (pn === 5 ? '中五宮' : p.name + wPal(pn) + '宮') + '</span>' +
      dirLabel(p) + ' Palace · <span class="' + v.cls + '">' + v.zh + ' ' + v.en + '</span></h2>';

    if (pn === 5) {
      html += line('中宮天禽所居，寄於坤二。地盤干：' + p.earth + '。',
        'The center palace lodges its affairs in the SW (Kun 2). Earth stem: ' + p.earth + '.');
      el.innerHTML = html;
      return;
    }

    if (p.god) {
      var g = QimenData.gods[p.god];
      html += line('【八神・' + p.god + '】' + g.zh, '<b>' + g.en + '</b> — ' + g.enDesc);
    }
    p.stars.forEach(function (s) {
      var st = QimenData.stars[s];
      html += line('【九星・' + s + '】' + st.zh, '<b>' + st.en + '</b> — ' + st.enDesc);
    });
    if (p.door) {
      var d = QimenData.doors[p.door];
      html += line('【八門・' + p.door + '】' + d.zh + ' ' + d.warZh,
        '<b>' + d.en + '</b> — ' + d.enDesc + ' ' + d.warEn);
    }
    html += line('【天盤干】' + p.heaven.join('、') + '　【地盤干】' + p.earth,
      'Heaven stem(s): ' + p.heaven.join(', ') + ' · Earth stem: ' + p.earth);

    p.patterns.forEach(function (pat) {
      var info = QimenData.patterns[pat.name];
      if (info) html += line('【格局・' + pat.name + '】' + info.zh, '<b>' + info.en + '</b> — ' + info.enDesc);
    });
    ['kong', 'horse', 'tomb', 'xing', 'menPo'].forEach(function (f) {
      if (p.flags[f]) {
        var fi = QimenData.flags[f];
        html += line('【' + fi.zh + '】' + fi.desc, '<b>' + fi.en + '</b> — ' + fi.enDesc);
      }
    });
    el.innerHTML = html;
  }

  // ---------- strategy ----------

  function findDoorPalace(c, door) {
    for (var pn = 1; pn <= 9; pn++) {
      if (c.palaces[pn].door === door) return c.palaces[pn];
    }
    return null;
  }
  function findGodPalace(c, god) {
    for (var pn = 1; pn <= 9; pn++) {
      if (c.palaces[pn].god === god) return c.palaces[pn];
    }
    return null;
  }
  function findHeavenStem(c, stem) {
    for (var pn = 1; pn <= 9; pn++) {
      if (c.palaces[pn].heaven.indexOf(stem) >= 0) return c.palaces[pn];
    }
    return null;
  }

  function renderStrategy(c) {
    var fu = c.palaces[c.zhiFu.palace];
    var outer = QimenEngine.RING.map(function (pn) { return c.palaces[pn]; });

    // best palace to act toward: good door first, then score
    var GOOD = { '開門': 1, '休門': 1, '生門': 1 };
    var candidates = outer.filter(function (p) { return GOOD[p.door]; });
    candidates.sort(function (a, b) { return b.score - a.score; });
    var best = candidates[0];

    // the Chief's palace scatters all evils (值符所臨，百惡消散) —
    // never list it among the directions to avoid
    var notChief = function (p) { return p && p.palace !== c.zhiFu.palace ? p : null; };
    var worst = notChief(outer.slice().sort(function (a, b) { return a.score - b.score; })[0]) ||
      outer.slice().sort(function (a, b) { return a.score - b.score; })[1];
    var geng = notChief(findHeavenStem(c, '庚'));
    var tiger = notChief(findGodPalace(c, '白虎'));
    var sheng = findDoorPalace(c, '生門');
    var si = findDoorPalace(c, '死門');

    var html = '';
    html += block('主帥所在 The Chief’s Seat',
      '值符' + c.zhiFu.star + '鎮' + dirLabel(fu) + '。此方諸惡不侵，宜倚為靠山；切不可犯其鋒，勿向此方興訟爭鬥。',
      'The Chief (' + c.zhiFu.star + ') holds the ' + dirLabel(fu) + '. Evils cannot touch this direction — put it at your back as your patron; never quarrel or strike toward it.');

    if (c.fuYin) {
      html += block('伏吟 · 按兵不動 Hold Fast',
        '伏吟之局，天地同聲。利屯守、修備、故地重遊；不利首事新謀。',
        'A Hidden-Groan chart: heaven rests on earth. Fortify, repair, revisit old ground — do not launch anything new this hour.');
    } else if (c.fanYin) {
      html += block('反吟 · 以退為進 Withdraw to Advance',
        '反吟之局，事多反覆。宜退守回師、翻舊案；不宜強進。',
        'An Overturned chart: matters reverse themselves. Fall back, revisit and unwind old cases; forcing forward invites reversal.');
    }

    if (best) {
      var bd = QimenData.doors[best.door];
      html += block('出擊之方 Direction of Action',
        '本時辰宜從<b>' + dirLabel(best) + '</b>（' + best.door + (best.stars.length ? '、' + best.stars.join('') : '') + (best.god ? '、' + best.god : '') + '）而動：' + bd.warZh,
        'Act toward the <b>' + dirLabel(best) + '</b> this hour — it carries the ' + bd.en + (best.god ? ' under ' + QimenData.gods[best.god].en : '') + '. ' + bd.warEn);
    }

    if (sheng && si) {
      html += block('背生擊死 Back to Life, Strike at Death',
        '生門在<b>' + dirLabel(sheng) + '</b>，死門在<b>' + dirLabel(si) + '</b>。臨陣對壘、談判競逐，背' + sheng.dirZh + '而面' + si.dirZh + '，先立於不敗。',
        'The Life Door lies ' + dirLabel(sheng) + ', the Death Door ' + dirLabel(si) + '. In any contest or negotiation, stand with the ' + sheng.dir + ' at your back and face the ' + si.dir + ' — the classical posture that cannot be routed.');
    }

    var avoid = [];
    if (worst) avoid.push('<b>' + dirLabel(worst) + '</b>（' + [worst.god, worst.stars.join(''), worst.door].filter(Boolean).join('、') + '）');
    if (geng && geng !== worst) avoid.push('<b>' + dirLabel(geng) + '</b>（太白庚金所臨）');
    if (tiger && tiger !== worst && tiger !== geng) avoid.push('<b>' + dirLabel(tiger) + '</b>（白虎凶神）');
    html += block('避忌之方 Directions to Avoid',
      '慎勿向' + avoid.join('；') + '興舉大事。',
      'Keep major moves away from: ' + [worst && dirLabel(worst) + ' (worst palace of the hour)', geng && geng !== worst ? dirLabel(geng) + ' (Venus-metal 庚 — the adversary’s blade)' : null, tiger && tiger !== worst && tiger !== geng ? dirLabel(tiger) + ' (White Tiger)' : null].filter(Boolean).join('; ') + '.');

    var horse = c.palaces[c.horsePalace];
    html += block('動靜之機 Movement & Timing',
      '馬星在' + dirLabel(horse) + '，欲速行者從之。旬空在' + c.xunKong + '（' + kongDirs(c) + '），此方許諾多虛。',
      'The travelling horse stands ' + dirLabel(horse) + ' — take it when speed matters. The void branches ' + c.xunKong + ' hollow out ' + kongDirs(c) + '; promises from there ring empty.');

    $('strategy').innerHTML = html;
  }

  function kongDirs(c) {
    var seen = {}, out = [];
    for (var pn = 1; pn <= 9; pn++) {
      if (c.palaces[pn].flags.kong && !seen[pn]) { seen[pn] = 1; out.push(dirLabel(c.palaces[pn])); }
    }
    return out.join('、');
  }

  function block(title, zh, en) {
    return '<div class="strat"><h3>' + title + '</h3><p class="d-zh">' + zh + '</p><p class="d-en">' + en + '</p></div>';
  }

  function renderDoorUses(c) {
    var order = ['開門', '休門', '生門', '景門', '杜門', '傷門', '驚門', '死門'];
    var html = '<table class="uses"><thead><tr><th>門 Door</th><th>方位 Direction</th><th>宜 Route through it</th></tr></thead><tbody>';
    order.forEach(function (door) {
      var p = findDoorPalace(c, door);
      if (!p) return;
      var u = QimenData.doorUses[door];
      var d = QimenData.doors[door];
      var q = d.quality > 0 ? 'u-good' : (d.quality < 0 ? 'u-bad' : 'u-flat');
      html += '<tr class="' + q + '"><td><b>' + door + '</b><br><span class="soft">' + d.en + '</span></td>' +
        '<td><b>' + dirLabel(p) + '</b>' + (p.flags.kong ? ' <span class="badge b-flat">空</span>' : '') +
        (p.flags.menPo ? ' <span class="badge b-bad">迫</span>' : '') + '</td>' +
        '<td>' + u.zh + '<br><span class="soft">' + u.en + '</span></td></tr>';
    });
    html += '</tbody></table>';
    $('door-uses').innerHTML = html;
  }

  // ---------- live clock & navigation ----------

  function updateCountdown() {
    var el = $('countdown');
    if (!el) return;
    if (!state.live) { el.textContent = '（歷史盤 archived hour）'; return; }
    var now = new Date();
    var range = QimenEngine.hourRange(now);
    var ms = range.end.getTime() - now.getTime();
    if (ms <= 0) return;
    var m = Math.floor(ms / 60000), s = Math.floor((ms % 60000) / 1000);
    el.textContent = '距換盤 next chart in ' + m + 'm ' + two(s) + 's';
  }

  function tick() {
    if (state.live) {
      var now = new Date();
      state.viewDate = now;
      // recast only when the double-hour turns; otherwise just tick the clock
      if (!state._rangeEnd || now.getTime() >= state._rangeEnd) {
        state._rangeEnd = QimenEngine.hourRange(now).end.getTime();
        render();
      } else {
        $('clock-time').textContent = fmtTime(now);
        $('clock-date').textContent = fmtDate(now);
      }
    }
    updateCountdown();
  }

  function setView(d, live) {
    state.viewDate = d;
    state.live = live;
    state._rangeEnd = null;
    $('dt-input').value = fmtDate(d) + 'T' + fmtTime(d);
    render();
  }

  $('btn-prev').addEventListener('click', function () {
    var r = QimenEngine.hourRange(state.viewDate);
    setView(new Date(r.start.getTime() - 3600 * 1000), false);
  });
  $('btn-next').addEventListener('click', function () {
    var r = QimenEngine.hourRange(state.viewDate);
    setView(new Date(r.end.getTime() + 3600 * 1000), false);
  });
  $('btn-now').addEventListener('click', function () { setView(new Date(), true); });
  $('dt-input').addEventListener('change', function () {
    var v = this.value;
    if (!v) return;
    var d = new Date(v);
    if (!isNaN(d.getTime())) setView(d, Math.abs(d.getTime() - Date.now()) < 60000);
  });

  setView(new Date(), true);
  setInterval(tick, 1000);
})();
