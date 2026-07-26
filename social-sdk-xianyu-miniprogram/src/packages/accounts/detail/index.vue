<template>
  <view class="page-account-detail">
        <view class="nav-bar">
      <navigator url="/packages/accounts/list/index" hover-class="none" class="nav-back"><text class="nav-arrow">‹</text><text>返回</text></navigator>
      <text class="nav-title">账号详情</text>
      <view class="nav-actions"></view>
    </view>

    <scroll-view scroll-y class="main-scroll">
      <view class="profile-header">
        <view class="avatar-wrap">{{ account.accountName ? account.accountName[0] : '?' }}</view>
        <text class="account-name">{{ account.accountName || '未命名账号' }}</text>
        <text class="status-tag" :class="account.enabled ? 'tag-active' : 'tag-paused'">{{ account.enabled ? '运行中' : '已暂停' }}</text>
      </view>
      <view class="health-card">
        <view class="ring"><text class="score-val">{{ account.score || '--' }}</text></view>
        <text class="health-text">健康分</text>
      </view>
      <view class="stats-row">
        <view class="stat-box"><text class="num">{{ account.productCount || 0 }}</text><text class="lbl">商品</text></view>
        <view class="stat-box"><text class="num">{{ account.orderCount || 0 }}</text><text class="lbl">订单</text></view>
        <view class="stat-box"><text class="num">{{ account.messageCount || 0 }}</text><text class="lbl">消息</text></view>
        <view class="stat-box"><text class="num">{{ account.replayRate || '--' }}</text><text class="lbl">回复率</text></view>
      </view>
      <view class="card">
        <text class="section-label">账号信息</text>
        <view class="info-row"><text>闲鱼昵称</text><text>{{ account.nickname || '未同步' }}</text></view>
        <view class="info-row"><text>备注</text><text>{{ account.remark || '无' }}</text></view>
      </view>
      <view class="btn-group">
        <button class="btn-primary" @click="syncProfile">同步资料</button>
        <button class="btn-secondary" @click="toggleStatus">{{ account.enabled ? '暂停' : '启用' }}账号</button>
        <button class="btn-danger" @click="confirmDelete">删除账号</button>
      </view>
    </scroll-view>
  </view>
</template>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '@/api/request'
const account = ref<any>({})
onMounted(async () => {
  const pages = getCurrentPages() as any[]
  const prev = pages[pages.length - 2]?.options || {}
  if (prev.id) await loadAccount(Number(prev.id))
})
async function loadAccount(id: number) {
  try { const r = await api.get(`/api/mini/accounts/${id}`, undefined, false); account.value = r }
  catch (e: any) { uni.showToast({ title: e?.message || '加载失败', icon: 'none' }) }
}
async function syncProfile() {
  try { await api.post(`/api/mini/accounts/${account.value.id}/profile/sync`); uni.showToast({ title: '同步成功', icon: 'success' }); await loadAccount(account.value.id) }
  catch (e: any) { uni.showToast({ title: e?.message || '同步失败', icon: 'none' }) }
}
async function toggleStatus() {
  uni.showModal({ title: '提示', content: '确定操作此账号？', success: async res => {
    if (!res.confirm) return
    try { await api.put(`/api/mini/accounts/${account.value.id}/status`, { enabled: !account.value.enabled }); uni.showToast({ title: '已更新', icon: 'success' }); await loadAccount(account.value.id) }
    catch { }
  }})
}
function confirmDelete() {
  uni.showModal({ title: '删除确认', content: '确定删除此账号？', confirmColor: '#ef4444', success: async res => {
    if (!res.confirm) return
    try { await api.delete(`/api/mini/accounts/${account.value.id}`); uni.navigateBack() } catch {}
  }})
}
</script>
<style scoped lang="scss">
.page-account-detail{min-height:100vh;background:#f5f5f7}
.nav-bar{display:flex;align-items:center;justify-content:space-between;height:88rpx;padding:0 24rpx;background:#fff;border-bottom:1rpx solid #e5e7eb}
.nav-back{display:flex;align-items:center;gap:8rpx;font-size:28rpx;color:#4f46e5;font-weight:600;text-decoration:none}.nav-arrow{font-size:36rpx;font-weight:300}.nav-title{font-size:32rpx;font-weight:700;color:#111827}.nav-actions{display:flex;gap:16rpx}
.main-scroll{height:calc(100vh - 88rpx)}
.profile-header{padding:48rpx 32rpx 40rpx;background:linear-gradient(135deg,#4f46e5,#7c3aed);border-radius:0 0 48rpx 48rpx;display:flex;flex-direction:column;align-items:center}
.avatar-wrap{width:96rpx;height:96rpx;border-radius:28rpx;background:rgba(255,255,255,.2);display:flex;align-items:center;justify-content:center;font-size:40rpx;color:#fff;border:3rpx solid rgba(255,255,255,.3)}
.account-name{font-size:36rpx;font-weight:700;color:#fff;margin-top:16rpx}
.status-tag{font-size:22rpx;padding:6rpx 16rpx;border-radius:8rpx;margin-top:10rpx}.tag-active{background:rgba(16,185,129,.2);color:#10b981;border:1rpx solid rgba(16,185,129,.3)}.tag-paused{background:rgba(156,163,175,.2);color:#9ca3af}
.health-card{background:#fff;border-radius:24rpx;margin:-24rpx 24rpx 16rpx;padding:28rpx;display:flex;flex-direction:column;align-items:center;box-shadow:0 8rpx 32rpx rgba(0,0,0,.08);position:relative;z-index:1}
.ring{width:120rpx;height:120rpx;border-radius:50%;border:8rpx solid #e5e7eb;display:flex;align-items:center;justify-content:center;font-size:40rpx;font-weight:800;color:#4f46e5}
.stats-row{display:flex;gap:12rpx;padding:0 24rpx;margin-bottom:16rpx}
.stat-box{flex:1;background:#fff;border-radius:20rpx;padding:20rpx 8rpx;text-align:center;border:1rpx solid #e5e7eb}
.num{font-size:32rpx;font-weight:800;color:#111827;line-height:1}.lbl{font-size:20rpx;color:#9ca3af;margin-top:6rpx;display:block}
.card{background:#fff;border-radius:24rpx;margin:0 24rpx 16rpx;padding:24rpx}
.section-label{font-size:28rpx;font-weight:700;color:#111827;display:block;margin-bottom:16rpx}
.info-row{display:flex;justify-content:space-between;align-items:center;padding:14rpx 0;border-bottom:1rpx solid #f8f8f9;font-size:26rpx;color:#6b7280}:last-child{border-bottom:none;font-weight:500;color:#111827}
.btn-group{padding:0 24rpx;display:flex;flex-direction:column;gap:16rpx}
.btn-primary{height:88rpx;background:linear-gradient(135deg,#4f46e5,#7c3aed);color:#fff;border:none;border-radius:24rpx;font-size:30rpx;font-weight:600}
.btn-secondary{height:88rpx;background:#fff;color:#111827;border:1rpx solid #e5e7eb;border-radius:24rpx;font-size:30rpx;font-weight:500}
.btn-danger{height:88rpx;background:#fff;color:#ef4444;border:1rpx solid #fecaca;border-radius:24rpx;font-size:30rpx;font-weight:600}
</style>
