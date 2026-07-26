import { api } from './request'
import type { PageResponse } from '@/types/common'

export interface ReplyLogItem {
  id: number
  messageId: string
  accountId: number
  sessionId: string
  ruleId?: number
  replyContent: string
  createdAt: string
}

export function getList(params?: any) {
  return api.get<PageResponse<ReplyLogItem>>('/api/mini/reply-logs', params)
}
