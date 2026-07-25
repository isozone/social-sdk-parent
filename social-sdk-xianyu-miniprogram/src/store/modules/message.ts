// Message Store - sessions, unread counts, history cache
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { ChatSession, Message, SendMessageParams } from '@/types/message'
import api from '@/api'

export const useMessageStore = defineStore('message', () => {
  const sessions = ref<ChatSession[]>([])
  const history = ref<Message[]>([])
  const loading = ref(false)
  const wsConnected = ref(false)

  async function loadSessions(accountId?: number) {
    loading.value = true
    try {
      const params: any = accountId ? { accountId } : {}
      const res = await api.get<any>('/api/mini/messages/sessions', params, false)
      if (Array.isArray(res)) sessions.value = res
      else if (res?.records) sessions.value = res.records
    } finally {
      loading.value = false
    }
  }

  async function loadHistory(params: { accountId: number; sessionId: string; limit?: number }) {
    try {
      const res = await api.get<any>('/api/mini/messages/history', params, false)
      if (Array.isArray(res)) history.value = res
      else if (res?.records) history.value = res.records
      else history.value = []
    } catch (e) {
      history.value = []
    }
  }

  async function sendMessage(data: SendMessageParams) {
    const result = await api.post('/api/mini/messages/send', data)
    // Optimistic append
    history.value.push({
      id: String(Date.now()),
      sessionId: data.sessionId,
      accountId: data.accountId,
      content: data.content,
      direction: 'outgoing',
      msgType: data.msgType || 'TEXT',
      autoReplied: false,
      createdAt: new Date().toISOString(),
    })
    return result
  }

  async function syncMessages(accountId: number) {
    return api.post('/api/mini/messages/sync', { accountId })
  }

  async function markSessionRead(sessionId: string, accountId: number) {
    // Mark session as read locally
    sessions.value = sessions.value.map(s =>
      s.id === sessionId ? { ...s, unreadCount: 0 } : s
    )
  }

  return { sessions, history, loading, wsConnected, loadSessions, loadHistory, sendMessage, syncMessages, markSessionRead }
})
