import { api } from './request'
import type { ChatSession, Message, SendMessageParams, SyncMessagesParams, FetchMessagesParams } from '@/types/message'
import type { PageResponse } from '@/types/common'

export function getSessions(accountId: number | string, params?: any) {
  return api.get<PageResponse<ChatSession>>('/api/mini/messages/sessions', { accountId, ...params })
}

export function getSessionHistory(accountId: number | string, sessionId: string) {
  return api.get<Message[]>('/api/mini/messages/history', { accountId, sessionId })
}

export function sendMessage(data: SendMessageParams) {
  return api.post('/api/mini/messages/send', data)
}

export function syncMessages(params: SyncMessagesParams) {
  return api.post('/api/mini/messages/sync', params)
}

export function fetchMessages(params: FetchMessagesParams) {
  return api.get<Message[]>('/api/mini/messages/fetch', params)
}
