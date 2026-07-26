import { api } from './request'
import type { NotifyMessage, UnreadCount } from '@/types/notify'
import type { PageResponse } from '@/types/common'

export function getMessages() {
  return api.get<PageResponse<NotifyMessage>>('/api/mini/notify/messages')
}

// 后端真实端点：NotifyMessageController /api/notify/messages + @GetMapping("/unread-count")
export function getUnreadCount() {
  return api.get<UnreadCount>('/api/mini/notify/messages/unread-count')
}

export function markRead(id: number | string) {
  // 后端真实端点：NotifyMessageController /api/notify/messages + @PostMapping("/{id}/read")
  return api.post(`/api/mini/notify/messages/${id}/read`)
}

// 后端真实端点：NotifyMessageController /api/notify/messages + @PostMapping("/read-all")
export function markAllRead() {
  return api.post('/api/mini/notify/messages/read-all')
}
