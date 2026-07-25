export interface NotifyMessage {
  id: number
  title: string
  content: string
  type: string
  read: boolean
  createdAt: string
}

export interface UnreadCount {
  count: number
}

export interface NotifyChannel {
  id: number
  name: string
  type: 'EMAIL' | 'WEBHOOK' | 'SMS'
  enabled: boolean
  config: Record<string, any>
}

export interface NotifyTemplate {
  id: number
  name: string
  scenario: string
  subject: string
  body: string
}

export interface NotifySubscription {
  id: number
  name: string
  channelId: number
  scenario: string
  enabled: boolean
}

export interface NotifyDigestConfig {
  enabled: boolean
  schedule: string
  channels: string[]
}
