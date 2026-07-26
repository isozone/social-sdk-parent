<template>
  <view class="page-notify">
    <view class="nav-bar">
      <view class="nav-back" @click="goBack"><text class="nav-arrow">‹</text><text>返回</text></view>
      <text class="nav-title">站内通知</text>
      <text class="nav-action" v-if="unreadCount > 0" @click="markAllRead">全部已读</text>
    </view>

    <view class="seg-bar">
      <view class="seg-btn" :class="{ active: filter === 'ALL' }" @click="switchFilter('ALL')">全部</view>
      <view class="seg-btn" :class="{ active: filter === 'UNREAD' }" @click="switchFilter('UNREAD')">未读<text v-if="unreadCount > 0" class="badge">{{ unreadCount }}</text></view>
      <view class="seg-btn" :class="{ active: filter === 'READ' }" @click="switchFilter('READ')">已读</view>
    </view>

    <scroll-view scroll-y class="list-area" @scrolltolower="loadMore">
      <view v-if="loading && list.length === 0" class="loading-hint">加载中...</view>
      <view v-if="!loading && list.length === 0" class="empty-hint">
        <text class="empty-emoji">📭</text>
        <text>暂无通知</text>
      </view>

      <view class="notify-card" :class="{ unread: !n.read }" v-for="n in list" :key="n.id" @click="openItem(n)">
        <view class="nc-dot" v-if="!n.read" />
        <view class="nc-icon" :class="iconClass(n.type)">{{ iconEmoji(n.type) }}</view>
        <view class="nc-body">
          <view class="nc-title">{{ n.title }}</view>
          <view class="nc-content">{{ n.content }}</view>
          <view class="nc-meta">
            <text class="nc-type">{{ typeLabel(n.type) }}</text>
            <text class="nc-time">{{ formatTime(n.createdAt) }}</text>
          </view>
        </view>
        <view class="nc-action" v-if="!n.read" @click.stop="markItemRead(n)">✓</view>
      </view>

      <view v-if="loadingMore" class="loading-hint">加载中...</view>
      <view v-if="noMore && list.length > 0" class="end-hint">— 已到底 —</view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getMessages, getUnreadCount, markRead, markAllRead } from '@/api/notify'
import type { NotifyMessage } from '@/types/notify'

