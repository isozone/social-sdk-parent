import request from './request'

export const getVipHeaderStatus = () => request.get('/vip/header-status')
export const getVipStatus = () => request.get('/vip/status')
export const verifyVipStatus = () => request.post('/vip/verify')
export const bindCommunityUser = () => request.post('/vip/community/bind')
export const getVipIdentity = () => request.get('/vip/identity')
export const sendVipEmailCode = (data) => request.post('/vip/email/send-code', data)
export const verifyVipEmail = (data) => request.post('/vip/email/verify', data)
export const getCommunityMenu = () => request.get('/community/menu')

// 接入密钥配置（B 端：付费后填写 app-id/secret，保存后自动持久化生效，无需改环境变量）
export const getAccessConfig = () => request.get('/vip/access/config')
export const saveAccessConfig = (data) => request.post('/vip/access/config', data)

export const communityGet = (path, params) => request.get(`/community/client${path}`, { params })
export const communityPost = (path, data) => request.post(`/community/client${path}`, data)
export const communityPut = (path, data) => request.put(`/community/client${path}`, data)
export const communityDelete = (path) => request.delete(`/community/client${path}`)
