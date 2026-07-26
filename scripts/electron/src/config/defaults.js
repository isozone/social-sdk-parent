const isMas = process.mas === true || process.env.ELECTRON_DISTRIBUTION === 'mas';

const defaults = {
  windowBounds: { width: 1200, height: 800 },
  autoStart: false,
  minimizeToTray: !isMas,
  startInTray: false,
  backendMode: isMas ? 'cloud' : 'local',
  cloudBaseUrl: '',
  lastPort: 8080,
  javaPath: 'java'
};

function normalizeBaseUrl(value) {
  const raw = String(value || '').trim();
  if (!raw) return '';
  return raw.replace(/\/+$/, '');
}

function getBackendMode(store) {
  const mode = store.get('backendMode', defaults.backendMode);
  if (mode === 'cloud') return 'cloud';
  return 'local';
}

module.exports = {
  defaults,
  isMas,
  normalizeBaseUrl,
  getBackendMode
};
