import { api } from './request'
import type { PageResponse } from '@/types/common'

// 后端真实端点：OrderController /api/orders
export function getList(params?: any) {
  return api.get<PageResponse<any>>('/api/mini/orders', params)
}

// 后端真实端点：OrderController /api/orders + @GetMapping("/{id}")
export function getDetail(id: number | string) {
  return api.get<any>(`/api/mini/orders/${id}`)
}

// 后端真实端点：OrderController /api/orders + @PostMapping("/accounts/{accountId}/sync")
export function syncByAccount(accountId: number | string) {
  return api.post(`/api/mini/orders/accounts/${accountId}/sync`)
}

// 后端真实端点：OrderController /api/orders + @GetMapping("/accounts/{accountId}/debug")
export function debugByAccount(accountId: number | string) {
  return api.get(`/api/mini/orders/accounts/${accountId}/debug`)
}

// 后端真实端点：OrderController /api/orders + @PostMapping("/{id}/delivery")
export function delivery(id: number | string, data: any) {
  return api.post(`/api/mini/orders/${id}/delivery`, data)
}
