import { api } from './request'
import type { ChatSession, Message, SendMessageParams, SyncMessagesParams, FetchMessagesParams } from '@/types/message'
import type { PageResponse } from '@/types/common'

// 后端真实端点：MessageController /api/messages + @GetMapping("/sessions")
export function getSessions(accountId: number | string, params?: any) {
  return api.get<PageResponse<ChatSession>>('/api/mini/messages/sessions', { accountId, ...params })
}

// 后端真实端点：MessageController /api/messages + @GetMapping("/history")
export function getSessionHistory(accountId: number | string, sessionId: string) {
  return api.get<Message[]>('/api/mini/messages/history', { accountId, sessionId })
}

// 后端真实端点：MessageController /api/messages + @PostMapping("/send")
export function sendMessage(data: SendMessageParams) {
  return api.post('/api/mini/messages/send', data)
}

// 后端真实端点：MessageController /api/messages + @PostMapping("/sync")
export function syncMessages(params: SyncMessagesParams) {
  return api.post('/api/mini/messages/sync', params)
}

// 后端真实端点：MessageController /api/messages + @GetMapping("/list")
// 后端无 /fetch 端点，用 /list 聚合消息列表
export function fetchMessages(params: FetchMessagesParams) {
  return api.get<Message[]>('/api/mini/messages/list', params)
}
