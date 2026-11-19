// Run with: node --test   (Node 18+, no dependencies)
const test = require('node:test');
const assert = require('node:assert');
const os = require('os');
const path = require('path');

process.env.DATA_FILE = path.join(os.tmpdir(), `tasknest-test-${process.pid}.json`);
const { server } = require('./server');

let base;
test.before(() => new Promise((r) => server.listen(0, () => { base = `http://localhost:${server.address().port}`; r(); })));
test.after(() => { server.close(); require('fs').rmSync(process.env.DATA_FILE, { force: true }); });

const put = (id, task, baseVersion) =>
  fetch(`${base}/tasks/${id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ task, baseVersion }) });
const t = (id, title, updatedAt, extra = {}) => ({ id, title, description: '', isCompleted: false, isDeleted: false, updatedAt, version: 0, ...extra });

test('create then update with correct baseVersion', async () => {
  let r = await put('a', t('a', 'first', 1), 0);
  assert.strictEqual(r.status, 200);
  assert.strictEqual((await r.json()).version, 1);
  r = await put('a', t('a', 'second', 2), 1);
  assert.strictEqual(r.status, 200);
  const body = await r.json();
  assert.strictEqual(body.version, 2);
  assert.strictEqual(body.title, 'second');
  assert.strictEqual(body.seq, undefined, 'internal seq must not leak');
});

test('stale baseVersion returns 409 with server copy', async () => {
  const r = await put('a', t('a', 'stale edit', 3), 1);
  assert.strictEqual(r.status, 409);
  const { serverTask } = await r.json();
  assert.strictEqual(serverTask.version, 2);
  assert.strictEqual(serverTask.title, 'second');
});

test('change feed pages by sequence and reports hasMore', async () => {
  await put('b', t('b', 'b', 4), 0);
  await put('c', t('c', 'c', 5), 0);
  let r = await (await fetch(`${base}/tasks/changes?since=0&limit=2`)).json();
  // Only the latest write per task is in the feed: a (seq 2), b (seq 3), c (seq 4).
  assert.deepStrictEqual(r.tasks.map((x) => x.id), ['a', 'b']);
  assert.strictEqual(r.hasMore, true);
  r = await (await fetch(`${base}/tasks/changes?since=${r.cursor}&limit=2`)).json();
  assert.deepStrictEqual(r.tasks.map((x) => x.id), ['c']);
  assert.strictEqual(r.hasMore, false);
  const empty = await (await fetch(`${base}/tasks/changes?since=${r.cursor}`)).json();
  assert.deepStrictEqual(empty, { tasks: [], cursor: r.cursor, hasMore: false });
});

test('tombstone delete appears in change feed', async () => {
  const before = await (await fetch(`${base}/tasks/changes?since=0&limit=500`)).json();
  const r = await put('b', t('b', 'b', 6, { isDeleted: true }), 1);
  assert.strictEqual(r.status, 200);
  const after = await (await fetch(`${base}/tasks/changes?since=${before.cursor}`)).json();
  assert.strictEqual(after.tasks.length, 1);
  assert.strictEqual(after.tasks[0].isDeleted, true);
});

test('debug edit simulates another device and bumps version', async () => {
  const r = await fetch(`${base}/debug/edit/c`, { method: 'POST', body: JSON.stringify({ title: 'from laptop' }) });
  const body = await r.json();
  assert.strictEqual(body.version, 2);
  assert.strictEqual(body.title, 'from laptop');
});

test('bad requests are rejected', async () => {
  const r = await fetch(`${base}/tasks/x`, { method: 'PUT', body: '{"nope":1}' });
  assert.strictEqual(r.status, 400);
  const mismatch = await put('x', t('y', 'wrong id', 1), 0);
  assert.strictEqual(mismatch.status, 400);
});
