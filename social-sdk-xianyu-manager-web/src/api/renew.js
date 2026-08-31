import api from '@/api/request'

// ===== Cookie 刷新日志（A1） =====
export function listCookieRenewLogs(params) {
  return api.get('/account/cookie-renew/logs', { params })
}

export function runCookieRenew() {
  return api.post('/account/cookie-renew/run')
}

// ===== 登录续期日志（A3） =====
export function listLoginRenewLogs(params) {
  return api.get('/account/login-renew/logs', { params })
}

export function runLoginRenew() {
  return api.post('/account/login-renew/run')
}

// ===== Token 续期日志（A4） =====
export function listTokenRenewalLogs(params) {
  return api.get('/account/token-renewal/logs', { params })
}

export function runTokenRenewal() {
  return api.post('/account/token-renewal/run')
}
