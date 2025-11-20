const fs = require('fs');
const path = require('path');

const sql = fs.readFileSync(path.join(__dirname, 'demo_data.sql'), 'utf8');
const lines = sql.split(/\r?\n/);

function parseValuesList(s) {
  // s is the substring inside VALUES(...)
  const res = [];
  let i = 0;
  while (i < s.length) {
    while (i < s.length && /[\s,]/.test(s[i])) i++;
    if (i >= s.length) break;
    if (s[i] === 'N') { // NULL
      if (s.substr(i,4) === 'NULL') { res.push(null); i += 4; }
      else { throw new Error('Unexpected token at ' + i + ' in ' + s); }
    } else if (s[i] === "'") {
      i++; let val = '';
      while (i < s.length) {
        if (s[i] === "'") { i++; break; }
        if (s[i] === '\\' && i+1 < s.length) { val += s[i+1]; i += 2; continue; }
        val += s[i++];
      }
      res.push(val);
    } else {
      // number or bare token
      let j = i;
      while (j < s.length && /[^,\s]/.test(s[j])) j++;
      const tok = s.substring(i,j);
      if (/^\d+$/.test(tok)) res.push(parseInt(tok,10));
      else res.push(tok);
      i = j;
    }
    // skip comma
    while (i < s.length && /[\s]/.test(s[i])) i++;
    if (s[i] === ',') { i++; continue; }
  }
  return res;
}

const players = new Map();
const games = new Map();
const gamePlayers = [];

for (const line of lines) {
  const l = line.trim();
  if (!l.startsWith('INSERT INTO')) continue;
  if (l.startsWith("INSERT INTO players")) {
    const m = l.match(/VALUES\s*\((.*)\);$/);
    if (!m) continue;
    const vals = parseValuesList(m[1]);
    // players (id, username, display_name, password_hash, gender, avatar_url, country_code, created_at)
    const id = vals[0];
    players.set(id, { id, username: vals[1], display_name: vals[2], country_code: vals[6], created_at: vals[7] });
  } else if (l.startsWith("INSERT INTO games")) {
    const m = l.match(/VALUES\s*\((.*)\);$/);
    if (!m) continue;
    const vals = parseValuesList(m[1]);
    // games (id, created_at, started_at, ended_at, total_rounds, status)
    const id = vals[0];
    games.set(id, { id, created_at: vals[1], started_at: vals[2], ended_at: vals[3], total_rounds: vals[4], status: vals[5] });
  } else if (l.startsWith("INSERT INTO game_players")) {
    const m = l.match(/VALUES\s*\((.*)\);$/);
    if (!m) continue;
    const vals = parseValuesList(m[1]);
    // (game_id, player_id, joined_at, left_at, final_score, total_time, result)
    gamePlayers.push({ game_id: vals[0], player_id: vals[1], joined_at: vals[2], left_at: vals[3], final_score: vals[4], total_time: vals[5], result: vals[6] });
  }
}

const errors = [];

// Basic existence checks
for (const gp of gamePlayers) {
  if (!games.has(gp.game_id)) errors.push(`game_players references missing game ${gp.game_id}`);
  if (!players.has(gp.player_id)) errors.push(`game_players references missing player ${gp.player_id}`);
}

// Per-game consistency
const gpByGame = new Map();
for (const gp of gamePlayers) {
  if (!gpByGame.has(gp.game_id)) gpByGame.set(gp.game_id, []);
  gpByGame.get(gp.game_id).push(gp);
}

function toDate(s) { return s ? new Date(s) : null; }

for (const [gid, g] of games.entries()) {
  const created = toDate(g.created_at);
  const started = toDate(g.started_at);
  const ended = toDate(g.ended_at);
  if (started && started < created) errors.push(`game ${gid} started_at before created_at`);
  if (ended && started && ended < started) errors.push(`game ${gid} ended_at before started_at`);
  const entries = gpByGame.get(gid) || [];
  const playerSet = new Set();

  for (const e of entries) {
    // duplicate player in same game
    if (playerSet.has(e.player_id)) errors.push(`duplicate player ${e.player_id} in game ${gid}`);
    playerSet.add(e.player_id);

    const joined = toDate(e.joined_at);
    const left = toDate(e.left_at);

    if (!joined) { errors.push(`game_player in game ${gid} player ${e.player_id} has no joined_at`); continue; }
    if (joined < created) errors.push(`joined_at ${e.joined_at} < game.created_at ${g.created_at} for game ${gid} player ${e.player_id}`);
    if (started && joined > started) {
      // allow join after start but warn? It's acceptable; no error
    }
    if (left) {
      if (ended && (left > ended)) errors.push(`left_at ${e.left_at} > game.ended_at ${g.ended_at} for game ${gid} player ${e.player_id}`);
      if (left < joined) errors.push(`left_at ${e.left_at} < joined_at ${e.joined_at} for game ${gid} player ${e.player_id}`);
      // total_time roughly matches left-joined
      const dt = left.getTime() - joined.getTime();
      const diff = Math.abs(dt - e.total_time);
      if (diff > 2000) errors.push(`total_time ${e.total_time} differs from left-joined delta ${dt} by ${diff}ms for ${gid}/${e.player_id}`);
    } else {
      // no left: total_time should be plausible
      if (e.total_time < 0 || e.total_time > 24*3600*1000) errors.push(`implausible total_time ${e.total_time} for running/pending player ${e.player_id} game ${gid}`);
    }
  }

  // results consistency
  if (g.status === 'finished') {
    // all players should have non-null result
    const resVals = entries.map(x => x.result);
    for (const r of resVals) {
      if (r === null || r === undefined) errors.push(`game ${gid} finished but a player has null result`);
      if (r && !['win','lose','draw'].includes(r)) errors.push(`game ${gid} has invalid result value ${r}`);
    }
    // verify winners correspond to highest score
    if (entries.length > 0) {
      const maxScore = Math.max(...entries.map(e=>e.final_score));
      const winners = entries.filter(e=>e.final_score===maxScore);
      if (winners.length===entries.length) {
        // all draw -> all results must be 'draw'
        for (const w of winners) if (w.result !== 'draw') errors.push(`game ${gid} all tie but result not draw for ${w.player_id}`);
      } else if (winners.length>1) {
        // multiple winners -> winners should be 'draw' per generator logic
        for (const w of winners) if (w.result !== 'draw') errors.push(`game ${gid} multiple winners but result not draw for ${w.player_id}`);
      } else {
        // one winner -> winner must be 'win' others 'lose'
        for (const w of winners) if (w.result !== 'win') errors.push(`game ${gid} winner ${w.player_id} result not 'win'`);
        for (const e of entries) if (e.final_score !== maxScore && e.result !== 'lose') errors.push(`game ${gid} non-winner ${e.player_id} result not 'lose'`);
      }
    }
  } else {
    // not finished: results should be null
    for (const e of entries) if (e.result !== null) errors.push(`game ${gid} status ${g.status} but player ${e.player_id} has result ${e.result}`);
  }
}

// Summary
if (errors.length === 0) {
  console.log('VALIDATION PASSED: No consistency errors found');
} else {
  console.log(`VALIDATION FAILED: ${errors.length} issue(s)`);
  for (const err of errors.slice(0,200)) console.log('- ' + err);
}

process.exit(errors.length === 0 ? 0 : 1);

