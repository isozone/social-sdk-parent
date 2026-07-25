import { api } from './request'
import type { NotifyMessage, NotifyChannel, NotifyTemplate, NotifySubscription, NotifyDigestConfig } from '@/types/notify'

export function getMessages() {
  return api.get<NotifyMessage[]>('/api/mini/notify/messages')
}

export function getChannels() {
  return api.get<NotifyChannel[]>('/api/mini/notify/channels')
}

export function updateChannel(id: number | string, data: Partial<NotifyChannel>) {
  return api.put(`/api/mini/notify/channels/${id}`, data)
}

export function deleteChannel(id: number | string) {
  return api.delete(`/api/mini/notify/channels/${id}`)
}

export function getTemplates() {
  return api.get<NotifyTemplate[]>('/api/mini/notify/templates')
}

export function createTemplate(data: Partial<NotifyTemplate>) {
  return api.post('/api/mini/notify/templates', data)
}

export function updateTemplate(id: number | string, data: Partial<NotifyTemplate>) {
  return api.put(`/api/mini/notify/templates/${id}`, data)
}

export function deleteTemplate(id: number | string) {
  return api.delete(`/api/mini/notify/templates/${id}`)
}

export function getSubscriptions() {
  return api.get<NotifySubscription[]>('/api/mini/notify/subscriptions')
}

export function createSubscription(data: Partial<NotifySubscription>) {
  return api.post('/api/mini/notify/subscriptions', data)
}

export function updateSubscription(id: number | string, data: Partial<NotifySubscription>) {
  return api.put(`/api/mini/notify/subscriptions/${id}`, data)
}

export function deleteSubscription(id: number | string) {
  return api.delete(`/api/mini/notify/subscriptions/${id}`)
}

export function getDigest() {
  return api.get<NotifyDigestConfig>('/api/mini/notify/digest')
}

export function updateDigest(config: NotifyDigestConfig) {
  return api.put('/api/mini/notify/digest', config)
}
