import { api } from './request'

export function runCookieRenew() {
  return api.post('/api/mini/account/cookie-renew/run')
}
export function getCookieRenewLogs(params?: any) {
  return api.get<any>('/api/mini/account/cookie-renew/logs', params)
}
export function runLoginRenew() {
  return api.post('/api/mini/account/login-renew/run')
}
export function getLoginRenewLogs(params?: any) {
  return api.get<any>('/api/mini/account/login-renew/logs', params)
}
export function runTokenRenewal() {
  return api.post('/api/mini/account/token-renewal/run')
}
export function getTokenRenewalLogs(params?: any) {
  return api.get<any>('/api/mini/account/token-renewal/logs', params)
}
