const statusText = document.querySelector('#statusText');
const detailText = document.querySelector('#detailText');
const stateDot = document.querySelector('#stateDot');
const ageText = document.querySelector('#ageText');
const demoBanner = document.querySelector('#demoBanner');
const installButton = document.querySelector('#installButton');
const cookTimerForm = document.querySelector('#cookTimerForm');
const cookTimerMinutes = document.querySelector('#cookTimerMinutes');
const cookTimerStatus = document.querySelector('#cookTimerStatus');
const cancelCookTimer = document.querySelector('#cancelCookTimer');
const temperatureChart = document.querySelector('#temperatureChart');
let installPrompt = null;
let cookTimerDeadline = null;
let cookTimerFinishedAnnounced = false;
let temperatureSamples = [];
let graphSource = null;
let lastGraphSampleAt = 0;

function temperature(value) {
  return typeof value === 'number' && Number.isFinite(value) ? value.toFixed(1) : '—';
}

function setTextIfChanged(element, value) {
  if (element.textContent !== value) element.textContent = value;
}

function setReading(currentId, targetId, probe) {
  setTextIfChanged(document.querySelector(`#${currentId}`), temperature(probe?.current_c));
  setTextIfChanged(document.querySelector(`#${targetId}`), `${temperature(probe?.target_c)} °C`);
}

function renderCookTimer(now = Date.now()) {
  if (cookTimerDeadline === null) {
    setTextIfChanged(cookTimerStatus, cookTimerFinishedAnnounced ? 'Timer finished' : 'No local timer running');
    return;
  }
  const remainingSeconds = Math.max(0, Math.ceil((cookTimerDeadline - now) / 1000));
  if (remainingSeconds === 0) {
    setTextIfChanged(cookTimerStatus, 'Timer finished');
    if (!cookTimerFinishedAnnounced) {
      cookTimerFinishedAnnounced = true;
      cookTimerDeadline = null;
    }
    return;
  }
  const hours = Math.floor(remainingSeconds / 3600);
  const minutes = Math.floor((remainingSeconds % 3600) / 60);
  const seconds = remainingSeconds % 60;
  const formatted = hours > 0
    ? `${hours}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
    : `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
  setTextIfChanged(cookTimerStatus, `${formatted} remaining`);
}

function recordTemperatureSample(payload, now = Date.now()) {
  const source = payload?.demo === true ? 'demo' : payload?.source || 'unknown';
  if (graphSource !== null && source !== graphSource) temperatureSamples = [];
  graphSource = source;
  if (now - lastGraphSampleAt < 5000) return;
  const reading = payload?.state === 'live' ? payload.reading : null;
  const sample = { time: now, values: {} };
  for (const [key, field] of [['pit', 'pit'], ['probe1', 'probe1'], ['probe2', 'probe2']]) {
    const item = reading?.[field];
    sample.values[key] = {
      current: Number.isFinite(item?.current_c) ? item.current_c : null,
      target: Number.isFinite(item?.target_c) ? item.target_c : null,
    };
  }
  temperatureSamples.push(sample);
  if (temperatureSamples.length > 720) temperatureSamples.shift();
  lastGraphSampleAt = now;
}

function drawTemperatureChart() {
  const canvas = temperatureChart;
  const context = canvas.getContext('2d');
  const bounds = canvas.getBoundingClientRect();
  const ratio = Math.max(1, window.devicePixelRatio || 1);
  const width = Math.max(1, bounds.width);
  const height = Math.max(180, bounds.height);
  if (canvas.width !== Math.round(width * ratio) || canvas.height !== Math.round(height * ratio)) {
    canvas.width = Math.round(width * ratio);
    canvas.height = Math.round(height * ratio);
  }
  context.setTransform(ratio, 0, 0, ratio, 0, 0);
  context.clearRect(0, 0, width, height);
  const left = 42; const right = width - 8; const top = 8; const bottom = height - 26;
  context.font = '12px system-ui, sans-serif';
  context.fillStyle = '#5a6259';
  for (let tick = 0; tick <= 3; tick += 1) {
    const y = top + (bottom - top) * tick / 3;
    context.strokeStyle = '#dddacf'; context.lineWidth = 1;
    context.beginPath(); context.moveTo(left, y); context.lineTo(right, y); context.stroke();
    context.fillText(`${400 - tick * 100}°`, 2, y + 4);
  }
  context.fillText('older', left, height - 5);
  context.fillText('now', right - 22, height - 5);
  if (temperatureSamples.length < 2) {
    context.fillText('Waiting for fresh samples', left + 8, top + 24);
    canvas.setAttribute('aria-label', 'Temperature graph. Waiting for fresh samples.');
    return;
  }

  const start = temperatureSamples[0].time;
  const end = Math.max(start + 1, temperatureSamples[temperatureSamples.length - 1].time);
  const definitions = [
    ['pit', 'current', '#244a35', false], ['probe1', 'current', '#a56619', false], ['probe2', 'current', '#356a86', false],
    ['pit', 'target', '#244a35', true], ['probe1', 'target', '#a56619', true], ['probe2', 'target', '#356a86', true],
  ];
  for (const [channel, kind, color, dashed] of definitions) {
    context.strokeStyle = color; context.lineWidth = dashed ? 1.5 : 2.5;
    context.setLineDash(dashed ? [5, 4] : []);
    context.beginPath();
    let started = false; let previousTime = null;
    for (const sample of temperatureSamples) {
      const value = sample.values[channel][kind];
      if (!Number.isFinite(value) || value < 0 || value > 400 || (previousTime !== null && sample.time - previousTime > 15_000)) {
        if (started) context.stroke();
        context.beginPath(); started = false;
      } else {
        const x = left + (right - left) * (sample.time - start) / (end - start);
        const y = bottom - (bottom - top) * value / 400;
        if (!started) { context.moveTo(x, y); started = true; } else context.lineTo(x, y);
      }
      previousTime = sample.time;
    }
    if (started) context.stroke();
  }
  context.setLineDash([]);
  const latest = temperatureSamples[temperatureSamples.length - 1].values;
  const summary = ['pit', 'probe1', 'probe2'].map((key) => `${key} ${latest[key].current === null ? 'unavailable' : `${latest[key].current.toFixed(1)} °C`}`).join(', ');
  canvas.setAttribute('aria-label', `Temperature graph, current page session. ${summary}. Dashed lines show reported targets.`);
}

