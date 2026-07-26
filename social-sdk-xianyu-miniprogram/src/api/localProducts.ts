import { api } from './request'
import type { PageResponse } from '@/types/common'

// 后端真实端点：LocalProductController /api/local-products
export function getList(params?: any) {
  return api.get<PageResponse<any>>('/api/mini/local-products', params)
}

// 后端真实端点：LocalProductController /api/local-products + @GetMapping("/{id}")
export function getDetail(id: number | string) {
  return api.get<any>(`/api/mini/local-products/${id}`)
}

// 后端真实端点：LocalProductController /api/local-products + @PutMapping("/{id}")
export function update(id: number | string, data: any) {
  return api.put(`/api/mini/local-products/${id}`, data)
}

// 后端真实端点：LocalProductController /api/local-products + @DeleteMapping("/{id}")
export function remove(id: number | string) {
  return api.delete(`/api/mini/local-products/${id}`)
}

// 后端真实端点：LocalProductController /api/local-products + @PostMapping("/{id}/publish")
export function publish(id: number | string) {
  return api.post(`/api/mini/local-products/${id}/publish`)
}

// 后端真实端点：LocalProductController /api/local-products + @PostMapping("/batch-publish")
export function batchPublish(data: any) {
  return api.post('/api/mini/local-products/batch-publish', data)
}
