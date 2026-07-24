import api from '@/api/request'

export function login(data) {
  return api.post('/auth/login', data)
}

export function getProfile() {
  return api.get('/auth/profile')
}

export function updateProfile(data) {
  return api.put('/auth/profile', data)
}

export function changePassword(data) {
  return api.put('/auth/password', data)
}
