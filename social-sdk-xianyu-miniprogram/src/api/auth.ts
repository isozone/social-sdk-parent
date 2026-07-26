import { api } from './request'
import type { AdminUser, JwtResponse } from '@/types/common'

// 对齐后端 JwtResponse：token / tokenType / expiresIn / user{id,username,displayName,roleLevel}
export type LoginResponse = JwtResponse

// 对齐后端 AuthController.getProfile 返回 Map 字段 + AdminUser 实体
export type ProfileData = AdminUser

export function login(username: string, password: string) {
  return api.post<LoginResponse>('/api/mini/auth/login', { username, password }, false)
}

export function getProfile() {
  return api.get<ProfileData>('/api/mini/auth/profile')
}

export function updateProfile(data: Partial<ProfileData>) {
  return api.put('/api/mini/auth/profile', data)
}

export function updatePassword(newPw: string) {
  return api.put('/api/mini/auth/password', { newPassword: newPw })
}
