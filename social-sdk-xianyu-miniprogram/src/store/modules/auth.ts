// Auth Store - JWT Token + Admin User Profile
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { AdminUser } from '@/types/common'
import { api } from '@/api/request'

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
    // 后端 JwtResponse 字段：token / tokenType / expiresIn / user{id,username,displayName,roleLevel}
    const data = await api.post('/api/mini/auth/login', { username, password }, false)
    const jwt = data as any
    if (jwt?.token) {
      token.value = jwt.token
      profile.value = jwt.user || null
      uni.setStorageSync('aiyudb_token', jwt.token)
      if (jwt.user) {
        uni.setStorageSync('aiyudb_profile', JSON.stringify(jwt.user))
      }
    }
    return data
  }

  async function fetchProfile() {
    if (!token.value) return
    try {
      const data = await api.get('/api/mini/auth/profile', undefined, false)
      if (data) {
        profile.value = data as AdminUser
        uni.setStorageSync('aiyudb_profile', JSON.stringify(data))
      }
    } catch {
      // profile 刷新失败不影响已持久化的登录态
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
    const updated = await api.put('/api/mini/auth/profile', data)
    if (updated) {
      profile.value = { ...profile.value!, ...updated }
      uni.setStorageSync('aiyudb_profile', JSON.stringify(profile.value))
    }
    return updated
  }

  async function changePassword(newPassword: string) {
    return api.put('/api/mini/auth/password', { newPassword })
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
