import { api } from './request'
import type { PageResponse } from '@/types/common'

export interface BuyerItem {
  id: number
  buyerName: string
  avatar?: string
  orderCount: number
  totalAmount: number
  lastContactAt?: string
}

export function getList(params?: any) {
  return api.get<PageResponse<BuyerItem>>('/api/mini/monitor/buyers', params)
}

export function getDetail(id: number | string) {
  return api.get<BuyerItem>(`/api/mini/monitor/buyers/${id}`)
}

export interface TagParams {
  buyerId: number
  tags: string[]
}

export function tag(data: TagParams) {
  return api.post('/api/mini/monitor/buyers/tag', data)
}

export interface NoteParams {
  buyerId: number
  content: string
}

export function addNote(data: NoteParams) {
  return api.post('/api/mini/monitor/buyers/notes', data)
}
