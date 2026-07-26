import { api } from './request'
import type { RuleItem, CreateRuleParams, TestRuleMatchParams, TestRuleMatchResult } from '@/types/rule'
import type { PageResponse } from '@/types/common'

export function getRules(params?: any) {
  return api.get<PageResponse<RuleItem>>('/api/mini/rules', params)
}

export function getRule(id: number | string) {
  return api.get<RuleItem>(`/api/mini/rules/${id}`)
}

export function createRule(data: CreateRuleParams) {
  return api.post<RuleItem>('/api/mini/rules', data)
}

export function updateRule(id: number | string, data: Partial<CreateRuleParams>) {
  return api.put<RuleItem>(`/api/mini/rules/${id}`, data)
}

export function deleteRule(id: number | string) {
  return api.delete(`/api/mini/rules/${id}`)
}

export function toggleRule(id: number | string, enabled: boolean) {
  return api.put<RuleItem>(`/api/mini/rules/${id}/toggle`, { enabled })
}

export function testRule(id: number | string, data: TestRuleMatchParams) {
  return api.post<TestRuleMatchResult>(`/api/mini/rules/${id}/test`, data)
}
