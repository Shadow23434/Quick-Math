const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const bcrypt = require('bcrypt');

function uuid() {
  if (crypto.randomUUID) return crypto.randomUUID();
  // fallback for older Node: produce v4-like UUID
  const b = crypto.randomBytes(16);
  b[6] = (b[6] & 0x0f) | 0x40;
  b[8] = (b[8] & 0x3f) | 0x80;
  const hex = b.toString('hex');
  return [hex.substring(0,8), hex.substring(8,12), hex.substring(12,16), hex.substring(16,20), hex.substring(20)].join('-');
}

function hashPassword(password) {
  const saltRounds = 12;
  return bcrypt.hashSync(password, saltRounds);
}

function randInt(min, max) { // inclusive
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function randChoice(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

function formatDate(d) {
  const YYYY = d.getFullYear();
  const MM = String(d.getMonth() + 1).padStart(2, '0');
  const DD = String(d.getDate()).padStart(2, '0');
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  const ss = String(d.getSeconds()).padStart(2, '0');
  return `${YYYY}-${MM}-${DD} ${hh}:${mm}:${ss}`;
}

// Generate players
const numPlayers = 20;
const players = [];
const genders = ['male','female','other'];
const countries = require('./countries');

// Name pools by locale (display names are realistic; usernames are slugified ASCII)
const vietnamNames = [
  'Nguyễn Văn An', 'Trần Thị Bích', 'Lê Quang Huy', 'Phạm Minh Tuấn', 'Hoàng Anh Khoa',
  'Võ Đức Long', 'Bùi Thị Nga', 'Đoàn Văn Sơn', 'Lâm Thị Hồng', 'Đặng Thanh Tùng'
];
const englishNames = [
  'John Smith', 'Emma Johnson', 'Liam Williams', 'Olivia Brown', 'Noah Jones',
  'Ava Miller', 'Lucas Davis', 'Sophia Wilson', 'Mason Moore', 'Isabella Taylor'
];
const spanishNames = [
  'Carlos García', 'María Rodríguez', 'José Martínez', 'Ana López', 'Luis Hernández',
  'Sofía González', 'Diego Pérez', 'Lucía Sánchez', 'Miguel Ramírez', 'Elena Torres'
];
const portugueseNames = [
  'João Silva', 'Maria Santos', 'Pedro Oliveira', 'Ana Costa', 'Lucas Fernandes'
];
const frenchNames = [
  'Jean Dupont', 'Marie Dubois', 'Pierre Martin', 'Julie Bernard'
];
const germanNames = [
  'Hans Müller', 'Anna Schmidt', 'Karl Fischer'
];
const russianNames = [
  'Ivan Ivanov', 'Olga Petrova', 'Dmitry Sokolov'
];
const japaneseNames = [
  'Taro Yamada', 'Yuki Sato', 'Hiroshi Tanaka'
];
const chineseNames = [
  'Li Wei', 'Wang Fang', 'Zhang Lei'
];
const koreanNames = [
  'Kim Minsoo', 'Lee Ji-eun', 'Park Joon'
];
const arabicNames = [
  'Mohammed Ali', 'Fatima Zahra', 'Ahmed Hassan'
];
const defaultNames = [
  'Alex Cooper', 'Maya Patel', 'Diego Cruz', 'Sana Khan', 'Oliver King', 'Chloe Green'
];

function getNamePoolForCountry(code){
  // common groupings
  const eng = new Set(['us','gb','au','ca','nz','ie']);
  const span = new Set(['es','mx','ar','co','pe','ve','cl','ec','uy','py','bo','do','cr','pa','gt','hn','ni','sv']);
  const portug = new Set(['br','pt']);
  const fren = new Set(['fr','be','ch','lu','mc']);
  const germ = new Set(['de','at','ch']);
  const rus = new Set(['ru','by','ua','kz']);
  const jap = new Set(['jp']);
  const chi = new Set(['cn','tw','hk']);
  const kor = new Set(['kr','kp']);
  const arab = new Set(['sa','ae','eg','iq','jo','lb','sy','om','qa','kw','bh','ye']);

  if (code === 'vn') return vietnamNames;
  if (eng.has(code)) return englishNames;
  if (span.has(code)) return spanishNames;
  if (portug.has(code)) return portugueseNames;
  if (fren.has(code)) return frenchNames;
  if (germ.has(code)) return germanNames;
  if (rus.has(code)) return russianNames;
  if (jap.has(code)) return japaneseNames;
  if (chi.has(code)) return chineseNames;
  if (kor.has(code)) return koreanNames;
  if (arab.has(code)) return arabicNames;
  // fallback: prefer English/neutral names to avoid assigning region-specific names to unrelated countries
  return englishNames.concat(defaultNames);
}

function slugifyName(name) {
  // normalize, remove diacritics, convert to lowercase, keep letters/numbers and dot
  const noDiacritics = name.normalize('NFD').replace(/\p{Diacritic}/gu, '');
  const slug = noDiacritics.toLowerCase().replace(/[^a-z0-9]+/g, '.').replace(/^\.|\.$/g, '');
  return slug;
}

const usedUsernames = new Set();
const usedDisplayNames = new Set();

// Define country-code groups aligned with name pools
const groupCountries = {
  vietnam: ['vn'],
  english: ['us','gb','au','ca','nz','ie','in','ph','ng','ke','za','jm','bm','bb','vg'],
  spanish: ['es','mx','ar','co','pe','ve','cl','ec','uy','py','bo','do','cr','pa','gt','hn','ni','sv'],
  portuguese: ['br','pt'],
  french: ['fr','be','ch','lu','mc','ci','sn','ml','bf','ne','tg','cm','cd','ht'],
  german: ['de','at','ch','lu'],
  russian: ['ru','by','ua','kz'],
  japanese: ['jp'],
  chinese: ['cn','tw','hk','sg'],
  korean: ['kr'],
  arabic: ['sa','ae','eg','iq','jo','lb','sy','om','qa','kw','bh','ye','ma','dz','tn','ly'],
  default: []
};

function pickCountryFromGroup(groupName) {
  const arr = groupCountries[groupName] || [];
  if (arr.length === 0) return randChoice(countries);
  return randChoice(arr);
}

// Helper to pick a random group weighted slightly toward common languages
const groupKeys = Object.keys(groupCountries);
function pickGroup() {
  // simple weights: more English/Spanish/Brazilian representation
  const weights = groupKeys.map(k => {
    if (k === 'english') return 20;
    if (k === 'spanish') return 12;
    if (k === 'portuguese') return 6;
    if (k === 'vietnam') return 3;
    if (k === 'french') return 6;
    if (k === 'german') return 3;
    if (k === 'russian') return 2;
    if (k === 'japanese' || k === 'chinese' || k === 'korean') return 2;
    if (k === 'arabic') return 4;
    return 1;
  });
  // pick weighted
  const total = weights.reduce((a,b)=>a+b,0);
  let r = Math.floor(Math.random() * total);
  for (let i=0;i<groupKeys.length;i++){
    if (r < weights[i]) return groupKeys[i];
    r -= weights[i];
  }
  return groupKeys[0];
}

// Map group name to name pool
const groupPools = {
  vietnam: vietnamNames,
  english: englishNames,
  spanish: spanishNames,
  portuguese: portugueseNames,
  french: frenchNames,
  german: germanNames,
  russian: russianNames,
  japanese: japaneseNames,
  chinese: chineseNames,
  korean: koreanNames,
  arabic: arabicNames,
  default: defaultNames
};

for (let i = 0; i < numPlayers; i++) {
  const id = uuid();

  // pick a language group first
  const group = pickGroup();
  // pick a name from the group's pool
  const pool = groupPools[group] || groupPools['default'] || englishNames;
  let fullName = randChoice(pool);
  let attempts = 0;
  while (usedDisplayNames.has(fullName) && attempts < 10) {
    fullName = randChoice(pool);
    attempts++;
  }
  usedDisplayNames.add(fullName);

  // then pick a country code from the same group (fallback to random global country)
  const country_code = pickCountryFromGroup(group);

  let base = slugifyName(fullName);
  if (!base) {
    // fallback to ascii id if slug empty
    base = `user${String(i+1).padStart(2,'0')}`;
  }
  let username = base;
  let suffix = 1;
  while (usedUsernames.has(username)) {
    username = `${base}${suffix}`;
    suffix++;
  }
  usedUsernames.add(username);

  const display_name = fullName;
  // Default password is '123' (hashed with BCrypt)
  const password_hash = hashPassword('123');
  const gender = randChoice(genders);
  const avatar_url = `https://i.pravatar.cc/150?img=${(i % 70) + 1}`;
  const created_at = new Date(Date.now() - randInt(0, 365) * 24 * 3600 * 1000);
  players.push({ id, username, display_name, password_hash, gender, avatar_url, country_code, created_at: formatDate(created_at) });
}

// Generate games and game_players together with consistent logic
const numGames = 50;
const games = [];
const gamePlayers = [];

// weighted status picker helper
function pickStatus() {
  // weights: pending 25, running 25, finished 35, cancelled 15
  const choices = ['pending','running','finished','cancelled'];
  const weights = [25,25,35,15];
  const total = weights.reduce((a,b)=>a+b,0);
  let r = Math.floor(Math.random() * total);
  for (let i=0;i<choices.length;i++) {
    if (r < weights[i]) return choices[i];
    r -= weights[i];
  }
  return 'pending';
}

for (let i = 0; i < numGames; i++) {
  const id = uuid();
  const createdAt = new Date(Date.now() - randInt(0, 365) * 24 * 3600 * 1000);
  const status = pickStatus();

  // determine started/ended based on status
  let startedAt = null;
  let endedAt = null;
  if (status === 'running' || status === 'finished' || status === 'cancelled') {
    // start between createdAt +1s and +1h
    startedAt = new Date(createdAt.getTime() + randInt(1, 3600) * 1000);
  }
  if (status === 'finished' || status === 'cancelled') {
    // end between startedAt +10s and +90min
    const base = startedAt ? startedAt.getTime() : createdAt.getTime();
    endedAt = new Date(base + randInt(10, 90*60) * 1000);
    // ensure endedAt after startedAt
    if (startedAt && endedAt.getTime() <= startedAt.getTime()) {
      endedAt = new Date(startedAt.getTime() + 10000);
    }
  }

  const total_rounds = randInt(1, 10);
  games.push({ id, created_at: formatDate(createdAt), started_at: startedAt ? formatDate(startedAt) : null, ended_at: endedAt ? formatDate(endedAt) : null, total_rounds, status });

  // choose 2-4 distinct players for this game
  const numPlayersInGame = randInt(2, Math.min(4, players.length));
  // shuffle players and take first k
  const shuffled = players.slice().sort(()=>Math.random()-0.5);
  const selected = shuffled.slice(0, numPlayersInGame);

  // per-player entries
  const entries = [];
  for (const p of selected) {
    // joined: between createdAt and (startedAt or createdAt+30s)
    const joinBase = createdAt.getTime();
    const joinLimit = startedAt ? startedAt.getTime() : (joinBase + randInt(1, 30) * 1000);
    const joinedTime = new Date(randInt(joinBase, Math.max(joinBase+1, joinLimit)));

    // left: if endedAt exists -> between (startedAt or joined) and endedAt; otherwise null
    let leftTime = null;
    if (endedAt) {
      const leftMin = Math.max(startedAt ? startedAt.getTime() : joinedTime.getTime(), joinedTime.getTime());
      const leftMax = endedAt.getTime();
      leftTime = new Date(randInt(leftMin, Math.max(leftMin+1, leftMax)));
    }

    // total_time in ms consistent with joined/left (if left exists), else random small variance
    let total_time = 0;
    if (leftTime) {
      total_time = Math.max(0, leftTime.getTime() - joinedTime.getTime() + randInt(-500, 500));
    } else {
      // running or pending: create a plausible accumulated time
      total_time = randInt(5000, 300000);
    }

    // final_score: random
    const final_score = randInt(0, 1000);

    entries.push({ game_id: id, player_id: p.id, joined_at: formatDate(joinedTime), left_at: leftTime ? formatDate(leftTime) : null, final_score, total_time });
  }

  // determine results only if finished: highest score -> win; ties -> draw
  if (status === 'finished') {
    const maxScore = Math.max(...entries.map(e => e.final_score));
    const winners = entries.filter(e => e.final_score === maxScore);
    if (winners.length === entries.length) {
      entries.forEach(e => e.result = 'draw');
    } else if (winners.length > 1) {
      entries.forEach(e => e.result = winners.includes(e) ? 'draw' : 'lose');
    } else {
      entries.forEach(e => e.result = (e.final_score === maxScore) ? 'win' : 'lose');
    }
  } else {
    // pending/running/cancelled: no per-player result
    entries.forEach(e => e.result = null);
  }

  for (const e of entries) gamePlayers.push(e);
}

// build SQL
let sql = '';
sql += '-- Demo data generated by gen.js\n';
sql += 'SET FOREIGN_KEY_CHECKS = 0;\n';

sql += '\n-- Players\n';
for (const p of players) {
  sql += `INSERT INTO players (id, username, display_name, password_hash, gender, avatar_url, country_code, created_at) VALUES ('${p.id}', '${p.username.replace(/'/g, "\\'")}', '${p.display_name.replace(/'/g, "\\'")}', '${p.password_hash}', '${p.gender}', '${p.avatar_url}', '${p.country_code}', '${p.created_at}');\n`;
}

sql += '\n-- Games\n';
for (const g of games) {
  const started = g.started_at ? `'${g.started_at}'` : 'NULL';
  const ended = g.ended_at ? `'${g.ended_at}'` : 'NULL';
  sql += `INSERT INTO games (id, created_at, started_at, ended_at, total_rounds, status) VALUES ('${g.id}', '${g.created_at}', ${started}, ${ended}, ${g.total_rounds}, '${g.status}');\n`;
}

sql += '\n-- Game players\n';
for (const gp of gamePlayers) {
  const left = gp.left_at ? `'${gp.left_at}'` : 'NULL';
  sql += `INSERT INTO game_players (game_id, player_id, joined_at, left_at, final_score, total_time, result) VALUES ('${gp.game_id}', '${gp.player_id}', '${gp.joined_at}', ${left}, ${gp.final_score}, ${gp.total_time}, ${gp.result ? `'${gp.result}'` : 'NULL'});\n`;
}

sql += '\nSET FOREIGN_KEY_CHECKS = 1;\n';

// write to file
const outPath = path.join(__dirname, 'demo_data.sql');
fs.writeFileSync(outPath, sql, 'utf8');
console.log(`Wrote demo SQL to ${outPath}`);
