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

export function getList(params?: any) {
  return api.get<PageResponse<ReviewItem>>('/api/mini/monitor/reviews', params)
}

export interface SubmitReviewParams {
  reviewId: number
  content: string
  rating: number
}

export function submitReview(data: SubmitReviewParams) {
  return api.post('/api/mini/monitor/reviews', data)
}

export interface RefundParams {
  reviewId: number
  reason: string
}

export function refund(data: RefundParams) {
  return api.post(`/api/mini/monitor/reviews/refund`, data)
}
