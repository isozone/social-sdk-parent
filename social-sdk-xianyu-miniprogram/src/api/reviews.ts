import { api } from './request'
import type { PageResponse } from '@/types/common'

export interface ReviewItem {
  id: number
  orderId: number
  productId: number
  productTitle: string
  buyerName: string
  content?: string
  rating: number
  status: 'PENDING' | 'REVIEWED' | 'REFUNDED'
  createdAt: string
}

// 后端真实端点：ReviewController /api/reviews + @GetMapping("/refunds")（退款列表）
export function getList(params?: any) {
  return api.get<PageResponse<ReviewItem>>('/api/mini/reviews/refunds', params)
}

export interface SubmitReviewParams {
  reviewId: number
  content: string
  rating: number
}

// 后端真实端点：ReviewController /api/reviews + @PostMapping("/orders/{orderId}")
export function submitReview(orderId: number, data: SubmitReviewParams) {
  return api.post(`/api/mini/reviews/orders/${orderId}`, data)
}

export interface RefundParams {
  reviewId: number
  reason: string
}

// 后端真实端点：ReviewController /api/reviews + @PostMapping("/refunds")
export function refund(data: RefundParams) {
  return api.post('/api/mini/reviews/refunds', data)
}
