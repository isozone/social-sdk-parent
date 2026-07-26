const fetch = require('node-fetch');
const { normalizeBaseUrl } = require('../config/defaults');

async function waitForCloudBackend(baseUrl, timeout = 30000) {
  const base = normalizeBaseUrl(baseUrl);
  if (!base) return { ready: false, reason: '未配置云端后台基础 URL' };

  const start = Date.now();
  const candidates = [
    `${base}/actuator/health`,
    `${base}/api/auth/profile`,
    `${base}/`
  ];

  while (Date.now() - start < timeout) {
    for (const url of candidates) {
      try {
        const res = await fetch(url, { timeout: 5000 });
        if (res.status < 500) return { ready: true, url: base };
      } catch {}
    }
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  return { ready: false, reason: `云端后台不可达: ${base}` };
}

module.exports = {
  waitForCloudBackend
};
