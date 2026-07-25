import { api } from './request'

export interface LoginResponse {
  accessToken: string
  expiresIn: number
  user: {
    id: number
    username: string
    displayName: string
    roleLevel: number
  }
}

export interface ProfileData {
  id: number
  username: string
  displayName: string
  roleLevel: number
  email?: string
  phone?: string
}

export function login(username: string, password: string) {
  return api.post<LoginResponse>('/api/mini/auth/login', { username, password }, false)
}

export function getProfile() {
  return api.get<ProfileData>('/api/mini/auth/profile')
}

export function updateProfile(data: Partial<ProfileData>) {
  return api.put('/api/mini/auth/profile', data)
}

export function updatePassword(oldPw: string, newPw: string) {
  return api.put('/api/mini/auth/password', { oldPassword: oldPw, newPassword: newPw })
}
