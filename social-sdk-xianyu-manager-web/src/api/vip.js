import request from './request'

export const getVipHeaderStatus = () => request.get('/vip/header-status')
export const getVipStatus = () => request.get('/vip/status')
export const getVipConfig = () => request.get('/vip/config')
export const bindCommunityUser = () => request.post('/vip/community/bind')
export const createVipOrder = (data) => request.post('/vip/orders', data)
export const getVipOrder = (localOrderNo) => request.get(`/vip/orders/${localOrderNo}`)
export const getCommunityMenu = () => request.get('/community/menu')
