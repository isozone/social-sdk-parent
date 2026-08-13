import request from './request'

export const getVipHeaderStatus = () => request.get('/vip/header-status')
export const getVipStatus = () => request.get('/vip/status')
export const verifyVipStatus = () => request.post('/vip/verify')
export const bindCommunityUser = () => request.post('/vip/community/bind')
export const getVipIdentity = () => request.get('/vip/identity')
export const sendVipEmailCode = (data) => request.post('/vip/email/send-code', data)
export const verifyVipEmail = (data) => request.post('/vip/email/verify', data)
export const getCommunityMenu = () => request.get('/community/menu')

// 接入密钥配置（B 端：只读展示，密钥由 new-api 付费后自动下发，不再手填）
export const getAccessConfig = () => request.get('/vip/access/config')
// 接入密钥自动流程：拉套餐 → 创建订单支付 → 凭订单号取密钥落地
export const getAccessPlans = () => request.get('/vip/access/plans')
export const applyAccessPlan = (data) => request.post('/vip/access/apply', data)
export const getAccessCredential = (orderNo) => request.get('/vip/access/credential', { params: { orderNo } })

export const communityGet = (path, params) => request.get(`/community/client${path}`, { params })
export const communityPost = (path, data) => request.post(`/community/client${path}`, data)
export const communityPut = (path, data) => request.put(`/community/client${path}`, data)
export const communityDelete = (path) => request.delete(`/community/client${path}`)
