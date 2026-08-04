import api from '@/api/request'

export function getOrderDetail(id) {
  return api.get(`/orders/${id}/detail`)
}
