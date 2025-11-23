// javascript
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const bcrypt = require('bcryptjs');
const countries = require('./countries.js');

function uuid() {
    if (crypto.randomUUID) return crypto.randomUUID();
    // fallback for older Node: produce v4-like UUID
    const b = crypto.randomBytes(16);
    b[6] = (b[6] & 0x0f) | 0x40;
    b[8] = (b[8] & 0x3f) | 0x80;
    const hex = b.toString('hex');
    return [hex.substring(0,8), hex.substring(8,12), hex.substring(12,16), hex.substring(16,20), hex.substring(20)].join('-');
}

function hashPasswordSync(password) {
    // Use bcrypt to create a salted hash compatible with many systems
    const saltRounds = 12;
    return bcrypt.hashSync(password, saltRounds);
}

const OUT = path.resolve(__dirname, 'demo_data.sql');
const PLAYER_COUNT = 10;
const MATCH_COUNT = 5;
const DEFAULT_AVATAR = 'https://tse1.mm.bing.net/th/id/OIP.pLa0MvBoBWBLYBwKtdbLhQAAAA?rs=1&pid=ImgDetMain&o=7&rm=3';

function nowSql(dt = new Date()) {
    return dt.toISOString().slice(0, 19).replace('T', ' ');
}

function sqlEscape(s) {
    return s.replace(/'/g, "''");
}

function randomFrom(arr) {
    return arr[Math.floor(Math.random() * arr.length)];
}

function randInt(min, max) { // inclusive
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

// Small attribute pools
const genders = ['male','female','other'];
const statuses = ['online','in_game','offline'];

// Name pools by locale (small realistic lists)
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
const portugueseNames = ['João Silva', 'Maria Santos', 'Pedro Oliveira', 'Ana Costa', 'Lucas Fernandes'];
const frenchNames = ['Jean Dupont', 'Marie Dubois', 'Pierre Martin', 'Julie Bernard'];
const germanNames = ['Hans Müller', 'Anna Schmidt', 'Karl Fischer'];
const russianNames = ['Ivan Ivanov', 'Olga Petrova', 'Dmitry Sokolov'];
const japaneseNames = ['Taro Yamada', 'Yuki Sato', 'Hiroshi Tanaka'];
const chineseNames = ['Li Wei', 'Wang Fang', 'Zhang Lei'];
const koreanNames = ['Kim Minsoo', 'Lee Ji-eun', 'Park Joon'];
const arabicNames = ['Mohammed Ali', 'Fatima Zahra', 'Ahmed Hassan'];
const defaultNames = ['Alex Cooper', 'Maya Patel', 'Diego Cruz', 'Sana Khan', 'Oliver King', 'Chloe Green'];

function slugifyName(name) {
  if (!name) return '';
  // remove diacritics and non-alphanumeric, use dots between parts
  const noDiacritics = name.normalize('NFD').replace(/\p{Diacritic}/gu, '');
  const slug = noDiacritics.toLowerCase().replace(/[^a-z0-9]+/g, '.').replace(/^\.|\.$/g, '');
  return slug || '';
}

function getNamePoolForCountry(code){
  const c = (code || '').toLowerCase();
  const eng = new Set(['us','gb','au','ca','nz','ie','in','ph']);
  const span = new Set(['es','mx','ar','co','pe','ve','cl','ec','uy','py','bo','do','cr','pa','gt','hn','ni','sv']);
  const portug = new Set(['br','pt']);
  const fren = new Set(['fr','be','ch','lu','mc']);
  const germ = new Set(['de','at','ch']);
  const rus = new Set(['ru','by','ua','kz']);
  const jap = new Set(['jp']);
  const chi = new Set(['cn','tw','hk','sg']);
  const kor = new Set(['kr']);
  const arab = new Set(['sa','ae','eg','iq','jo','lb','sy','om','qa','kw','bh','ye','ma','dz','tn','ly']);

  if (c === 'vn') return vietnamNames;
  if (eng.has(c)) return englishNames;
  if (span.has(c)) return spanishNames;
  if (portug.has(c)) return portugueseNames;
  if (fren.has(c)) return frenchNames;
  if (germ.has(c)) return germanNames;
  if (rus.has(c)) return russianNames;
  if (jap.has(c)) return japaneseNames;
  if (chi.has(c)) return chineseNames;
  if (kor.has(c)) return koreanNames;
  if (arab.has(c)) return arabicNames;
  return englishNames.concat(defaultNames);
}

// Helper to pick a country from a named group
const groupCountries = {
  vietnam: ['vn'],
  english: ['us','gb','au','ca','nz','ie','in','ph','za'],
  spanish: ['es','mx','ar','co','pe','ve','cl','ec','uy','py','bo','do','cr','pa','gt','hn','ni','sv'],
  portuguese: ['br','pt'],
  french: ['fr','be','ch','lu','mc'],
  german: ['de','at','ch','lu'],
  russian: ['ru','by','ua','kz'],
  japanese: ['jp'],
  chinese: ['cn','tw','hk','sg'],
  korean: ['kr'],
  arabic: ['sa','ae','eg','iq','jo','lb','sy','om','qa','kw','bh','ye','ma','dz','tn','ly'],
  default: countries
};

const groupKeys = Object.keys(groupCountries);
function pickGroup() {
  const weights = groupKeys.map(k => {
    if (k === 'english') return 20;
    if (k === 'spanish') return 12;
    if (k === 'portuguese') return 6;
    if (k === 'vietnam') return 4;
    if (k === 'french') return 5;
    if (k === 'german') return 3;
    if (k === 'russian') return 2;
    if (k === 'japanese' || k === 'chinese' || k === 'korean') return 2;
    if (k === 'arabic') return 3;
    return 1;
  });
  const total = weights.reduce((a,b)=>a+b,0);
  let r = Math.floor(Math.random() * total);
  for (let i=0;i<groupKeys.length;i++){
    if (r < weights[i]) return groupKeys[i];
    r -= weights[i];
  }
  return groupKeys[0];
}

function pickCountryFromGroup(groupName) {
  const arr = groupCountries[groupName] || countries;
  return arr && arr.length ? randomFrom(arr) : randomFrom(countries);
}

// Build players: ensure unique usernames and display names, random but plausible attributes
const players = [];
const usedUsernames = new Set();
const usedDisplayNames = new Set();
for (let i = 1; i <= PLAYER_COUNT; i++) {
    const id = uuid();

    // pick country group first to bias country selection, then pick a specific country from that group
    const group = pickGroup();
    const country_code = pickCountryFromGroup(group);
    const pool = getNamePoolForCountry(country_code);
    let fullName = (pool && pool.length) ? randomFrom(pool) : `Player ${i}`;
    let attempts = 0;
    while (usedDisplayNames.has(fullName) && attempts < 10) {
        fullName = (pool && pool.length) ? randomFrom(pool) : `Player ${i}`;
        attempts++;
    }
    usedDisplayNames.add(fullName);

    // slugify for username and ensure uniqueness
    const base = slugifyName(fullName) || `user${String(i).padStart(2,'0')}`;
    let username = base;
    let suffix = 1;
    while (usedUsernames.has(username)) {
        username = `${base}${suffix}`;
        suffix++;
    }
    usedUsernames.add(username);

    const password_hash = hashPasswordSync('123');
    const gender = randomFrom(genders);
    const avatar_url = `https://i.pravatar.cc/150?img=${(i % 70) + 1}`;
    const created_at = nowSql(new Date(Date.now() - Math.floor(Math.random() * 1000 * 60 * 60 * 24 * 30)));
    // default status is 'offline' per schema requirement
    const status = 'offline';
    // last_active_at: set to a recent time for offline users (or NULL if you prefer to leave empty)
    const last_active_at = status === 'offline' ? nowSql(new Date(Date.now() - Math.floor(Math.random() * 1000 * 60 * 60 * 24 * 30))) : null;

    players.push({ id, username, display_name: fullName, password_hash, gender, avatar_url, country_code, created_at, status, last_active_at });
}

// Build matches: force all matches to be "finished" with logical start/end times
const matches = [];
for (let m = 0; m < MATCH_COUNT; m++) {
    const id = uuid();
    // created within last 10 days (random)
    const createdAt = new Date(Date.now() - randInt(0, 10 * 24 * 3600) * 1000);
    const created_at = nowSql(createdAt);

    // finished: ensure started > created and ended > started, both not in the future
    const nowMs = Date.now();
    let startedMs = createdAt.getTime() + randInt(5, 2 * 3600) * 1000;
    startedMs = Math.max(startedMs, createdAt.getTime() + 1000);
    if (startedMs >= nowMs) startedMs = Math.max(createdAt.getTime() + 1000, nowMs - randInt(1, 3600) * 1000);

    let endedMs = startedMs + randInt(10, 3 * 3600) * 1000 + randInt(0, 60) * 1000;
    if (endedMs >= nowMs) endedMs = Math.max(startedMs + 1000, nowMs - randInt(1, 60) * 1000);
    if (endedMs <= startedMs) endedMs = startedMs + 1000;

    const started = new Date(startedMs);
    const ended = new Date(endedMs);
    const total_rounds = randInt(1, 10);
    const status = 'finished';

    // keep ms values for building consistent player join/leave times later
    matches.push({ id, created_at, started_at: nowSql(started), ended_at: nowSql(ended), total_rounds, status, _startedMs: startedMs, _endedMs: endedMs });
}

// Build game_history: for each finished match pick 2 players, generate join/leave times and scores, compute results
const game_history = [];
for (const match of matches) {
    const pool = players.slice();
    // shuffle
    for (let i = pool.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [pool[i], pool[j]] = [pool[j], pool[i]];
    }
    const k = 2;
    const selected = pool.slice(0, k);

    const entries = [];
    const startedMs = match._startedMs;
    const endedMs = match._endedMs;
    for (const p of selected) {
        // joined between started and started + 30% of duration
        const duration = Math.max(1000, endedMs - startedMs);
        const joinOffset = randInt(0, Math.floor(duration * 0.3 / 1000)) * 1000;
        const joinMs = Math.min(endedMs - 1000, startedMs + joinOffset);
        // left between join and ended
        const leftMs = Math.max(joinMs + 1000, randInt(joinMs, endedMs));
        const joined_at = nowSql(new Date(joinMs));
        const left_at = nowSql(new Date(leftMs));
        const total_time = Math.max(0, leftMs - joinMs + randInt(-500, 500));
        const final_score = randInt(0, 1000);
        entries.push({ game_id: match.id, player_id: p.id, joined_at, left_at, final_score, total_time });
    }

    // determine results: highest score wins; if tie, lower total_time wins; if still tie -> draw
    const maxScore = Math.max(...entries.map(e => e.final_score));
    const candidates = entries.filter(e => e.final_score === maxScore);
    if (candidates.length === entries.length) {
        entries.forEach(e => e.result = 'draw');
    } else if (candidates.length > 1) {
        // tie-break by total_time (lower wins)
        const minTime = Math.min(...candidates.map(c => c.total_time));
        const winners = candidates.filter(c => c.total_time === minTime);
        if (winners.length === 1) {
            entries.forEach(e => e.result = winners.includes(e) ? 'win' : 'lose');
        } else {
            // multiple winners -> draw among them
            entries.forEach(e => e.result = winners.includes(e) ? 'draw' : 'lose');
        }
    } else {
        // single winner
        entries.forEach(e => e.result = (e.final_score === maxScore) ? 'win' : 'lose');
    }

    for (const e of entries) {
        game_history.push({ match_id: e.game_id, player_id: e.player_id, final_score: e.final_score, total_time: e.total_time, result: e.result });
    }
}

// Compose SQL
let sql = '';
sql += "USE quickmath;\n\n";
sql += "-- Players\n";
for (const p of players) {
    // SQL escaping: double single quotes for SQL string literal safety
    const esc = s => (''+s).replace(/'/g, "''");
    const lastActive = p.last_active_at ? `'${esc(p.last_active_at)}'` : 'NULL';
    const statusVal = esc(p.status || 'offline');
    sql += `INSERT INTO players (id, username, display_name, password_hash, gender, avatar_url, country_code, created_at, status, last_active_at) VALUES ('${esc(p.id)}', '${esc(p.username)}', '${esc(p.display_name)}', '${esc(p.password_hash)}', '${esc(p.gender)}', '${esc(p.avatar_url)}', '${esc(p.country_code)}', '${esc(p.created_at)}', '${statusVal}', ${lastActive});\n`;
}
sql += "\n-- Matches\n";
for (const m of matches) {
    sql += `INSERT INTO matches (id, created_at, started_at, ended_at, total_rounds, status) VALUES ('${sqlEscape(m.id)}','${sqlEscape(m.created_at)}',${m.started_at ? `'${sqlEscape(m.started_at)}'` : 'NULL'},${m.ended_at ? `'${sqlEscape(m.ended_at)}'` : 'NULL'},${m.total_rounds},'${sqlEscape(m.status)}');\n`;
}
sql += "\n-- Game history\n";
for (const gh of game_history) {
    sql += `INSERT INTO game_history (match_id, player_id, final_score, total_time, result) VALUES ('${sqlEscape(gh.match_id)}','${sqlEscape(gh.player_id)}',${gh.final_score},${gh.total_time},${gh.result ? `'${sqlEscape(gh.result)}'` : 'NULL'});\n`;
}

fs.writeFileSync(OUT, sql, 'utf8');
console.log(`Seed SQL written to ${OUT}`);
