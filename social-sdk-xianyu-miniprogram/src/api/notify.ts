import { api } from './request'
import type { NotifyMessage, UnreadCount } from '@/types/notify'
import type { PageResponse } from '@/types/common'

export function getMessages() {
  return api.get<PageResponse<NotifyMessage>>('/api/mini/monitor/notify/messages')
}

export function getUnreadCount() {
  return api.get<UnreadCount>('/api/mini/monitor/notify/unread-count')
}

export function markRead(id: number | string) {
  return api.post(`/api/mini/monitor/notify/mark-read`, { id })
}

export function markAllRead() {
  return api.post('/api/mini/monitor/notify/mark-all-read')
}
