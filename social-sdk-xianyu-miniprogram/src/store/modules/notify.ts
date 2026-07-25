// Notify Store - notification count + unread list
import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { NotifyMessage, UnreadCount } from '@/types/notify'
import api from '@/api'

export const useNotifyStore = defineStore('notify', () => {
  const messages = ref<NotifyMessage[]>([])
  const unreadCount = ref(0)

  async function fetchUnread() {
    try {
      const res = await api.get<any>('/api/mini/notify/messages/unread-count', undefined, false)
      if (typeof res === 'number') {
        unreadCount.value = res
      } else if (res?.count !== undefined) {
        unreadCount.value = res.count
      }
    } catch { /* ignore */ }
  }

  async function fetchMessages(page = 1, size = 20) {
    try {
      const res = await api.get<any>('/api/mini/notify/messages', { page, size }, false)
      if (Array.isArray(res)) messages.value = res
      else if (res?.records) messages.value = res.records
      else messages.value = []
    } catch {
      messages.value = []
    }
  }

  async function markRead(id: number) {
    await api.post(`/api/mini/notify/messages/${id}/read`)
    messages.value = messages.value.map(m => m.id === id ? { ...m, read: true } : m)
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  }

  async function markAllRead() {
    await api.post('/api/mini/notify/messages/read-all')
    messages.value.forEach(m => { m.read = true })
    unreadCount.value = 0
  }

  return { messages, unreadCount, fetchUnread, fetchMessages, markRead, markAllRead }
})
