import { api } from './request'
import type { ProductItem, CategoryNode, PolishResult } from '@/types/product'
import type { PageResponse } from '@/types/common'

export function getProducts(params?: any) {
  return api.get<PageResponse<ProductItem>>('/api/mini/monitor/products', params)
}

export function getProduct(id: number | string) {
  return api.get<ProductItem>(`/api/mini/monitor/products/${id}`)
}

export function shelfOn(id: number | string) {
  return api.post(`/api/mini/monitor/products/${id}/shelf-on`)
}

export function shelfOff(id: number | string) {
  return api.post(`/api/mini/monitor/products/${id}/shelf-off`)
}

export function polish(id: number | string) {
  return api.post<PolishResult>(`/api/mini/monitor/products/${id}/polish`)
}

export function polishBatch(ids: number[]) {
  return api.post<PolishResult[]>('/api/mini/monitor/products/polish-batch', { ids })
}

export function uploadImage(fileUrl: string): Promise<{ url: string }> {
  return new Promise((resolve, reject) => {
    const headers: Record<string, string> = {}
    // set Authorization via storage if Pinia store not yet initialized
    const token = (() => {
      try {
        const t = uni.getStorageSync('mini_token')
        return String(t || '').replace(/^Bearer\s+/i, '')
      } catch { return '' }
    })()
    if (token) headers['Authorization'] = `Bearer ${token}`

    uni.uploadFile({
      url: '/api/mini/monitor/products/upload-image',
      filePath: fileUrl,
      name: 'file',
      header: headers,
      formData: {},
      success: res => {
        try {
          const data = JSON.parse(res.data)
          if (data.code === 0) {
            resolve(data.data)
          } else {
            reject(new Error(data.message || '上传失败'))
          }
        } catch {
          reject(new Error('上传失败'))
        }
      },
      fail: err => reject(err),
    })
  })
}

export function getCategoryTree() {
  return api.get<CategoryNode[]>('/api/mini/monitor/categories/tree')
}
