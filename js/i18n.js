/*
 * i18n.js — English glosses for the Tong Shu engine's Chinese vocabulary.
 * Covers every 宜/忌 activity term emitted by lunar-javascript, plus the
 * twelve Day Officers, 28 lunar mansions, twelve Day Gods, stems, branches,
 * zodiac animals, solar terms, Na Yin names and Peng Zu taboo couplets.
 */
(function (root) {
  'use strict';

  var ACTIVITIES = {
    '祭祀': 'Worship & ancestor rites',
    '祈福': 'Pray for blessings',
    '求嗣': 'Pray for heirs',
    '开光': 'Consecration',
    '塑绘': 'Sculpt & paint icons',
    '斋醮': 'Fasting & Taoist rites',
    '普渡': 'Universal salvation rites',
    '出行': 'Travel',
    '乘船': 'Boat travel',
    '出火': 'Move the ancestral altar',
    '安香': 'Install the incense altar',
    '嫁娶': 'Marriage / wedding',
    '订盟': 'Engagement / betrothal',
    '纳采': 'Betrothal gifts (proposal)',
    '问名': 'Exchange birth dates (matchmaking)',
    '纳婿': 'Take in a son-in-law',
    '归宁': "Bride's home visit",
    '冠笄': 'Coming-of-age rites',
    '会亲友': 'Gather with family & friends',
    '进人口': 'Add household members (adopt / hire)',
    '雇佣': 'Hire workers',
    '入学': 'Start school / apprenticeship',
    '习艺': 'Learn a craft',
    '赴任': 'Take up a post',
    '上梁': 'Raise the ridge beam',
    '竖柱': 'Raise pillars',
    '开柱眼': 'Cut pillar mortises',
    '定磉': 'Set pillar footings',
    '起基': 'Lay the foundation',
    '动土': 'Ground-breaking',
    '拆卸': 'Demolition / dismantling',
    '修造': 'Construction & renovation',
    '盖屋': 'Roofing / build a house',
    '合脊': 'Close the roof ridge',
    '架马': 'Set up scaffolding',
    '作梁': 'Hew beams',
    '安门': 'Install doors',
    '修门': 'Repair doors',
    '安床': 'Set up the bed',
    '合帐': 'Sew the bed canopy',
    '安机械': 'Install machinery',
    '安碓磑': 'Install the mill',
    '挂匾': 'Hang plaques & signboards',
    '入宅': 'Move into a new home',
    '移徙': 'Relocation / moving',
    '分居': 'Divide the household',
    '扫舍': 'Sweep & clean the house',
    '修饰垣墙': 'Repair walls & fences',
    '补垣': 'Mend walls',
    '坏垣': 'Tear down walls',
    '破屋': 'Tear down buildings',
    '平治道涂': 'Level roads & paths',
    '造桥': 'Build bridges',
    '筑堤': 'Build embankments',
    '开渠': 'Dig channels',
    '掘井': 'Dig a well',
    '开池': 'Dig a pond',
    '放水': 'Fill ponds / release water',
    '塞穴': 'Seal holes & burrows',
    '开厕': 'Build a privy',
    '造仓': 'Build granaries',
    '开仓': 'Open the granary',
    '造庙': 'Build temples',
    '造船': 'Build boats',
    '造车器': 'Build carts & implements',
    '造畜稠': 'Build livestock pens',
    '教牛马': 'Train draft animals',
    '牧养': 'Pasture livestock',
    '纳畜': 'Acquire livestock',
    '捕捉': 'Catch pests',
    '断蚁': 'Exterminate ants',
    '畋猎': 'Hunting',
    '取渔': 'Fishing',
    '结网': 'Weave nets',
    '割蜜': 'Harvest honey',
    '栽种': 'Planting & sowing',
    '伐木': 'Fell timber',
    '开市': 'Open for business',
    '交易': 'Trade & deals',
    '立券': 'Sign contracts',
    '纳财': 'Collect payments / receive wealth',
    '出货财': 'Ship goods / release funds',
    '置产': 'Purchase property',
    '开生坟': 'Prepare a tomb in advance',
    '合寿木': 'Make a coffin in advance',
    '安葬': 'Burial',
    '破土': 'Break ground for burial',
    '启钻': 'Open a grave for reburial',
    '修坟': 'Repair graves',
    '立碑': 'Erect tombstones',
    '谢土': 'Thanksgiving to the earth',
    '入殓': 'Encoffining',
    '成服': 'Don mourning garments',
    '除服': 'End mourning',
    '移柩': 'Move the coffin',
    '行丧': 'Funeral procession',
    '沐浴': 'Bathing & cleansing',
    '理发': 'Haircut',
    '整手足甲': 'Trim nails',
    '求医': 'Seek a physician',
    '治病': 'Treat illness',
    '针灸': 'Acupuncture',
    '探病': 'Visit the sick',
    '解除': 'Cleansing & exorcism',
    '裁衣': 'Cut cloth / tailoring',
    '经络': 'Set up looms / weaving',
    '雕刻': 'Carving & engraving',
    '词讼': 'Litigation',
    '无': 'None listed',
    '诸事不宜': 'Nothing is favorable today',
    '馀事勿取': 'Undertake nothing else'
  };

  /* 建除十二神 — the twelve Day Officers */
  var OFFICERS = {
    '建': { en: 'Establish', note: 'A day of beginnings — good for setting out, taking office and opening ventures; avoid ground-breaking.' },
    '除': { en: 'Remove', note: 'A day for clearing away — cleansing, healing and casting off the old.' },
    '满': { en: 'Full', note: 'A day of abundance — good for rites, gatherings and stocking up; poor for burials.' },
    '平': { en: 'Balance', note: 'An even, ordinary day — good for smoothing, levelling and routine matters.' },
    '定': { en: 'Settle', note: 'A day for fixing things in place — betrothals, contracts, foundations and appointments.' },
    '执': { en: 'Hold', note: 'A day of firm grasp — good for capture, repairs and binding agreements; avoid moving house.' },
    '破': { en: 'Break', note: 'A day of rupture — favorable only for demolition and tearing down; avoid all else.' },
    '危': { en: 'Danger', note: 'A precarious day — tread carefully; suited to quiet devotion, not ventures.' },
    '成': { en: 'Complete', note: 'A day of achievement — excellent for weddings, openings and finishing works.' },
    '收': { en: 'Receive', note: 'A day of harvest — good for collecting, storing and taking in; poor for setting out.' },
    '开': { en: 'Open', note: 'A day of openings — good for launches, learning and communication; avoid burials.' },
    '闭': { en: 'Close', note: 'A day of closure — good for sealing, storing and burial; avoid grand beginnings.' }
  };

  /* 二十八宿 — the 28 lunar mansions */
  var MANSIONS = {
    '角': 'Horn', '亢': 'Neck', '氐': 'Root', '房': 'Room', '心': 'Heart',
    '尾': 'Tail', '箕': 'Winnowing Basket', '斗': 'Dipper', '牛': 'Ox',
    '女': 'Girl', '虚': 'Emptiness', '危': 'Rooftop', '室': 'Encampment',
    '壁': 'Wall', '奎': 'Legs', '娄': 'Bond', '胃': 'Stomach', '昴': 'Pleiades',
    '毕': 'Net', '觜': 'Turtle Beak', '参': 'Three Stars', '井': 'Well',
    '鬼': 'Ghost', '柳': 'Willow', '星': 'Star', '张': 'Extended Net',
    '翼': 'Wings', '轸': 'Chariot'
  };

  /* 十二值日天神 — the twelve Day Gods (Yellow Path / Black Path) */
  var DAY_GODS = {
    '青龙': 'Azure Dragon', '明堂': 'Bright Hall', '天刑': 'Heavenly Punishment',
    '朱雀': 'Vermilion Bird', '金匮': 'Golden Chest', '天德': 'Heavenly Virtue',
    '白虎': 'White Tiger', '玉堂': 'Jade Hall', '天牢': 'Heavenly Prison',
    '玄武': 'Black Tortoise', '司命': 'Controller of Fate', '勾陈': 'Hooked Array'
  };

  var STEMS = {
    '甲': 'Jiǎ', '乙': 'Yǐ', '丙': 'Bǐng', '丁': 'Dīng', '戊': 'Wù',
    '己': 'Jǐ', '庚': 'Gēng', '辛': 'Xīn', '壬': 'Rén', '癸': 'Guǐ'
  };

  var BRANCHES = {
    '子': 'Zǐ', '丑': 'Chǒu', '寅': 'Yín', '卯': 'Mǎo', '辰': 'Chén', '巳': 'Sì',
    '午': 'Wǔ', '未': 'Wèi', '申': 'Shēn', '酉': 'Yǒu', '戌': 'Xū', '亥': 'Hài'
  };

  var ZODIAC = {
    '鼠': 'Rat', '牛': 'Ox', '虎': 'Tiger', '兔': 'Rabbit', '龙': 'Dragon',
    '蛇': 'Snake', '马': 'Horse', '羊': 'Goat', '猴': 'Monkey', '鸡': 'Rooster',
    '狗': 'Dog', '猪': 'Pig'
  };

  var DIRECTIONS = {
    '东': 'East', '南': 'South', '西': 'West', '北': 'North',
    '东北': 'Northeast', '东南': 'Southeast', '西南': 'Southwest', '西北': 'Northwest',
    '正东': 'Due East', '正南': 'Due South', '正西': 'Due West', '正北': 'Due North',
    '中宫': 'Center'
  };

  var SOLAR_TERMS = {
    '立春': 'Start of Spring', '雨水': 'Rain Water', '惊蛰': 'Awakening of Insects',
    '春分': 'Spring Equinox', '清明': 'Clear & Bright', '谷雨': 'Grain Rain',
    '立夏': 'Start of Summer', '小满': 'Grain Buds', '芒种': 'Grain in Ear',
    '夏至': 'Summer Solstice', '小暑': 'Minor Heat', '大暑': 'Major Heat',
    '立秋': 'Start of Autumn', '处暑': 'End of Heat', '白露': 'White Dew',
    '秋分': 'Autumn Equinox', '寒露': 'Cold Dew', '霜降': 'Frost Descent',
    '立冬': 'Start of Winter', '小雪': 'Minor Snow', '大雪': 'Major Snow',
    '冬至': 'Winter Solstice', '小寒': 'Minor Cold', '大寒': 'Major Cold'
  };

  var NAYIN = {
    '海中金': 'Gold in the Sea', '炉中火': 'Fire in the Furnace', '大林木': 'Great Forest Wood',
    '路旁土': 'Roadside Earth', '剑锋金': 'Sword-edge Metal', '山头火': 'Mountaintop Fire',
    '涧下水': 'Stream Water', '城头土': 'City-wall Earth', '白蜡金': 'White Wax Metal',
    '杨柳木': 'Willow Wood', '泉中水': 'Spring Water', '屋上土': 'Rooftop Earth',
    '霹雳火': 'Thunderbolt Fire', '松柏木': 'Pine & Cypress Wood', '长流水': 'Long-flowing Water',
    '沙中金': 'Gold in the Sand', '山下火': 'Fire below the Mountain', '平地木': 'Plains Wood',
    '壁上土': 'Wall Earth', '金箔金': 'Gold-leaf Metal', '覆灯火': 'Covered Lamp Fire',
    '天河水': 'Sky River Water', '大驿土': 'Highway Earth', '钗钏金': 'Hairpin Metal',
    '桑柘木': 'Mulberry Wood', '大溪水': 'Great Stream Water', '沙中土': 'Sand Earth',
    '天上火': 'Fire in the Sky', '石榴木': 'Pomegranate Wood', '大海水': 'Ocean Water'
  };

  var ELEMENTS = { '金': 'Metal', '木': 'Wood', '水': 'Water', '火': 'Fire', '土': 'Earth' };

  /* 彭祖百忌 — Peng Zu's taboos, keyed by leading stem/branch character */
  var PENGZU = {
    '甲': 'Do not open the granary — wealth will scatter.',
    '乙': 'Do not plant — a thousand shoots will not grow.',
    '丙': 'Do not repair the stove — calamity will follow.',
    '丁': 'Do not cut hair — sores will form on the head.',
    '戊': 'Do not take on land — ill fortune to its owner.',
    '己': 'Do not tear up contracts — both parties lose.',
    '庚': 'Do not set the looms — the weaving comes to nothing.',
    '辛': 'Do not brew sauces — the host will never taste them.',
    '壬': 'Do not open the waterways — floods are hard to hold back.',
    '癸': 'Do not go to court — the weak will face the strong.',
    '子': 'Do not consult diviners — you invite misfortune.',
    '丑': 'Hold no coming-of-age rites — the wearer will not return home.',
    '寅': 'Hold no sacrifices — the spirits will not partake.',
    '卯': 'Do not dig wells — the water will not run sweet.',
    '辰': 'Do not weep — deeper mourning will follow.',
    '巳': 'Do not travel far — wealth stays hidden away.',
    '午': 'Do not thatch the roof — the house must be redone.',
    '未': 'Do not take medicine — poison enters the gut.',
    '申': 'Do not set up the bed — spirits enter the room.',
    '酉': 'Do not host guests — drunken disorder follows.',
    '戌': 'Do not eat dog meat — strange things come to the bed.',
    '亥': 'Hold no weddings — it bodes ill for the groom.'
  };

  /* Double-hour clock labels, in traditional order starting from 子 */
  var HOUR_RANGES = {
    '子': '23–01', '丑': '01–03', '寅': '03–05', '卯': '05–07',
    '辰': '07–09', '巳': '09–11', '午': '11–13', '未': '13–15',
    '申': '15–17', '酉': '17–19', '戌': '19–21', '亥': '21–23'
  };

  /* Curated list for the auspicious-day finder */
  var FINDER_ACTIVITIES = [
    '嫁娶', '订盟', '开市', '交易', '立券', '出行', '入宅', '移徙', '动土',
    '修造', '安床', '祭祀', '祈福', '求嗣', '会亲友', '入学', '赴任', '纳财',
    '置产', '栽种', '理发', '求医', '治病', '裁衣', '纳畜', '安葬'
  ];

  function ganzhi(gz) {
    if (!gz || gz.length < 2) return gz || '';
    var g = STEMS[gz.charAt(0)] || gz.charAt(0);
    var z = BRANCHES[gz.charAt(1)] || gz.charAt(1);
    return g + ' ' + z;
  }

  function nayin(name) {
    var poetic = NAYIN[name];
    var el = ELEMENTS[name.charAt(name.length - 1)];
    if (poetic && el) return poetic + ' · ' + el;
    return poetic || el || name;
  }

  root.TSI18N = {
    ACTIVITIES: ACTIVITIES,
    OFFICERS: OFFICERS,
    MANSIONS: MANSIONS,
    DAY_GODS: DAY_GODS,
    STEMS: STEMS,
    BRANCHES: BRANCHES,
    ZODIAC: ZODIAC,
    DIRECTIONS: DIRECTIONS,
    SOLAR_TERMS: SOLAR_TERMS,
    NAYIN: NAYIN,
    ELEMENTS: ELEMENTS,
    PENGZU: PENGZU,
    HOUR_RANGES: HOUR_RANGES,
    FINDER_ACTIVITIES: FINDER_ACTIVITIES,
    activity: function (t) { return ACTIVITIES[t] || t; },
    zodiac: function (t) { return ZODIAC[t] || t; },
    direction: function (t) { return DIRECTIONS[t] || t; },
    mansion: function (t) { return MANSIONS[t] || t; },
    dayGod: function (t) { return DAY_GODS[t] || t; },
    solarTerm: function (t) { return SOLAR_TERMS[t] || t; },
    ganzhi: ganzhi,
    nayin: nayin
  };
})(typeof window !== 'undefined' ? window : globalThis);
