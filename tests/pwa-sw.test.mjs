import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import vm from 'node:vm';

const workerSource = await readFile(new URL('../pwa/service-worker.js', import.meta.url), 'utf8');

function loadWorker(fetchImpl = async () => ({ ok: true })) {
  const listeners = {};
  const cacheCalls = { added: [], matched: 0, opened: [], deleted: [] };
  const cache = { addAll: async (paths) => { cacheCalls.added = Array.from(paths); } };
  const caches = {
    open: async (name) => { cacheCalls.opened.push(name); return cache; },
    keys: async () => ['open-akorn-shell-v1', 'other-cache'],
    delete: async (name) => { cacheCalls.deleted.push(name); return true; },
    match: async () => { cacheCalls.matched += 1; return { source: 'cache' }; },
  };
  const self = {
    location: { origin: 'http://127.0.0.1:8080' },
    clients: { claim: async () => {} },
    addEventListener: (name, callback) => { listeners[name] = callback; },
    skipWaiting: async () => {},
  };
  vm.runInNewContext(workerSource, { self, caches, URL, fetch: fetchImpl, Promise });
  return { listeners, cacheCalls };
}

test('install cache contains only static shell assets', async () => {
  const { listeners, cacheCalls } = loadWorker();
  let installPromise;
  listeners.install({ waitUntil: (promise) => { installPromise = promise; } });
  await installPromise;
  assert.deepEqual(cacheCalls.opened, ['open-akorn-shell-v2']);
  assert.ok(cacheCalls.added.includes('/'));
  assert.ok(cacheCalls.added.includes('/app.js'));
  assert.ok(!cacheCalls.added.some((path) => path.startsWith('/api/')));
});

test('activation removes obsolete app shell caches and keeps unrelated caches', async () => {
  const { listeners, cacheCalls } = loadWorker();
  let activationPromise;
  listeners.activate({ waitUntil: (promise) => { activationPromise = promise; } });
  await activationPromise;
  assert.deepEqual(cacheCalls.deleted, ['open-akorn-shell-v1']);
});

test('status request always uses network with no-store and never reads cache', async () => {
  const calls = [];
  const { listeners, cacheCalls } = loadWorker(async (request, options) => {
    calls.push({ request, options });
    return { state: 'live' };
  });
  let response;
  listeners.fetch({
    request: { method: 'GET', url: 'http://127.0.0.1:8080/api/status' },
    respondWith: (promise) => { response = promise; },
  });
  await response;
  assert.equal(calls.length, 1);
  assert.equal(calls[0].options.cache, 'no-store');
  assert.equal(cacheCalls.matched, 0);
});

test('offline status request rejects instead of returning cached telemetry', async () => {
  const { listeners, cacheCalls } = loadWorker(async () => { throw new TypeError('offline'); });
  let response;
  listeners.fetch({
    request: { method: 'GET', url: 'http://127.0.0.1:8080/api/status' },
    respondWith: (promise) => { response = promise; },
  });
  await assert.rejects(response, /offline/);
  assert.equal(cacheCalls.matched, 0);
});

test('static shell reads cache while non-GET requests are left alone', async () => {
  const calls = [];
  const { listeners, cacheCalls } = loadWorker(async (...args) => { calls.push(args); return {}; });
  let staticResponse;
  listeners.fetch({
    request: { method: 'GET', url: 'http://127.0.0.1:8080/app.js' },
    respondWith: (promise) => { staticResponse = promise; },
  });
  assert.deepEqual(await staticResponse, { source: 'cache' });
  assert.equal(cacheCalls.matched, 1);

  let responded = false;
  listeners.fetch({
    request: { method: 'POST', url: 'http://127.0.0.1:8080/api/status' },
    respondWith: () => { responded = true; },
  });
  assert.equal(responded, false);
  assert.equal(calls.length, 0);
});