const list = ref<NotifyMessage[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const noMore = ref(false)
const page = ref(1)
const pageSize = 20
const filter = ref<'ALL' | 'UNREAD' | 'READ'>('ALL')
const unreadCount = ref(0)

onMounted(() => reload())

async function reload() {
  page.value = 1
  noMore.value = false
  list.value = []
  await Promise.all([loadUnread(), load()])
}

async function loadUnread() {
  try { const r = await getUnreadCount(); unreadCount.value = r?.count || 0 } catch {}
}

async function load() {
  if (page.value === 1) loading.value = true
  else loadingMore.value = true
  try {
    const res = await getMessages({ page: page.value, pageSize, filter: filter.value })
    const rows = res?.records || []
    if (page.value === 1) list.value = rows
    else list.value.push(...rows)
    if (rows.length < pageSize) noMore.value = true
  } catch (e: any) {
    uni.showToast({ title: e?.message || '加载失败', icon: 'none' })
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

async function loadMore() {
  if (loadingMore.value || noMore.value) return
  page.value += 1
  await load()
}

function switchFilter(f: 'ALL' | 'UNREAD' | 'READ') {
  if (filter.value === f) return
  filter.value = f
  reload()
}

async function markItemRead(n: NotifyMessage) {
  try {
    await markRead(n.id)
    n.read = true
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  } catch (e: any) {
    uni.showToast({ title: e?.message || '操作失败', icon: 'none' })
  }
}

async function markAllRead() {
  uni.showLoading({ title: '处理中...' })
  try {
    await markAllRead()
    list.value.forEach(n => n.read = true)
    unreadCount.value = 0
    uni.showToast({ title: '已全部标记已读', icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: e?.message || '操作失败', icon: 'none' })
  } finally { uni.hideLoading() }
}

function openItem(n: NotifyMessage) {
  if (!n.read) markItemRead(n)
  // 通知详情可在站内展开，无需跳页
}

function typeLabel(t: string) {
  return { ORDER: '订单', MESSAGE: '消息', SYSTEM: '系统', ACCOUNT: '账号', AI: 'AI', WARN: '预警' }[t] || t
}
function iconEmoji(t: string) {
  return { ORDER: '📦', MESSAGE: '💬', SYSTEM: '⚙️', ACCOUNT: '👤', AI: '🤖', WARN: '⚠️' }[t] || '🔔'
}
function iconClass(t: string) {
  return t.toLowerCase()
}

function formatTime(s: string) {
  if (!s) return ''
  return s.replace('T', ' ').slice(0, 16)
}

function goBack() { uni.navigateBack() }
</script>

<style scoped lang="scss">
.page-notify { min-height: 100vh; background: #f5f5f7; display: flex; flex-direction: column; }
.nav-bar { display: flex; align-items: center; justify-content: space-between; height: 88rpx; padding: 0 24rpx; background: rgba(255,255,255,.92); backdrop-filter: blur(20rpx); border-bottom: 1rpx solid #e5e7eb; flex-shrink: 0; }
.nav-back { display: flex; align-items: center; gap: 8rpx; font-size: 28rpx; color: #4f46e5; font-weight: 600; }
.nav-arrow { font-size: 36rpx; font-weight: 300; }
.nav-title { font-size: 32rpx; font-weight: 700; color: #111827; }
.nav-action { font-size: 26rpx; color: #4f46e5; font-weight: 600; }

.seg-bar { display: flex; gap: 8rpx; padding: 20rpx 24rpx; background: #fff; border-bottom: 1rpx solid #e5e7eb; flex-shrink: 0; }
.seg-btn { flex: 1; height: 72rpx; line-height: 72rpx; text-align: center; font-size: 24rpx; color: #6b7280; background: #f3f4f6; border-radius: 16rpx; position: relative; }
.seg-btn.active { background: linear-gradient(135deg, #4f46e5, #7c3aed); color: #fff; font-weight: 600; }
.badge { position: absolute; top: 8rpx; right: 20rpx; min-width: 28rpx; height: 28rpx; line-height: 28rpx; padding: 0 8rpx; border-radius: 14rpx; background: #ef4444; color: #fff; font-size: 18rpx; }

.list-area { flex: 1; padding: 20rpx 24rpx; }
.loading-hint, .end-hint { text-align: center; font-size: 22rpx; color: #9ca3af; padding: 30rpx 0; }
.empty-hint { display: flex; flex-direction: column; align-items: center; padding: 80rpx 0; gap: 20rpx; font-size: 24rpx; color: #9ca3af; }
.empty-emoji { font-size: 80rpx; }

.notify-card { display: flex; align-items: flex-start; gap: 20rpx; background: #fff; border-radius: 20rpx; padding: 28rpx 24rpx; margin-bottom: 16rpx; box-shadow: 0 2rpx 12rpx rgba(0,0,0,.03); position: relative; }
.notify-card.unread { background: rgba(79,70,229,.04); border: 1rpx solid rgba(79,70,229,.15); }
.nc-dot { position: absolute; top: 28rpx; right: 24rpx; width: 16rpx; height: 16rpx; border-radius: 50%; background: #ef4444; }
.nc-icon { width: 72rpx; height: 72rpx; border-radius: 20rpx; background: #f3f4f6; display: flex; align-items: center; justify-content: center; font-size: 36rpx; flex-shrink: 0; }
.nc-body { flex: 1; min-width: 0; }
.nc-title { font-size: 28rpx; font-weight: 600; color: #111827; }
.nc-content { font-size: 24rpx; color: #6b7280; margin-top: 8rpx; line-height: 1.5; }
.nc-meta { display: flex; align-items: center; gap: 16rpx; margin-top: 12rpx; }
.nc-type { font-size: 20rpx; color: #4f46e5; background: rgba(79,70,229,.08); padding: 4rpx 12rpx; border-radius: 8rpx; }
.nc-time { font-size: 20rpx; color: #9ca3af; }
.nc-action { width: 56rpx; height: 56rpx; border-radius: 50%; background: rgba(79,70,229,.1); color: #4f46e5; display: flex; align-items: center; justify-content: center; font-size: 28rpx; flex-shrink: 0; }
.nc-action:active { background: rgba(79,70,229,.2); }
</style>
