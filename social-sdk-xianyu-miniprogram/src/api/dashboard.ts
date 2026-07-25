import { api } from './request'
import type { PageResponse } from '@/types/common'
import type { DashboardData, AccountStatsParams } from '@/types/dashboard'
import type { AccountItem } from '@/types/account'

export function getDashboard(accountId?: number | string) {
  return api.get<DashboardData>('/api/mini/monitor/dashboard', accountId ? { accountId } : undefined)
}

export function getAccountStats(params?: AccountStatsParams) {
  return api.get<PageResponse<AccountItem>>('/api/mini/monitor/accounts', params)
}
