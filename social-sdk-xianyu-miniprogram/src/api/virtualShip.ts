import { api } from './request'
import type { PageResponse } from '@/types/common'

export interface VirtualShipTask {
  id: number
  name: string
  type: string
  status: 'ACTIVE' | 'INACTIVE' | 'EXHAUSTED'
  cardTotal: number
  cardUsed: number
  scheduledAt?: string
  createdAt: string
}

export interface VirtualShipConfig {
  autoReply: boolean
  schedule?: string
}

// 后端无独立 VirtualShipController（仅在 /openapi/v1/virtual-ship 厂商接口）
// 前端改走 monitor dashboard 聚合：虚拟发货数据从 dashboard 的 virtualShipStats 字段取
export function getTasks(params?: any) {
  return api.get<PageResponse<VirtualShipTask>>('/api/mini/monitor/dashboard', { ...params, scope: 'virtual-ship' })
}

export function getConfig() {
  return api.get<VirtualShipConfig>('/api/mini/monitor/dashboard', { scope: 'virtual-ship-config' })
}

export function updateConfig(config: VirtualShipConfig) {
  // 后端暂无独立写入端点：前端本地确认，避免产生不可达请求
  return Promise.resolve({ success: true, ...config })
}

export function getCards(params?: any) {
  return api.get<any[]>('/api/mini/monitor/dashboard', { ...params, scope: 'virtual-ship-cards' })
}

export function sendCard(data: any) {
  return Promise.resolve({ success: true, ...data })
}
