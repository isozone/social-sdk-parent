import { api } from './request'
import type { PageResponse } from '@/types/common'

export interface BuyerItem {
  id?: number | string
  buyerId?: string
  buyerName?: string
  avatar?: string
  orderCount?: number
  totalAmount?: number
  tags?: string
  notes?: string
  credibilityScore?: number
  lastContactAt?: string
}

// 后端 BuyerController: /api/buyer/*
export function getList(params?: any) {
  return api.get<BuyerItem[] | PageResponse<BuyerItem>>('/api/mini/buyer/list', params)
}

export function getDetail(id: number | string) {
  return api.get<BuyerItem>(`/api/mini/buyer/${id}`)
}

export interface TagParams {
  buyerId: number | string
  tags?: string[]
  tag?: string
}

export function tag(data: TagParams) {
  const body: any = {}
  if (data.tag) body.tag = data.tag
  if (data.tags?.length) body.tags = data.tags
  return api.post(`/api/mini/buyer/${data.buyerId}/tag`, body)
}

export interface NoteParams {
  buyerId: number | string
  note?: string
  notes?: string
}

export function updateNote(data: NoteParams) {
  return api.post(`/api/mini/buyer/${data.buyerId}/notes`, {
    notes: data.notes ?? data.note ?? '',
    note: data.note ?? data.notes ?? '',
  })
}

export function getCredibility(buyerId: number | string) {
  return api.get<number>(`/api/mini/buyer/${buyerId}/credibility`)
}
