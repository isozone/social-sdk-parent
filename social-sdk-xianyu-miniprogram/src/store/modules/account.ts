// Account Store - current selected Xianyu account
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { AccountItem } from '@/types/account'
import api from '@/api'

export const useAccountStore = defineStore('account', () => {
  const current = ref<AccountItem | null>(null)
  const list = ref<AccountItem[]>([])
  const loading = ref(false)

  const accountId = computed(() => current.value?.id)

  // Load persisted account id
  try {
    const savedId = uni.getStorageSync('aiyudb_accountId')
    if (savedId) {
      // load full account later
    }
  } catch {}

  async function setAccount(accountId: number | string) {
    try {
      const acc = await api.get<AccountItem>(`/api/mini/accounts/${accountId}`)
      current.value = acc
      uni.setStorageSync('aiyudb_accountId', String(acc.id))
    } catch (e) {
      uni.showToast({ title: '加载账号失败', icon: 'none' })
    }
  }

  // setCurrent 别名：直接传整个 account 对象（AccountSwitcher 等组件期望此签名）
  async function setCurrent(acc: AccountItem) {
    current.value = acc
    uni.setStorageSync('aiyudb_accountId', String(acc.id))
  }

  async function clearAccount() {
    current.value = null
    uni.removeStorageSync('aiyudb_accountId')
  }

  async function fetchList(page = 1, size = 20) {
    loading.value = true
    try {
      const res = await api.get<any>('/api/mini/accounts', { page, size }, false)
      if (res.records) list.value = res.records
    } finally {
      loading.value = false
    }
  }

  return { current, list, loading, accountId, setAccount, setCurrent, clearAccount, fetchList }
})
