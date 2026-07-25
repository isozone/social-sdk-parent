import { api } from './request'

export interface CloudStorageAccount {
  id: number
  provider: string
  bucketName: string
  region: string
  status: 'connected' | 'disconnected'
  createdAt: string
}

export interface CloudStorageFile {
  key: string
  size: number
  lastModified: string
  url?: string
}

export function getAccounts() {
  return api.get<CloudStorageAccount[]>('/api/mini/storage/accounts')
}

export function getFileList(accountId: number | string) {
  return api.get<CloudStorageFile[]>(`/api/mini/storage/files?accountId=${accountId}`)
}

export function deleteFile(accountId: number | string, key: string) {
  return api.delete(`/api/mini/storage/files?accountId=${accountId}&key=${key}`)
}

export function getOAuthUrl(provider: string) {
  return api.get<{ url: string }>(`/api/mini/storage/oauth/${provider}`)
}
