import { api } from './request'
import type { WalletOverview, TransactionItem } from '@/types/wallet'
import type { PageResponse } from '@/types/common'

export function getWalletOverview() {
  return api.get<WalletOverview>('/api/mini/wallet/overview')
}

export function getTransactions(params?: any) {
  return api.get<PageResponse<TransactionItem>>('/api/mini/wallet/transactions', params)
}
