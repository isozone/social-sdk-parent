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

export function getTasks(params?: any) {
  return api.get<PageResponse<VirtualShipTask>>('/api/mini/monitor/virtual-ship/tasks', params)
}

export function getConfig() {
  return api.get<VirtualShipConfig>('/api/mini/monitor/virtual-ship/config')
}

export function updateConfig(config: VirtualShipConfig) {
  return api.put('/api/mini/monitor/virtual-ship/config', config)
}

export function getCards(accountId: number | string) {
  return api.get<any[]>('/api/mini/monitor/virtual-ship/cards', { accountId })
}

export function sendCard(accountId: number | string, cardId: number) {
  return api.post('/api/mini/monitor/virtual-ship/send-card', { accountId, cardId })
}
