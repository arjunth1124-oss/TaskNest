#!/usr/bin/env node
/**
 * TaskNest demo sync server. Zero dependencies, needs only Node.js 18+.
 *
 *   node server.js            -> http://localhost:8080
 *   PORT=9000 node server.js
 *   FAIL_RATE=0.3 node server.js   -> 30% of requests fail with 503 (test retries/backoff)
 *
 * Endpoints
 *   GET  /health
 *   GET  /tasks                          all tasks (debug)
 *   GET  /tasks/changes?since=N&limit=M  change feed after sequence N
 *   PUT  /tasks/:id   { task, baseVersion }   200 stored task | 409 { serverTask }
 *   POST /debug/edit/:id  { title?, description?, isCompleted?, isDeleted? }
 *        simulates an edit from "another device" (for conflict demos)
 */
const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = Number(process.env.PORT || 8080);
const FAIL_RATE = Number(process.env.FAIL_RATE || 0);
const DATA_FILE = process.env.DATA_FILE || path.join(__dirname, 'data.json');

// ---- storage ---------------------------------------------------------------
let db = { seq: 0, tasks: {} };
if (fs.existsSync(DATA_FILE)) {
  try { db = JSON.parse(fs.readFileSync(DATA_FILE, 'utf8')); } catch { /* start fresh */ }
}
const persist = () => fs.writeFileSync(DATA_FILE, JSON.stringify(db, null, 2));

const publicTask = ({ seq, ...task }) => task;

function store(id, fields) {
  const existing = db.tasks[id];
  const task = {
    id,
    title: String(fields.title ?? existing?.title ?? ''),
    description: String(fields.description ?? existing?.description ?? ''),
    isCompleted: Boolean(fields.isCompleted ?? existing?.isCompleted ?? false),
    isDeleted: Boolean(fields.isDeleted ?? existing?.isDeleted ?? false),
    updatedAt: Number(fields.updatedAt ?? Date.now()),
    version: (existing?.version ?? 0) + 1,
    seq: ++db.seq, // global, monotonic: drives the change feed (no clock-skew issues)
  };
  db.tasks[id] = task;
  persist();
  return task;
}

// ---- http helpers ----------------------------------------------------------
function send(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(body));
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    let raw = '';
    req.on('data', (c) => { raw += c; if (raw.length > 1e6) req.destroy(); });
    req.on('end', () => { try { resolve(raw ? JSON.parse(raw) : {}); } catch (e) { reject(e); } });
    req.on('error', reject);
  });
}

// ---- routes ----------------------------------------------------------------
async function handle(req, res) {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const parts = url.pathname.split('/').filter(Boolean);

  if (req.method === 'GET' && url.pathname === '/health') return send(res, 200, { ok: true });

  if (FAIL_RATE > 0 && Math.random() < FAIL_RATE) {
    return send(res, 503, { error: 'Simulated outage (FAIL_RATE)' });
  }

  if (req.method === 'GET' && url.pathname === '/tasks') {
    return send(res, 200, Object.values(db.tasks).map(publicTask));
  }

  if (req.method === 'GET' && url.pathname === '/tasks/changes') {
    const since = Number(url.searchParams.get('since') || 0);
    const limit = Math.min(Math.max(Number(url.searchParams.get('limit') || 200), 1), 500);
    const changed = Object.values(db.tasks).filter((t) => t.seq > since).sort((a, b) => a.seq - b.seq);
    const page = changed.slice(0, limit);
    const cursor = page.length ? page[page.length - 1].seq : Math.max(since, 0);
    return send(res, 200, { tasks: page.map(publicTask), cursor, hasMore: changed.length > limit });
  }

  if (req.method === 'PUT' && parts[0] === 'tasks' && parts.length === 2) {
    const id = decodeURIComponent(parts[1]);
    let body;
    try { body = await readJson(req); } catch { return send(res, 400, { error: 'Invalid JSON' }); }
    const { task, baseVersion } = body || {};
    if (!task || typeof baseVersion !== 'number' || (task.id && task.id !== id)) {
      return send(res, 400, { error: 'Expected { task, baseVersion }' });
    }
    const existing = db.tasks[id];
    // Optimistic locking: the client must have seen the latest version.
    if (existing && baseVersion !== existing.version) {
      return send(res, 409, { serverTask: publicTask(existing) });
    }
    return send(res, 200, publicTask(store(id, task)));
  }

  if (req.method === 'POST' && parts[0] === 'debug' && parts[1] === 'edit' && parts[2]) {
    const id = decodeURIComponent(parts[2]);
    if (!db.tasks[id]) return send(res, 404, { error: 'No such task' });
    let body;
    try { body = await readJson(req); } catch { return send(res, 400, { error: 'Invalid JSON' }); }
    return send(res, 200, publicTask(store(id, { ...body, updatedAt: Date.now() })));
  }

  return send(res, 404, { error: 'Not found' });
}

const server = http.createServer((req, res) => {
  handle(req, res).catch((e) => send(res, 500, { error: String(e) }));
  res.on('finish', () => console.log(`${req.method} ${req.url} -> ${res.statusCode}`));
});

if (require.main === module) {
  server.listen(PORT, () => {
    console.log(`TaskNest server on http://localhost:${PORT} (emulator: http://10.0.2.2:${PORT})`);
    if (FAIL_RATE) console.log(`Simulating failures: ${FAIL_RATE * 100}% of requests`);
  });
}

module.exports = { server };
