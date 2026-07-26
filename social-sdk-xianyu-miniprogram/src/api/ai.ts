import { api } from './request'

interface AiChatMessage {
  role: 'user' | 'assistant'
  content: string
}

// 后端真实端点：AiChatController /api/ai/chat + @PostMapping("/test")
export function testChat(messages: AiChatMessage[]) {
  return api.post('/api/mini/ai/chat/test', { messages })
}

// 后端真实端点：AiDemoController /api/ai/demo + @PostMapping("/generate-title")
export function generateTitle(title: string) {
  return api.post('/api/mini/ai/demo/generate-title', { title })
}

// 后端真实端点：AiProviderController /api/ai/providers
export function getProviders() {
  return api.get<any[]>('/api/mini/ai/providers')
}

// 后端真实端点：AiProviderController /api/ai/providers/{id}/models
export function getModels(providerId: number | string) {
  return api.get<any[]>(`/api/mini/ai/providers/${providerId}/models`)
}

// 后端真实端点：AiCsController /api/ai/cs + @GetMapping("/sessions")
export function getCsSessions(accountId: number | string) {
  return api.get('/api/mini/ai/cs/sessions', { accountId })
}

// 后端真实端点：AiOpsController /api/ai/ops + @GetMapping("/tasks")
export function getOpsStats(accountId?: number | string) {
  return api.get('/api/mini/ai/ops/tasks', { accountId, status: 'COMPLETED', page: 1, size: 20 })
}
