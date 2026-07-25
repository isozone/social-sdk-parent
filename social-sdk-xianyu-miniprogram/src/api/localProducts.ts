import { api } from './request'
import type { LocalProductItem, PublishProductParams } from '@/types/product'
import type { PageResponse } from '@/types/common'

export function getLocalProducts(params?: any) {
  return api.get<PageResponse<LocalProductItem>>('/api/mini/monitor/local-products', params)
}

export function getLocalProduct(id: number | string) {
  return api.get<LocalProductItem>(`/api/mini/monitor/local-products/${id}`)
}

export function createLocalProduct(data: Omit<PublishProductParams, 'categoryId'> & { categoryId?: string }) {
  return api.post<LocalProductItem>('/api/mini/monitor/local-products', data)
}

export function updateLocalProduct(id: number | string, data: Partial<LocalProductItem>) {
  return api.put<LocalProductItem>(`/api/mini/monitor/local-products/${id}`, data)
}

export function deleteLocalProduct(id: number | string) {
  return api.delete(`/api/mini/monitor/local-products/${id}`)
}

export function publish(id: number | string) {
  return api.post(`/api/mini/monitor/local-products/${id}/publish`)
}

export function batchPublish(ids: number[]) {
  return api.post('/api/mini/monitor/local-products/batch-publish', { ids })
}