function renderStatus(payload) {
  const state = payload?.state || 'unavailable';
  const live = state === 'live' && payload?.reading;
  const isDemo = payload?.demo === true || payload?.source === 'demo';
  demoBanner.hidden = !isDemo;
  stateDot.className = `state-dot${live ? ' live' : state === 'stale' || state === 'unavailable' || state === 'disconnected' ? ' problem' : ''}`;
  setTextIfChanged(statusText, isDemo ? 'SIMULATION' : live ? 'Live status received' : state === 'stale' ? 'Status is stale' : state === 'disconnected' ? 'Disconnected' : 'Waiting for local gateway');
  setTextIfChanged(detailText, isDemo
    ? 'Fixture data only. No grill is connected.'
    : live
      ? 'Unverified hardware telemetry · monitoring only'
      : 'No fresh reading is shown. Check the local monitor connection.');
  setTextIfChanged(ageText, live && typeof payload.age_seconds === 'number' ? `${Math.floor(payload.age_seconds)}s ago` : '—');

  const reading = live ? payload.reading : null;
  setTextIfChanged(document.querySelector('#pitCurrent'), temperature(reading?.pit?.current_c));
  setTextIfChanged(document.querySelector('#pitTarget'), `${temperature(reading?.pit?.target_c)} °C`);
  setReading('probe1Current', 'probe1Target', reading?.probe1);
  setReading('probe2Current', 'probe2Target', reading?.probe2);
  setTextIfChanged(document.querySelector('#fanStatus'), reading?.fan_mode
    ? reading.fan_mode === 'manual' && typeof reading.fan_percent === 'number'
      ? `Manual · ${reading.fan_percent}% reported`
      : `${reading.fan_mode} reported`
    : 'Unknown');
  const alarm = document.querySelector('#alarmStatus');
  setTextIfChanged(alarm, reading ? reading.alarm ? 'Active' : 'Not reported active' : 'Unknown');
  alarm.classList.toggle('alarm', Boolean(reading?.alarm));
}

async function poll() {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 2500);
  try {
    const response = await fetch('/api/status', { cache: 'no-store', signal: controller.signal });
    if (!response.ok) throw new Error('gateway unavailable');
    const payload = await response.json();
    if (payload.schema_version !== 1) throw new Error('unsupported status schema');
    renderStatus(payload);
    recordTemperatureSample(payload);
  } catch (_error) {
    const payload = { state: 'unavailable', reading: null, demo: false, source: graphSource || 'unknown' };
    renderStatus(payload);
    recordTemperatureSample(payload);
  } finally {
    clearTimeout(timeout);
    renderCookTimer();
    drawTemperatureChart();
    setTimeout(poll, 1200);
  }
}

cookTimerForm.addEventListener('submit', (event) => {
  event.preventDefault();
  const minutes = Number(cookTimerMinutes.value);
  if (!Number.isInteger(minutes) || minutes < 1 || minutes > 1440) {
    cookTimerMinutes.setCustomValidity('Enter a whole number from 1 to 1440 minutes.');
    cookTimerMinutes.reportValidity();
    return;
  }
  cookTimerMinutes.setCustomValidity('');
  cookTimerDeadline = Date.now() + minutes * 60_000;
  cookTimerFinishedAnnounced = false;
  renderCookTimer();
});

cancelCookTimer.addEventListener('click', () => {
  cookTimerDeadline = null;
  cookTimerFinishedAnnounced = false;
  renderCookTimer();
});

window.addEventListener('beforeinstallprompt', (event) => {
  event.preventDefault();
  installPrompt = event;
  installButton.hidden = false;
});

installButton.addEventListener('click', async () => {
  if (!installPrompt) return;
  installPrompt.prompt();
  await installPrompt.userChoice;
  installPrompt = null;
  installButton.hidden = true;
});

if ('serviceWorker' in navigator && window.isSecureContext) {
  navigator.serviceWorker.register('/service-worker.js').catch(() => {});
}

window.addEventListener('resize', drawTemperatureChart);

poll();
