export interface AccountItem {
  id: number
  accountName: string
  cookie: string
  status: 'ACTIVE' | 'INACTIVE' | 'EXPIRED' | 'BANNED'
  remark?: string
  createdAt: string
  nickname?: string
  avatar?: string
  score?: number
  productCount?: number
  orderCount?: number
  enabled: boolean
}
