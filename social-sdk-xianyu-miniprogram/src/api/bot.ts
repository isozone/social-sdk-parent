import { api } from './request'

function qs(params?: any) {
  if (!params) return ''
  const pairs = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
  return pairs.length ? `?${pairs.join('&')}` : ''
}

export const listDeliveryRules = (params?: any) => api.get<any>('/api/mini/delivery-rules', params)
export const listAllDeliveryRules = (params?: any) => api.get<any[]>(`/api/mini/delivery-rules/list${qs(params)}`)
export const createDeliveryRule = (data: any) => api.post('/api/mini/delivery-rules', data)
export const updateDeliveryRule = (id: number | string, data: any) => api.put(`/api/mini/delivery-rules/${id}`, data)
export const deleteDeliveryRule = (id: number | string) => api.delete(`/api/mini/delivery-rules/${id}`)
export const matchDeliveryRule = (params: any) => api.post(`/api/mini/delivery-rules/match${qs(params)}`)

export const listCommentTemplates = (params?: any) => api.get<any>('/api/mini/comment-templates', params)
export const listAllCommentTemplates = (params?: any) => api.get<any[]>(`/api/mini/comment-templates/list${qs(params)}`)
export const createCommentTemplate = (data: any) => api.post('/api/mini/comment-templates', data)
export const updateCommentTemplate = (id: number | string, data: any) => api.put(`/api/mini/comment-templates/${id}`, data)
export const deleteCommentTemplate = (id: number | string) => api.delete(`/api/mini/comment-templates/${id}`)
export const toggleCommentTemplateEnabled = (id: number | string, enabled: boolean) => api.put(`/api/mini/comment-templates/${id}/enabled${qs({ enabled })}`)
export const previewCommentTemplate = (params: any) => api.post(`/api/mini/comment-templates/preview${qs(params)}`)

export const listItemReplies = (params?: any) => api.get<any>('/api/mini/item-reply', params)
export const listAllItemReplies = (params?: any) => api.get<any[]>(`/api/mini/item-reply/list${qs(params)}`)
export const createItemReply = (data: any) => api.post('/api/mini/item-reply', data)
export const updateItemReply = (id: number | string, data: any) => api.put(`/api/mini/item-reply/${id}`, data)
export const deleteItemReply = (id: number | string) => api.delete(`/api/mini/item-reply/${id}`)
export const toggleItemReplyEnabled = (id: number | string, enabled: boolean) => api.put(`/api/mini/item-reply/${id}/enabled${qs({ enabled })}`)
export const previewItemReply = (params: any) => api.post(`/api/mini/item-reply/preview${qs(params)}`)

export const listCloseNoticeLogs = (params?: any) => api.get<any>('/api/mini/close-notice/logs', params)
export const runCloseNoticeManually = () => api.post('/api/mini/close-notice/run')
