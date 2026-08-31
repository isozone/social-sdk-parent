import request from '@/api/request'

// 擦亮 API — 已对接后台 ProductController 的 /api/products/polish* 端点（由 PolishService 实现）
export function polishItem(accountId, itemId) {
  return request.post('/products/polish', null, { params: { accountId, itemId } })
}

export function batchPolish(accountId, itemIds) {
  return request.post('/products/polish/batch', { accountId, itemIds })
}

export function superPolish(accountId, itemId, times) {
  return request.post('/products/polish/super', null, { params: { accountId, itemId, times } })
}
