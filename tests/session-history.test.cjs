const test = require('node:test');
const assert = require('node:assert/strict');
const { createSessionHistory, STORAGE_KEY } = require('../pwa/session-history.js');

function memoryStorage(initial = null) {
  let value = initial;
  return {
    getItem: () => value,
    setItem: (_key, next) => { value = next; },
    value: () => value,
  };
}

function livePayload(source = 'tcp', current = 100) {
  const probe = (value) => ({ current_c: value, target_c: value + 10 });
  return {
    source,
    demo: false,
    state: 'live',
    reading: { pit: probe(current), probe1: probe(current - 30), probe2: probe(current - 20) },
  };
}

test('local history stores only fresh real readings and excludes simulation', () => {
  const storage = memoryStorage();
  const history = createSessionHistory(storage);
  assert.equal(history.record({ ...livePayload(), demo: true }, 1_000), false);
  assert.equal(history.record({ ...livePayload(), state: 'disconnected' }, 2_000), false);
  assert.deepEqual(history.list(), []);
  assert.equal(storage.value(), null);

  assert.equal(history.record(livePayload(), 10_000), true);
  assert.equal(history.record(livePayload('tcp', 120), 20_000), false);
  assert.equal(history.record(livePayload('tcp', 120), 70_000), true);
  const session = history.list()[0];
  assert.equal(session.samples.length, 2);
  assert.equal(session.samples[1].values.pit.current, 120);
  assert.equal(session.samples[1].offsetMs, 60_000);
  assert.equal(JSON.parse(storage.value()).length, 1);
});

test('changing transport closes the prior session and starts a new local record', () => {
  const history = createSessionHistory(memoryStorage());
  history.record(livePayload('tcp'), 100_000);
  history.record(livePayload('ble', 130), 160_000);
  const sessions = history.list();
  assert.equal(sessions.length, 2);
  assert.equal(sessions[0].source, 'tcp');
  assert.equal(sessions[0].endedAt, 160_000);
  assert.equal(sessions[1].source, 'ble');
  assert.equal(sessions[1].endedAt, null);
});

test('reload closes interrupted sessions and retains a bounded count', () => {
  const storage = memoryStorage();
  const history = createSessionHistory(storage, { maxSessions: 2 });
  history.record(livePayload(), 1_000);
  const restored = createSessionHistory(storage, { maxSessions: 2 });
  assert.equal(restored.list()[0].endedAt, 1_000);

  restored.record(livePayload(), 10_000);
  restored.finish(20_000);
  restored.record(livePayload(), 30_000);
  restored.finish(40_000);
  restored.record(livePayload(), 50_000);
  assert.equal(restored.list().length, 2);
  assert.equal(restored.list()[0].startedAt, 30_000);
});

test('sample count is capped and invalid temperatures become unavailable', () => {
  const history = createSessionHistory(memoryStorage(), { sampleIntervalMs: 1 });
  for (let index = 0; index < 725; index += 1) {
    history.record(livePayload('tcp', index === 0 ? 500 : 100), index * 1_000);
  }
  const samples = history.list()[0].samples;
  assert.equal(samples.length, 720);
  assert.equal(samples.at(-1).values.pit.current, 100);
  assert.equal(samples[0].values.pit.current, 100);
});

test('clear removes local records and storage failures do not break monitoring', () => {
  const storage = memoryStorage();
  const history = createSessionHistory(storage);
  history.record(livePayload(), 1_000);
  history.clear();
  assert.deepEqual(history.list(), []);
  assert.deepEqual(JSON.parse(storage.value()), []);
  assert.equal(STORAGE_KEY, 'open-akorn-cook-sessions-v1');

  const failing = { getItem: () => null, setItem: () => { throw new Error('quota'); } };
  const noStorage = createSessionHistory(failing);
  assert.doesNotThrow(() => noStorage.record(livePayload(), 1_000));
  assert.equal(noStorage.storageAvailable(), false);
});
