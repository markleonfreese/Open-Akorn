const statusText = document.querySelector('#statusText');
const detailText = document.querySelector('#detailText');
const stateDot = document.querySelector('#stateDot');
const ageText = document.querySelector('#ageText');
const demoBanner = document.querySelector('#demoBanner');
const installButton = document.querySelector('#installButton');
let installPrompt = null;

function temperature(value) {
  return typeof value === 'number' && Number.isFinite(value) ? value.toFixed(1) : '—';
}

function setReading(currentId, targetId, probe) {
  document.querySelector(`#${currentId}`).textContent = temperature(probe?.current_c);
  document.querySelector(`#${targetId}`).textContent = `${temperature(probe?.target_c)} °C`;
}

function renderStatus(payload) {
  const state = payload?.state || 'unavailable';
  const live = state === 'live' && payload?.reading;
  const isDemo = payload?.demo === true || payload?.source === 'demo';
  demoBanner.hidden = !isDemo;
  stateDot.className = `state-dot${live ? ' live' : state === 'stale' || state === 'unavailable' || state === 'disconnected' ? ' problem' : ''}`;
  statusText.textContent = isDemo ? 'SIMULATION' : live ? 'Live status received' : state === 'stale' ? 'Status is stale' : state === 'disconnected' ? 'Disconnected' : 'Waiting for local gateway';
  detailText.textContent = isDemo
    ? 'Fixture data only. No grill is connected.'
    : live
      ? 'Unverified hardware telemetry · monitoring only'
      : 'No fresh reading is shown. Check the local monitor connection.';
  ageText.textContent = live && typeof payload.age_seconds === 'number' ? `${Math.floor(payload.age_seconds)}s ago` : '—';

  const reading = live ? payload.reading : null;
  document.querySelector('#pitCurrent').textContent = temperature(reading?.pit?.current_c);
  document.querySelector('#pitTarget').textContent = `${temperature(reading?.pit?.target_c)} °C`;
  setReading('probe1Current', 'probe1Target', reading?.probe1);
  setReading('probe2Current', 'probe2Target', reading?.probe2);
  document.querySelector('#fanStatus').textContent = reading?.fan_mode
    ? reading.fan_mode === 'manual' && typeof reading.fan_percent === 'number'
      ? `Manual · ${reading.fan_percent}% reported`
      : `${reading.fan_mode} reported`
    : 'Unknown';
  const alarm = document.querySelector('#alarmStatus');
  alarm.textContent = reading ? reading.alarm ? 'Active' : 'Not reported active' : 'Unknown';
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
  } catch (_error) {
    renderStatus({ state: 'unavailable', reading: null, demo: false });
  } finally {
    clearTimeout(timeout);
    setTimeout(poll, 1200);
  }
}

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

poll();
