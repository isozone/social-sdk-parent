// Auth Store - JWT Token + Admin User Profile
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { AdminUser } from '@/types/common'
import api from '@/api'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>('')
  const profile = ref<AdminUser | null>(null)
  const isRefreshing = ref(false)

  const isLoggedIn = computed(() => !!token.value && !!profile.value)

  // Load persisted state on init
  try {
    const savedToken = uni.getStorageSync('aiyudb_token') || ''
    const savedProfile = uni.getStorageSync('aiyudb_profile')
    if (savedToken && savedProfile) {
      token.value = savedToken
      profile.value = JSON.parse(savedProfile)
    }
  } catch {}

  async function login(username: string, password: string) {
    const data = await api.post('/auth/login', { username, password }, false)
    if (data?.accessToken) {
      token.value = data.accessToken
      profile.value = data.user || null
      uni.setStorageSync('aiyudb_token', data.accessToken)
      if (data.user) {
        uni.setStorageSync('aiyudb_profile', JSON.stringify(data.user))
      }
    }
    return data
  }

  async function fetchProfile() {
    if (!token.value) return
    try {
      const data = await api.get('/auth/profile', undefined, false)
      if (data) {
        profile.value = data as AdminUser
        uni.setStorageSync('aiyudb_profile', JSON.stringify(data))
      }
    } catch (e) {
      console.warn('[auth] fetchProfile failed:', e)
    }
  }

  async function logout() {
    token.value = ''
    profile.value = null
    isRefreshing.value = false
    uni.removeStorageSync('aiyudb_token')
    uni.removeStorageSync('aiyudb_profile')
    uni.redirectTo({ url: '/pages/login/index' })
  }

  async function updateProfile(data: Partial<AdminUser>) {
    const updated = await api.put('/auth/profile', data)
    if (updated) {
      profile.value = { ...profile.value!, ...updated }
      uni.setStorageSync('aiyudb_profile', JSON.stringify(profile.value))
    }
    return updated
  }

  async function changePassword(oldPassword: string, newPassword: string) {
    return api.put('/auth/password', { oldPassword, newPassword })
  }

  return {
    token,
    profile,
    isRefreshing,
    isLoggedIn,
    login,
    fetchProfile,
    logout,
    updateProfile,
    changePassword,
  }
})
