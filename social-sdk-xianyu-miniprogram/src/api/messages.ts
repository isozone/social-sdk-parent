import { api } from './request'
import type { ChatSession, Message, SendMessageParams, SyncMessagesParams, FetchMessagesParams } from '@/types/message'
import type { PageResponse } from '@/types/common'

export function getSessions(accountId: number | string, params?: any) {
  return api.get<PageResponse<ChatSession>>('/api/mini/monitor/messages/sessions', { accountId, ...params })
}

export function getSessionHistory(accountId: number | string, sessionId: string) {
  return api.get<Message[]>('/api/mini/monitor/messages/sessions/' + sessionId + '/history', { accountId })
}

export function sendMessage(data: SendMessageParams) {
  return api.post('/api/mini/monitor/messages/send', data)
}

export function syncMessages(params: SyncMessagesParams) {
  return api.post('/api/mini/monitor/messages/sync', params)
}

export function fetchMessages(params: FetchMessagesParams) {
  return api.get<Message[]>('/api/mini/monitor/messages/fetch', params)
}
