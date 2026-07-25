// Product Store - product list cache + selected product
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { ProductItem, LocalProductItem, CategoryNode } from '@/types/product'
import api from '@/api'

export const useProductStore = defineStore('product', () => {
  const items = ref<ProductItem[]>([])
  const localItems = ref<LocalProductItem[]>([])
  const categories = ref<CategoryNode[]>([])
  const loading = ref(false)

  async function loadProducts(params?: any) {
    loading.value = true
    try {
      const res = await api.get<any>('/api/mini/products', params, false)
      if (Array.isArray(res)) items.value = res
      else if (res?.records) items.value = res.records
      else items.value = []
    } finally {
      loading.value = false
    }
  }

  async function loadLocalProducts(params?: any) {
    loading.value = true
    try {
      const res = await api.get<any>('/api/mini/local-products', params, false)
      if (Array.isArray(res)) localItems.value = res
      else if (res?.records) localItems.value = res.records
      else localItems.value = []
    } finally {
      loading.value = false
    }
  }

  async function loadCategories() {
    return api.get<CategoryNode[]>('/api/mini/products/category-tree')
  }

  async function createProduct(data: any) {
    return api.post('/api/mini/products', data)
  }

  async function syncProducts(accountId: number) {
    return api.post('/api/mini/products/sync', { accountId })
  }

  async function polish(id: number | string) {
    return api.post(`/api/mini/products/${id}/polish`)
  }

  async function shelfOn(id: number | string) {
    return api.post(`/api/mini/products/${id}/shelf-on`)
  }

  async function shelfOff(id: number | string) {
    return api.post(`/api/mini/products/${id}/shelf-off`)
  }

  return {
    items,
    localItems,
    categories,
    loading,
    loadProducts,
    loadLocalProducts,
    loadCategories,
    createProduct,
    syncProducts,
    polish,
    shelfOn,
    shelfOff,
  }
})
