export interface WalletOverview {
  balance: number
  frozenBalance: number
  withdrawableBalance: number
  lastSyncedAt: string
}

export interface TransactionItem {
  id: number
  type: string
  amount: number
  description: string
  occurredAt: string
  balanceAfter: number
}
