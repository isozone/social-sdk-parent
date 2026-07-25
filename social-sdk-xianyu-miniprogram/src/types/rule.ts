export interface RuleItem {
  id: number
  accountId: number
  accountName: string
  keyword: string
  replyText: string
  matchType: 'CONTAINS' | 'EQUALS' | 'REGEX'
  priority: number
  enabled: boolean
  hitCount: number
  createdAt: string
}

export interface CreateRuleParams {
  accountId: number
  keyword: string
  replyText: string
  matchType: 'CONTAINS' | 'EQUALS' | 'REGEX'
  priority: number
}

export interface TestRuleMatchParams {
  text: string
}

export interface TestRuleMatchResult {
  matched: boolean
  ruleName?: string
  replyText?: string
}
