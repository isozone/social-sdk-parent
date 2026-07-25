import { api } from './request'
import type { PageResponse } from '@/types/common'

export interface AuditLog {
  id: number
  userId: number
  username: string
  action: string
  target: string
  result: 'success' | 'failed'
  ip: string
  createdAt: string
}

export function getLogs(params?: any) {
  return api.get<PageResponse<AuditLog>>('/api/mini/audit/logs', params)
}
