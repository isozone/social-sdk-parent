export interface OrderItem {
  id: number
  orderId: string
  accountId: number
  accountName: string
  itemTitle: string
  buyerName: string
  amount: number
  status: 'PENDING' | 'PAID' | 'SHIPPED' | 'COMPLETED' | 'REFUNDING'
  trackingNo?: string
  deliveryType?: 'PHYSICAL' | 'VIRTUAL'
  virtualContent?: string
  goodsType?: string
  createdAt: string
  paidAt?: string
  shippedAt?: string
  completedAt?: string
  buyerMsg?: string
}

export interface DeliveryParams {
  trackingNo: string
  deliveryCompany?: string
}

export interface OrderStats {
  totalOrders: number
  pendingDelivery: number
  todaySales: number
}
