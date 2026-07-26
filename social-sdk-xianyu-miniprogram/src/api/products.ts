import { api } from './request'
import type { ProductItem, CategoryNode, PolishResult } from '@/types/product'
import type { PageResponse } from '@/types/common'

export function getProducts(params?: any) {
  return api.get<PageResponse<ProductItem>>('/api/mini/products', params)
}

export function getProduct(id: number | string) {
  return api.get<ProductItem>(`/api/mini/products/${id}`)
}

export function createProduct(data: any) {
  return api.post('/api/mini/products', data)
}

export function updateProduct(id: number | string, data: any) {
  return api.put(`/api/mini/products/${id}`, data)
}

export function deleteProduct(id: number | string) {
  return api.delete(`/api/mini/products/${id}`)
}

export function syncProducts(accountId: number | string) {
  return api.post('/api/mini/products/sync', { accountId })
}

export function shelfOn(id: number | string) {
  return api.post(`/api/mini/products/${id}/shelf-on`)
}

export function shelfOff(id: number | string) {
  return api.post(`/api/mini/products/${id}/shelf-off`)
}

export function polish(id: number | string) {
  return api.post<PolishResult>(`/api/mini/products/${id}/polish`)
}

export function polishBatch(ids: number[]) {
  return api.post<PolishResult[]>('/api/mini/products/polish/batch', { ids })
}

export function updatePrice(id: number | string, price: number) {
  return api.put(`/api/mini/products/${id}/price`, { price })
}

export function updateStock(id: number | string, stock: number) {
  return api.put(`/api/mini/products/${id}/stock`, { stock })
}

export function uploadImage(fileUrl: string): Promise<{ url: string }> {
  return new Promise((resolve, reject) => {
    const headers: Record<string, string> = {}
    const token = (() => {
      try {
        const t = uni.getStorageSync('mini_token')
        return String(t || '').replace(/^Bearer\s+/i, '')
      } catch { return '' }
    })()
    if (token) headers['Authorization'] = `Bearer ${token}`
    headers['X-App-Type'] = 'mini-program'

    uni.uploadFile({
      url: '/api/mini/products/upload',
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
  return api.get<CategoryNode[]>('/api/mini/products/category-tree')
}
