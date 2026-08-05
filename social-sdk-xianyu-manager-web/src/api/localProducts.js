import api from '@/api/request'

export function listLocalProducts(params) {
  return api.get('/local-products', { params })
}

export function getLocalProduct(id) {
  return api.get(`/local-products/${id}`)
}

export function saveLocalProduct(data) {
  return api.post('/local-products', data)
}

export function updateLocalProduct(id, data) {
  return api.put(`/local-products/${id}`, data)
}

export function deleteLocalProduct(id) {
  return api.delete(`/local-products/${id}`)
}

export function publishLocalProduct(id) {
  return api.post(`/local-products/${id}/publish`)
}

export function batchPublishLocalProducts(data) {
  return api.post('/local-products/batch-publish', data)
}

/** 批量删除本地商品：POST /api/local-products/batch-delete { ids:[] } */
export function batchDeleteLocalProducts(ids) {
  return api.post('/local-products/batch-delete', { ids })
}

/** 批量改运费偏好：POST /api/local-products/batch-shipping-mode { ids:[], shippingMode } */
export function batchUpdateShippingMode(ids, shippingMode) {
  return api.post('/local-products/batch-shipping-mode', { ids, shippingMode })
}

export function previewLocalProductImport(params) {
  const fd = new FormData()
  fd.append('file', params.file)
  if (params.deduplicate != null) fd.append('deduplicate', params.deduplicate)
  if (params.overwriteDuplicate != null) fd.append('overwriteDuplicate', params.overwriteDuplicate)
  if (params.defaultGoodsType) fd.append('defaultGoodsType', params.defaultGoodsType)
  if (params.imageStoragePath) fd.append('imageStoragePath', params.imageStoragePath)
  if (params.deliverContentSeparator) fd.append('deliverContentSeparator', params.deliverContentSeparator)
  return api.post('/local-products/import/preview', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
}

export function confirmLocalProductImport(params) {
  const fd = new FormData()
  fd.append('file', params.file)
  if (params.deduplicate != null) fd.append('deduplicate', params.deduplicate)
  if (params.overwriteDuplicate != null) fd.append('overwriteDuplicate', params.overwriteDuplicate)
  if (params.defaultGoodsType) fd.append('defaultGoodsType', params.defaultGoodsType)
  if (params.imageStoragePath) fd.append('imageStoragePath', params.imageStoragePath)
  if (params.deliverContentSeparator) fd.append('deliverContentSeparator', params.deliverContentSeparator)
  return api.post('/local-products/import/confirm', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
}

/**
 * 下载导入模板（CSV 或 Excel）。走后端接口动态生成，与列名常量严格对齐。
 * @param {'csv'|'xlsx'} type
 * @returns Blob 二进制（前端用 URL.createObjectURL + a.click 触发下载）
 */
export function downloadImportTemplate(type = 'csv') {
  return api.get(`/local-products/import/template.${type}`, { responseType: 'blob' })
}
