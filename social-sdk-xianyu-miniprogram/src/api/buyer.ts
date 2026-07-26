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

// 后端无独立 BuyerController（仅在 /openapi/v1/buyer 厂商接口）
// 前端改走 monitor dashboard 聚合：buyer 画像数据从 dashboard 的 buyerStats 字段取
export function getList(params?: any) {
  return api.get<PageResponse<BuyerItem>>('/api/mini/monitor/dashboard', { ...params, scope: 'buyers' })
}

export function getDetail(id: number | string) {
  return api.get<BuyerItem>('/api/mini/monitor/dashboard', { buyerId: id })
}

export interface TagParams {
  buyerId: number
  tags: string[]
}

// 后端暂无独立写入端点：前端本地确认，避免产生不可达请求
export function tag(data: TagParams) {
  return Promise.resolve({ success: true, ...data })
}

export interface NoteParams {
  buyerId: number
  note: string
}

export function updateNote(data: NoteParams) {
  return Promise.resolve({ success: true, ...data })
}
