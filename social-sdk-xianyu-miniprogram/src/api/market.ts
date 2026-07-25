import { api } from './request'

export interface MarketKeyword {
  keyword: string
  searchIndex: number
  onlineGoodsCount: number
}

export function getKeywords(keyword: string) {
  return api.get<MarketKeyword[]>('/api/mini/monitor/market/keywords', { keyword })
}

export function getTrend(keywords: string[]) {
  return api.get<any[]>('/api/mini/monitor/market/trend', { keywords: keywords.join(',') })
}

export function getLatest() {
  return api.get<any[]>('/api/mini/monitor/market/latest')
}
