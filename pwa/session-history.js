(function attachSessionHistory(global) {
  const STORAGE_KEY = 'open-akorn-cook-sessions-v1';
  const MAX_SESSIONS = 8;
  const MAX_SAMPLES = 720;
  const SAMPLE_INTERVAL_MS = 60_000;
  const MIN_C = -17.8;
  const MAX_C = 398.9;

  function validTemperature(value) {
    return typeof value === 'number' && Number.isFinite(value) && value >= MIN_C && value <= MAX_C
      ? Math.round(value * 10) / 10
      : null;
  }

  function cleanSample(sample) {
    if (!sample || !Number.isSafeInteger(sample.offsetMs) || sample.offsetMs < 0) return null;
    const values = {};
    for (const key of ['pit', 'probe1', 'probe2']) {
      const value = sample.values?.[key];
      values[key] = {
        current: validTemperature(value?.current),
        target: validTemperature(value?.target),
      };
    }
    return { offsetMs: sample.offsetMs, values };
  }

  function cleanSession(session) {
    if (!session || typeof session.id !== 'string' || !Number.isSafeInteger(session.startedAt) ||
        session.startedAt < 0 || !['tcp', 'ble'].includes(session.source) || !Array.isArray(session.samples)) return null;
    const samples = session.samples.slice(-MAX_SAMPLES).map(cleanSample);
    if (samples.some((sample) => sample === null)) return null;
    let endedAt = session.endedAt;
    if (endedAt !== null && (!Number.isSafeInteger(endedAt) || endedAt < session.startedAt)) return null;
    if (endedAt === null) endedAt = session.startedAt + (samples.at(-1)?.offsetMs || 0);
    return { id: session.id, startedAt: session.startedAt, endedAt, source: session.source, samples };
  }

  function createSessionHistory(storage, options = {}) {
    const maxSessions = options.maxSessions || MAX_SESSIONS;
    const sampleIntervalMs = options.sampleIntervalMs || SAMPLE_INTERVAL_MS;
    let storageAvailable = Boolean(storage);
    let sessions = [];
    try {
      const parsed = JSON.parse(storage?.getItem(STORAGE_KEY) || '[]');
      if (Array.isArray(parsed)) sessions = parsed.map(cleanSession).filter(Boolean).slice(-maxSessions);
    } catch (_error) {
      sessions = [];
      storageAvailable = false;
    }

    function persist() {
      try {
        storage?.setItem(STORAGE_KEY, JSON.stringify(sessions.slice(-maxSessions)));
      } catch (_error) {
        storageAvailable = false;
      }
    }

    // A page reload closes the prior in-memory connection session at its last sample.
    if (sessions.some((session) => session.endedAt === null)) {
      sessions = sessions.map((session) => session.endedAt === null
        ? { ...session, endedAt: session.startedAt + (session.samples.at(-1)?.offsetMs || 0) }
        : session);
      persist();
    }

    function finish(now = Date.now()) {
      const active = sessions.at(-1);
      if (!active || active.endedAt !== null) return false;
      const lastSampleAt = active.startedAt + (active.samples.at(-1)?.offsetMs || 0);
      active.endedAt = Math.max(now, lastSampleAt);
      persist();
      return true;
    }

    function record(payload, now = Date.now()) {
      const reading = payload?.reading;
      if (payload?.demo === true || payload?.state !== 'live' || !reading) {
        return finish(now);
      }
      const source = payload.source === 'tcp' || payload.source === 'ble' ? payload.source : null;
      if (!source || !Number.isSafeInteger(now) || now < 0) {
        return finish(now);
      }
      let active = sessions.at(-1);
      if (active && active.endedAt === null && active.source !== source) {
        finish(now);
        active = null;
      }
      if (!active || active.endedAt !== null) {
        active = { id: `${now}-${source}`, startedAt: now, endedAt: null, source, samples: [] };
        sessions.push(active);
      }
      const previous = active.samples.at(-1);
      const lastSampleAt = active.startedAt + (previous?.offsetMs || 0);
      if (previous && (now < lastSampleAt || now - lastSampleAt < sampleIntervalMs)) return false;
      const sample = { offsetMs: now - active.startedAt, values: {} };
      for (const [key, field] of [['pit', 'pit'], ['probe1', 'probe1'], ['probe2', 'probe2']]) {
        const item = reading[field];
        sample.values[key] = {
          current: validTemperature(item?.current_c),
          target: validTemperature(item?.target_c),
        };
      }
      active.samples.push(sample);
      if (active.samples.length > MAX_SAMPLES) active.samples.shift();
      while (sessions.length > maxSessions) sessions.shift();
      persist();
      return true;
    }

    function list() {
      return sessions.map((session) => ({
        ...session,
        samples: session.samples.map((sample) => ({
          offsetMs: sample.offsetMs,
          values: Object.fromEntries(Object.entries(sample.values).map(([key, value]) => [key, { ...value }])),
        })),
      }));
    }

    function clear() {
      sessions = [];
      persist();
    }

    return {
      record,
      finish,
      list,
      clear,
      storageAvailable: () => storageAvailable,
      maxSessions,
      maxSamplesPerSession: MAX_SAMPLES,
      sampleIntervalMs,
    };
  }

  global.OpenAkornSessionHistory = { createSessionHistory, STORAGE_KEY };
  if (typeof module !== 'undefined' && module.exports) module.exports = global.OpenAkornSessionHistory;
}(globalThis));
