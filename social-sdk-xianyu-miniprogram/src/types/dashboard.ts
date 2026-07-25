export interface DashboardData {
  kpiList: KpiItem[]
  accountStats: DashboardAccountStat[]
  orderTrend: TrendData[]
  messageActivity: TrendData[]
}

export interface KpiItem {
  label: string
  value: number
  unit: string
  trend: number
  trendDirection: 'up' | 'down'
}

export interface DashboardAccountStat {
  accountId: number
  displayName: string
  status: string
  orderCount: number
  messageCount: number
}

export interface TrendData {
  date: string
  value: number
}

export interface AccountStatsParams {
  accountId?: number
  page?: number
  size?: number
}
