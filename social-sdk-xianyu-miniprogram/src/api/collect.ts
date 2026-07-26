import { api } from './request'
import type { PageResponse } from '@/types/common'

export interface CollectItem {
  id: number
  accountId: number
  accountName: string
  productUrl: string
  collectType: string
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  message?: string
  createdAt: string
}

export function getList(params?: any) {
  return api.get<PageResponse<CollectItem>>('/api/mini/collect', params)
}

export function sync(id: number | string) {
  return api.post(`/api/mini/collect/${id}/sync`)
}
