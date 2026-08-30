/*
 * app.js — Tong Shu Daily Almanac
 * Renders one almanac "leaf" per day from the lunar-javascript engine,
 * with bilingual (中文 / English) glosses supplied by i18n.js.
 */
(function () {
  'use strict';

  var I = window.TSI18N;
  var current = new Date();
  current.setHours(12, 0, 0, 0);

  var $ = function (id) { return document.getElementById(id); };

  var WEEKDAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
  var WEEKDAYS_ZH = ['日', '一', '二', '三', '四', '五', '六'];
  var MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July',
    'August', 'September', 'October', 'November', 'December'];
  var BRANCH_ORDER = ['子', '丑', '寅', '卯', '辰', '巳', '午', '未', '申', '酉', '戌', '亥'];

  function pad(n) { return (n < 10 ? '0' : '') + n; }

  function ymd(date) {
    return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate());
  }

  function esc(s) {
    return String(s).replace(/[&<>"]/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c];
    });
  }

  function getLunar(date) {
    return Solar.fromYmd(date.getFullYear(), date.getMonth() + 1, date.getDate()).getLunar();
  }

  /* ---- rendering ---------------------------------------------------- */

  function chip(term, kind) {
    return '<li class="chip chip--' + kind + '">' +
      '<span class="chip__zh">' + esc(term) + '</span>' +
      '<span class="chip__en">' + esc(I.activity(term)) + '</span></li>';
  }

  function renderLeaf(date, l, s) {
    $('greg-month').textContent = MONTHS[date.getMonth()] + ' ' + date.getFullYear();
    $('greg-day').textContent = date.getDate();
    $('greg-weekday').textContent = WEEKDAYS[date.getDay()] + ' · 星期' + WEEKDAYS_ZH[date.getDay()];

    var monthZh = (l.getMonth() < 0 ? '闰' : '') + l.getMonthInChinese();
    $('lunar-date').textContent = '农历' + monthZh + '月' + l.getDayInChinese();
    $('lunar-year').textContent = l.getYearInGanZhi() + l.getYearShengXiao() + '年';
    $('lunar-date-en').textContent = 'Lunar month ' + Math.abs(l.getMonth()) +
      (l.getMonth() < 0 ? ' (leap)' : '') + ', day ' + l.getDay() +
      ' — Year of the ' + I.zodiac(l.getYearShengXiao()) +
      ' (' + I.ganzhi(l.getYearInGanZhi()) + ')';

    var setPillar = function (id, gz) {
      $(id).innerHTML = gz.split('').map(function (c) {
        return '<span>' + esc(c) + '</span>';
      }).join('');
    };
    setPillar('pillar-year', l.getYearInGanZhi());
    setPillar('pillar-month', l.getMonthInGanZhi());
    setPillar('pillar-day', l.getDayInGanZhi());
    $('pillar-day-en').textContent = I.ganzhi(l.getDayInGanZhi()) + ' day · ' + I.nayin(l.getDayNaYin());

    var officer = l.getZhiXing();
    var info = I.OFFICERS[officer] || { en: '', note: '' };
    $('officer-seal').textContent = officer;
    $('officer-name').textContent = officer + '日 · ' + info.en;
    $('officer-note').textContent = info.note;

    var xiu = l.getXiu();
    $('mansion').textContent = xiu + '宿 (' + I.mansion(xiu) + ') · ' + esc(l.getAnimal()) +
      ' — ' + (l.getXiuLuck() === '吉' ? 'auspicious' : 'inauspicious');

    var god = l.getDayTianShen();
    var isYellow = l.getDayTianShenType() === '黄道';
    $('day-god').textContent = god + ' ' + I.dayGod(god) + ' — ' +
      (isYellow ? 'Yellow Path day (吉 favorable)' : 'Black Path day (凶 unfavorable)');
    $('day-god').className = 'leaf__god ' + (isYellow ? 'is-lucky' : 'is-unlucky');
  }

  function renderActivities(l) {
    var yi = l.getDayYi();
    var ji = l.getDayJi();
    $('yi-list').innerHTML = yi.map(function (t) { return chip(t, 'yi'); }).join('');
    $('ji-list').innerHTML = ji.map(function (t) { return chip(t, 'ji'); }).join('');
  }

  function renderClash(l) {
    var chongGz = l.getDayChongGan() + l.getDayChong();
    var animal = l.getDayChongShengXiao();
    $('clash').innerHTML =
      '冲<strong>' + esc(animal) + '</strong> (' + esc(chongGz) + ') — clashes with the ' +
      esc(I.zodiac(animal)) + '. Those born in a ' + esc(I.zodiac(animal)) +
      ' year should keep a low profile today.';
    var sha = l.getDaySha();
    $('sha').innerHTML = '煞<strong>' + esc(sha) + '</strong> — the Sha spirit sits in the ' +
      esc(I.direction(sha)) + '; avoid facing ' + esc(I.direction(sha).toLowerCase()) +
      ' for important undertakings.';
  }

  function renderDirections(l) {
    var rows = [
      ['喜神', 'God of Joy', l.getDayPositionXiDesc()],
      ['福神', 'God of Fortune', l.getDayPositionFuDesc()],
      ['财神', 'God of Wealth', l.getDayPositionCaiDesc()]
    ];
    $('directions').innerHTML = rows.map(function (r) {
      return '<div class="dir"><span class="dir__zh">' + r[0] + '</span>' +
        '<span class="dir__en">' + r[1] + '</span>' +
        '<span class="dir__val">' + esc(r[2]) + ' ' + esc(I.direction(r[2])) + '</span></div>';
    }).join('');
  }

  function renderHours(date) {
    var now = new Date();
    var isToday = ymd(now) === ymd(date);
    var currentBranch = BRANCH_ORDER[Math.floor(((now.getHours() + 1) % 24) / 2)];
    var html = BRANCH_ORDER.map(function (branch, i) {
      var lt = Solar.fromYmdHms(date.getFullYear(), date.getMonth() + 1, date.getDate(),
        i * 2, 0, 0).getLunar();
      var luck = lt.getTimeTianShenLuck();
      var god = lt.getTimeTianShen();
      var cls = 'hour ' + (luck === '吉' ? 'hour--lucky' : 'hour--unlucky') +
        (isToday && branch === currentBranch ? ' hour--now' : '');
      return '<div class="' + cls + '" title="' + esc(god) + ' ' + esc(I.dayGod(god)) + '">' +
        '<span class="hour__branch">' + branch + '</span>' +
        '<span class="hour__range">' + I.HOUR_RANGES[branch] + '</span>' +
        '<span class="hour__luck">' + luck + '</span></div>';
    }).join('');
    $('hours').innerHTML = html;
  }

  function renderPengzu(l) {
    var gan = l.getPengZuGan();
    var zhi = l.getPengZuZhi();
    $('pengzu').innerHTML =
      '<p><span class="zh">' + esc(gan) + '</span><span class="en">' +
      esc(I.PENGZU[gan.charAt(0)] || '') + '</span></p>' +
      '<p><span class="zh">' + esc(zhi) + '</span><span class="en">' +
      esc(I.PENGZU[zhi.charAt(0)] || '') + '</span></p>';
  }

  function renderSeason(date, l, s) {
    var jq = l.getCurrentJieQi();
    var prev = l.getPrevJieQi(true);
    var next = l.getNextJieQi(true);
    var cur = jq ? jq.getName() : prev.getName();
    var nSolar = next.getSolar();
    var nDate = new Date(nSolar.getYear(), nSolar.getMonth() - 1, nSolar.getDay(), 12);
    var days = Math.round((nDate - date) / 86400000);
    $('season').innerHTML =
      '<p class="season__now">' + esc(cur) + ' <span>' + esc(I.solarTerm(cur)) + '</span></p>' +
      '<p class="season__next">Next: ' + esc(next.getName()) + ' ' +
      esc(I.solarTerm(next.getName())) + ' — ' + esc(nSolar.toYmd()) +
      (days > 0 ? ' (in ' + days + ' day' + (days > 1 ? 's' : '') + ')' : ' (today)') + '</p>';

    var fests = l.getFestivals().concat(l.getOtherFestivals(), s.getFestivals());
    $('festivals').textContent = fests.length ? fests.join(' · ') : '';
    $('festivals').hidden = !fests.length;
  }

  /* ---- auspicious-day finder ---------------------------------------- */

  function renderFinderOptions() {
    $('finder-select').innerHTML = I.FINDER_ACTIVITIES.map(function (t) {
      return '<option value="' + t + '">' + t + ' — ' + esc(I.activity(t)) + '</option>';
    }).join('');
  }

  function runFinder() {
    var term = $('finder-select').value;
    var results = [];
    var d = new Date(current);
    for (var i = 1; i <= 120 && results.length < 6; i++) {
      d.setDate(d.getDate() + 1);
      var l = getLunar(d);
      if (l.getDayYi().indexOf(term) >= 0) {
        results.push({
          date: new Date(d),
          lunar: l,
          officer: l.getZhiXing(),
          chong: l.getDayChongShengXiao()
        });
      }
    }
    if (!results.length) {
      $('finder-results').innerHTML =
        '<p class="finder__empty">No favorable day found in the next 120 days.</p>';
      return;
    }
    $('finder-results').innerHTML = results.map(function (r) {
      var off = I.OFFICERS[r.officer] || { en: '' };
      return '<button type="button" class="finder__day" data-date="' + ymd(r.date) + '">' +
        '<span class="finder__date">' + esc(WEEKDAYS[r.date.getDay()].slice(0, 3)) + ' ' +
        esc(MONTHS[r.date.getMonth()].slice(0, 3)) + ' ' + r.date.getDate() +
        (r.date.getFullYear() !== current.getFullYear() ? ' ' + r.date.getFullYear() : '') +
        '</span>' +
        '<span class="finder__meta">' + esc(r.officer) + ' ' + esc(off.en) +
        ' · 冲' + esc(r.chong) + ' (' + esc(I.zodiac(r.chong)) + ')</span></button>';
    }).join('');
  }

  /* ---- orchestration ------------------------------------------------ */

  function render() {
    var s = Solar.fromYmd(current.getFullYear(), current.getMonth() + 1, current.getDate());
    var l = s.getLunar();
    renderLeaf(current, l, s);
    renderActivities(l);
    renderClash(l);
    renderDirections(l);
    renderHours(current);
    renderPengzu(l);
    renderSeason(current, l, s);
    $('date-input').value = ymd(current);
    document.title = '通勝 Tong Shu · ' + ymd(current);
  }

  function shift(days) {
    current.setDate(current.getDate() + days);
    render();
    runFinder();
  }

  function init() {
    renderFinderOptions();

    $('btn-prev').addEventListener('click', function () { shift(-1); });
    $('btn-next').addEventListener('click', function () { shift(1); });
    $('btn-today').addEventListener('click', function () {
      current = new Date();
      current.setHours(12, 0, 0, 0);
      render();
      runFinder();
    });
    $('date-input').addEventListener('change', function () {
      var parts = this.value.split('-');
      if (parts.length === 3) {
        var d = new Date(+parts[0], +parts[1] - 1, +parts[2], 12);
        if (!isNaN(d)) { current = d; render(); runFinder(); }
      }
    });
    $('finder-select').addEventListener('change', runFinder);
    $('finder-results').addEventListener('click', function (e) {
      var btn = e.target.closest('[data-date]');
      if (!btn) return;
      var parts = btn.getAttribute('data-date').split('-');
      current = new Date(+parts[0], +parts[1] - 1, +parts[2], 12);
      render();
      runFinder();
      window.scrollTo({ top: 0, behavior: 'smooth' });
    });
    document.addEventListener('keydown', function (e) {
      if (e.target.tagName === 'INPUT' || e.target.tagName === 'SELECT') return;
      if (e.key === 'ArrowLeft') shift(-1);
      if (e.key === 'ArrowRight') shift(1);
    });

    render();
    runFinder();
  }

  init();
})();
