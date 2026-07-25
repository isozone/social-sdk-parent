import { api } from './request'
import type { OrderItem, DeliveryParams, OrderStats } from '@/types/order'
import type { PageResponse } from '@/types/common'

export function getOrders(params?: any) {
  return api.get<PageResponse<OrderItem>>('/api/mini/monitor/orders', params)
}

export function getOrder(id: number | string) {
  return api.get<OrderItem>(`/api/mini/monitor/orders/${id}`)
}

export function delivery(id: number | string, data: DeliveryParams) {
  return api.post(`/api/mini/monitor/orders/${id}/delivery`, data)
}

export function syncOrders() {
  return api.post('/api/mini/monitor/orders/sync')
}

export function getOrderStats() {
  return api.get<OrderStats>('/api/mini/monitor/orders/stats')
}
