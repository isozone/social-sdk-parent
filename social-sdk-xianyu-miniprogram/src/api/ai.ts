import { api } from './request'

interface AiChatMessage {
  role: 'user' | 'assistant'
  content: string
}

export function testChat(messages: AiChatMessage[]) {
  return api.post('/api/mini/monitor/ai/test-chat', { messages })
}

export function generateTitle(title: string) {
  return api.post('/api/mini/monitor/ai/generate-title', { title })
}

export function getProviders() {
  return api.get<any[]>('/api/mini/monitor/ai/providers')
}

export function getModels(provider: string) {
  return api.get<any[]>('/api/mini/monitor/ai/models', { provider })
}

export function getCsSessions(accountId: number | string) {
  return api.get('/api/mini/monitor/ai/cs-sessions', { accountId })
}
