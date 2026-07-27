import request from './request'

export const getVipHeaderStatus = () => request.get('/vip/header-status')
export const getVipStatus = () => request.get('/vip/status')
export const getVipConfig = () => request.get('/vip/config')
export const bindCommunityUser = () => request.post('/vip/community/bind')
export const createVipOrder = (data) => request.post('/vip/orders', data)
export const getVipOrder = (localOrderNo) => request.get(`/vip/orders/${localOrderNo}`)
export const getCommunityMenu = () => request.get('/community/menu')

export const communityGet = (path, params) => request.get(`/community/client${path}`, { params })
export const communityPost = (path, data) => request.post(`/community/client${path}`, data)
export const communityPut = (path, data) => request.put(`/community/client${path}`, data)
export const communityDelete = (path) => request.delete(`/community/client${path}`)
