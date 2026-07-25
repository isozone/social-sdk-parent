import api from './request.js'

// BOT-O3 发货匹配规则
export const listDeliveryRules = (params) => api.get('/delivery-rules', { params })
export const listAllDeliveryRules = (params) => api.get('/delivery-rules/list', { params })
export const createDeliveryRule = (data) => api.post('/delivery-rules', data)
export const updateDeliveryRule = (id, data) => api.put(`/delivery-rules/${id}`, data)
export const deleteDeliveryRule = (id) => api.delete(`/delivery-rules/${id}`)
export const matchDeliveryRule = (params) => api.post('/delivery-rules/match', null, { params })

// BOT-B1 评价模板
export const listCommentTemplates = (params) => api.get('/comment-templates', { params })
export const listAllCommentTemplates = (params) => api.get('/comment-templates/list', { params })
export const createCommentTemplate = (data) => api.post('/comment-templates', data)
export const updateCommentTemplate = (id, data) => api.put(`/comment-templates/${id}`, data)
export const deleteCommentTemplate = (id) => api.delete(`/comment-templates/${id}`)
export const toggleCommentTemplateEnabled = (id, enabled) => api.put(`/comment-templates/${id}/enabled`, null, { params: { enabled } })
export const previewCommentTemplate = (params) => api.post('/comment-templates/preview', null, { params })

// BOT-D1 商品专属回复
export const listItemReplies = (params) => api.get('/item-reply', { params })
export const listAllItemReplies = (params) => api.get('/item-reply/list', { params })
export const createItemReply = (data) => api.post('/item-reply', data)
export const updateItemReply = (id, data) => api.put(`/item-reply/${id}`, data)
export const deleteItemReply = (id) => api.delete(`/item-reply/${id}`)
export const toggleItemReplyEnabled = (id, enabled) => api.put(`/item-reply/${id}/enabled`, null, { params: { enabled } })
export const previewItemReply = (params) => api.post('/item-reply/preview', null, { params })

// BOT-A6/B5 关闭通知批次日志
export const listCloseNoticeLogs = (params) => api.get('/close-notice/logs', { params })
export const runCloseNoticeManually = () => api.post('/close-notice/run')
