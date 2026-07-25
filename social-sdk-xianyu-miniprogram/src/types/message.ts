export interface ChatSession {
  id: string
  accountId: number
  accountName: string
  userName: string
  userAvatar?: string
  lastMessage: string
  lastMessageTime: string
  unreadCount: number
  isAutoReplied: boolean
  sessionId?: string
}

export interface Message {
  id: string
  sessionId: string
  accountId: number
  content: string
  direction: 'incoming' | 'outgoing'
  msgType: 'TEXT' | 'IMAGE'
  autoReplied: boolean
  createdAt: string
  senderName?: string
}

export interface SendMessageParams {
  accountId: number
  sessionId: string
  content: string
  msgType: 'TEXT' | 'IMAGE'
}

export interface SyncMessagesParams {
  accountId: number
}

export interface FetchMessagesParams {
  accountId: number
  limit?: number
}
