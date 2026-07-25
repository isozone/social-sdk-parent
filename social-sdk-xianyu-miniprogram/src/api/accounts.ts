import { api } from './request'
import type { PageResponse } from '@/types/common'
import type { AccountItem } from '@/types/account'

export function getAccounts(params?: any): Promise<PageResponse<AccountItem>> {
  return api.get('/api/mini/accounts', params)
}

export function getAccount(id: number | string): Promise<AccountItem> {
  return api.get(`/api/mini/accounts/${id}`)
}

export function createAccount(data: any): Promise<AccountItem> {
  return api.post('/api/mini/accounts', data)
}

export function updateAccountStatus(id: number | string, enabled: boolean): Promise<any> {
  return api.put(`/api/mini/accounts/${id}/status`, { enabled })
}

export function deleteAccount(id: number | string): Promise<any> {
  return api.delete(`/api/mini/accounts/${id}`)
}

export function syncAccountProfile(id: number | string): Promise<any> {
  return api.post(`/api/mini/accounts/${id}/profile/sync`)
}

export function createQrLogin(): Promise<{ sessionId: string; qrCodeUrl: string }> {
  return api.post('/api/mini/accounts/qr-login')
}

export function getQrLoginStatus(sessionId: string): Promise<{ status: string }> {
  return api.get(`/api/mini/accounts/qr-login/status?sessionId=${sessionId}`)
}

export function launchChrome(id: number | string): Promise<any> {
  return api.post(`/api/mini/accounts/${id}/chrome/launch`)
}

export function stopChrome(id: number | string): Promise<any> {
  return api.post(`/api/mini/accounts/${id}/chrome/stop`)
}

export function getChromeStatus(id: number | string): Promise<any> {
  return api.get(`/api/mini/accounts/${id}/chrome/status`)
}

export function checkChromeAlive(id: number | string): Promise<boolean> {
  return api.get(`/api/mini/accounts/${id}/chrome/alive`)
}
