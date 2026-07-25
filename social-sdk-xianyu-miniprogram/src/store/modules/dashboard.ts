// Dashboard Store - KPI data + trend cache
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { DashboardData, KpiItem, TrendData } from '@/types/dashboard'
import api from '@/api'

export const useDashboardStore = defineStore('dashboard', () => {
  const data = ref<DashboardData | null>(null)
  const loading = ref(false)
  const lastRefreshedAt = ref<number>(0)
  const CACHE_TTL = 30_000 // 30s

  const kpis = computed(() => data.value?.kpiList ?? [])
  const accountStats = computed(() => data.value?.accountStats ?? [])
  const orderTrend = computed(() => data.value?.orderTrend ?? [])
  const messageActivity = computed(() => data.value?.messageActivity ?? [])

  async function refresh(accountId?: number) {
    if (data.value && Date.now() - lastRefreshedAt.value < CACHE_TTL) return
    loading.value = true
    try {
      const res = await api.get<any>('/api/mini/monitor/dashboard', accountId ? { accountId } : undefined, false)
      data.value = res as DashboardData
      lastRefreshedAt.value = Date.now()
    } finally {
      loading.value = false
    }
  }

  async function fetchAccountStats(accountId?: number) {
    return api.get<any>('/api/mini/monitor/accounts', accountId ? { accountId } : undefined, false)
  }

  return { data, loading, lastRefreshedAt, kpis, accountStats, orderTrend, messageActivity, refresh, fetchAccountStats }
})
