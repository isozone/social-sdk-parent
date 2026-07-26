const { getBackendMode, normalizeBaseUrl, isMas } = require('../config/defaults');

function resolveBackendTarget(store) {
  const mode = getBackendMode(store);
  if (mode === 'cloud') {
    return {
      mode,
      baseUrl: normalizeBaseUrl(store.get('cloudBaseUrl', '')),
      startsLocalBackend: false,
      appStoreSafe: true
    };
  }
  return {
    mode: 'local',
    port: Number(store.get('lastPort', 8080)) || 8080,
    baseUrl: `http://127.0.0.1:${Number(store.get('lastPort', 8080)) || 8080}`,
    startsLocalBackend: true,
    appStoreSafe: false
  };
}

function ensureModeAllowed(store) {
  if (isMas && getBackendMode(store) !== 'cloud') {
    store.set('backendMode', 'cloud');
  }
}

module.exports = {
  resolveBackendTarget,
  ensureModeAllowed
};
