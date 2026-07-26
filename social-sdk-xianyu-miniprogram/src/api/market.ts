import { api } from './request'

export interface MarketKeyword {
  keyword: string
  searchIndex: number
  onlineGoodsCount: number
}

// 后端无独立 MarketController（仅在 /openapi/v1/market 厂商接口）
// 前端改走 monitor dashboard 聚合：市场情报从 dashboard 的 marketStats 字段取
export function getKeywords(keyword: string) {
  return api.get<MarketKeyword[]>('/api/mini/monitor/dashboard', { scope: 'market', keyword })
}

export function getTrend(keywords: string[]) {
  return api.get<any[]>('/api/mini/monitor/dashboard', { scope: 'market', trend: keywords.join(',') })
}

export function getLatest() {
  return api.get<any[]>('/api/mini/monitor/dashboard', { scope: 'market', latest: true })
}
