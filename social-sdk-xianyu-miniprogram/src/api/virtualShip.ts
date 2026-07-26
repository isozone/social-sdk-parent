import { api } from './request'
import type { PageResponse } from '@/types/common'

export interface VirtualShipTask {
  id: number
  accountId?: number
  orderId?: number
  productId?: number
  status?: string
  name?: string
  type?: string
  cardTotal?: number
  cardUsed?: number
  scheduledAt?: string
  executeAt?: string
  createdAt?: string
  errorMessage?: string
}

export interface VirtualShipConfig {
  id?: number
  accountId?: number
  enabled?: boolean
  delaySeconds?: number
  autoConfirmDays?: number
  notifyAfterShip?: boolean
  autoReply?: boolean
  schedule?: string
}

// 后端 VirtualShipController: /api/virtual-ship/*
export function getTasks(params?: any) {
  return api.get<VirtualShipTask[] | PageResponse<VirtualShipTask>>('/api/mini/virtual-ship/tasks', params)
}

export function getConfig(accountId: number | string) {
  return api.get<VirtualShipConfig>('/api/mini/virtual-ship/config', { accountId })
}

export function updateConfig(config: VirtualShipConfig & { accountId: number | string }) {
  // 有 id 时走 PUT 局部更新；否则 POST 创建/全量保存
  if (config.id) {
    return api.put(`/api/mini/virtual-ship/config/${config.id}`, config)
  }
  return api.post('/api/mini/virtual-ship/config', config)
}

export function getCards(params?: any) {
  return api.get<any[]>('/api/mini/virtual-ship/cards', params)
}

export function importCards(data: { productId: number | string; cards: string[] }) {
  return api.post('/api/mini/virtual-ship/cards/import', data)
}

/** 手动发卡 / 触发发货：taskId 或 orderId 二选一 */
export function sendCard(data: { taskId?: number | string; orderId?: number | string }) {
  return api.post('/api/mini/virtual-ship/cards/send', data)
}

export function triggerTask(taskId: number | string) {
  return api.post(`/api/mini/virtual-ship/tasks/${taskId}/trigger`)
}

export function retryTask(taskId: number | string) {
  return api.post('/api/mini/virtual-ship/tasks/retry', { taskId })
}
