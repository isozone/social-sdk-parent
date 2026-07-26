import { api } from './request'
import type { PageResponse } from '@/types/common'

// 后端真实端点：CloudStorageController /api/cloud-storage
// 后端无独立 /storage/accounts 端点，用 /api/cloud-storage/accounts 聚合
export function getAccounts(params?: any) {
  return api.get<PageResponse<any>>('/api/mini/cloud-storage/accounts', params)
}

// 后端真实端点：CloudStorageController /api/cloud-storage + @GetMapping("/accounts/{id}")
export function getAccount(id: number | string) {
  return api.get<any>(`/api/mini/cloud-storage/accounts/${id}`)
}

// 后端真实端点：CloudStorageController /api/cloud-storage + @PutMapping("/accounts/{id}")
export function updateAccount(id: number | string, data: any) {
  return api.put(`/api/mini/cloud-storage/accounts/${id}`, data)
}

// 后端真实端点：CloudStorageController /api/cloud-storage + @DeleteMapping("/accounts/{id}")
export function removeAccount(id: number | string) {
  return api.delete(`/api/mini/cloud-storage/accounts/${id}`)
}

// 后端真实端点：CloudStorageController /api/cloud-storage + @GetMapping("/auth-url")
export function getAuthUrl(params?: any) {
  return api.get<any>('/api/mini/cloud-storage/auth-url', params)
}

// 后端真实端点：CloudStorageController /api/cloud-storage + @GetMapping("/files")
export function getFiles(params?: any) {
  return api.get<PageResponse<any>>('/api/mini/cloud-storage/files', params)
}

// 后端真实端点：CloudStorageController /api/cloud-storage + @GetMapping("/files/{fileId}")
export function getFile(fileId: number | string) {
  return api.get<any>(`/api/mini/cloud-storage/files/${fileId}`)
}

// 后端真实端点：CloudStorageController /api/cloud-storage + @PostMapping("/accounts/{storageAccountId}/files")
export function uploadFile(storageAccountId: number | string, data: any) {
  return api.post(`/api/mini/cloud-storage/accounts/${storageAccountId}/files`, data)
}

// 后端真实端点：CloudStorageController /api/cloud-storage + @PostMapping("/files/{fileId}/share")
export function shareFile(fileId: number | string) {
  return api.post(`/api/mini/cloud-storage/files/${fileId}/share`)
}

// 后端真实端点：CloudStorageController /api/cloud-storage + @DeleteMapping("/files/{fileId}/share")
export function unshareFile(fileId: number | string) {
  return api.delete(`/api/mini/cloud-storage/files/${fileId}/share`)
}
