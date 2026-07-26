export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
  success?: boolean
  timestamp?: number
}

// 对齐后端 AdminUser 实体 + AuthController.getProfile 返回 Map 字段
export interface AdminUser {
  id: number
  username: string
  displayName: string
  email?: string
  phone?: string
  roleLevel: number // 1=普通管理员, 9=超级管理员
}

// 对齐后端 JwtResponse：token / tokenType / expiresIn / user{id,username,displayName,roleLevel}
export interface JwtResponse {
  token: string
  tokenType: string
  expiresIn: number
  user: AdminUser
}

export interface PageResponse<T = any> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface PageInfo {
  page: number
  size: number
  keyword?: string
  [key: string]: any
}
