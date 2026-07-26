import { api } from './request'
import type { WalletOverview, TransactionItem } from '@/types/wallet'
import type { PageResponse } from '@/types/common'
import { useAccountStore } from '@/store/modules/account'

// 后端真实端点：WalletController /api/wallet + @GetMapping("/{accountId}")
// 后端无独立 /overview 端点，前端聚合：取当前账号 wallet 详情 + 交易明细
export function getWalletOverview() {
  const acc = useAccountStore()
  const accountId = acc.current?.id || 0
  return api.get<WalletOverview>(`/api/mini/wallet/${accountId}`)
}

// 后端真实端点：WalletController /api/wallet + @GetMapping("/{accountId}/transactions")
export function getTransactions(params?: any) {
  const acc = useAccountStore()
  const accountId = acc.current?.id || params?.accountId || 0
  const { accountId: _, ...rest } = params || {}
  return api.get<PageResponse<TransactionItem>>(`/api/mini/wallet/${accountId}/transactions`, rest)
}
