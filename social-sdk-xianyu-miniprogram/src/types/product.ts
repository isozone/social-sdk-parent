export interface ProductItem {
  id: number
  accountId: number
  itemTitle: string
  price: number
  originalPrice?: number
  stock: number
  status: 'DRAFT' | 'ON_SALE' | 'OFF_SALE' | 'SOLD'
  images: string[]
  description?: string
  categoryId?: string
  categoryName?: string
  viewCount: number
  favoriteCount: number
  detailUrl?: string
  policeCount?: number
  polishedAt?: string
  createdAt: string
  updatedAt: string
}

export interface LocalProductItem {
  id: number
  title: string
  price: number
  stock: number
  images: string[]
  description?: string
  categoryId?: string
  categoryPath?: string
  status: 'LOCAL' | 'PUBLISHING' | 'ON_SALE' | 'OFF_SALE' | 'FAILED'
  accountId?: number
  xianyuProductId?: number
  createdAt: string
}

export interface CategoryNode {
  id: string
  name: string
  children?: CategoryNode[]
}

export interface PublishProductParams {
  accountId?: number
  title: string
  description: string
  price: number
  stock: number
  categoryId?: string
  images: string[]
}

export interface PolishResult {
  productId: number
  title: string
  result: string
  timestamp: string
}
