<template>
  <view class="page-sessions">
    <scroll-view scroll-y class="session-list" @refresherpulling="onPullRefresh" :refresher-enabled="true" :refresher-triggered="loading">
      <view class="session-item" v-for="s in sessions" :key="s.id" @click="goChat(s)">
        <image class="user-avatar" :src="s.userAvatar || '/static/tab/user.svg'" mode="aspectFill" />
        <view class="session-info">
          <text class="session-name">{{ s.userName }}</text>
          <text class="session-last">{{ s.lastMessage }}</text>
        </view>
        <view class="session-meta">
          <text class="session-time">{{ s.lastMessageTime }}</text>
          <view v-if="s.unreadCount > 0" class="unread-badge">{{ s.unreadCount }}</view>
        </view>
      </view>
      <empty-state v-if="sessions.length === 0 && !loading" text="暂无会话" action-text="同步消息" @action="syncMsg" />
    </scroll-view>
  </view>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { api } from '@/api/request'
import EmptyState from '@/components/common/EmptyState.vue'
const sessions = ref<any[]>([])
const loading = ref(false)
async function load() {
  loading.value = true
  try {
    const res = await api.get('/api/mini/messages/sessions', undefined, false)
    if (Array.isArray(res)) sessions.value = res; else if (res?.records) sessions.value = res.records
  } finally { loading.value = false }
}
async function syncMsg() {
  uni.showLoading({ title: '同步中...' })
  try { await api.post('/api/mini/messages/sync'); uni.showToast({ title: '同步成功', icon: 'success' }); load() } catch {} finally { uni.hideLoading() }
}
function goChat(s: any) {
  uni.navigateTo({ url: `/packages/messages/chat/index?accountId=${s.accountId}&sessionId=${s.id}&userName=${encodeURIComponent(s.userName)}` })
  sessions.value.forEach(x => { if (x.id === s.id) x.unreadCount = 0 })
}
function onPullRefresh() { load() }
load()
</script>
<style scoped lang="scss">
.page-sessions { min-height: 100vh; background: var(--bg-page); }
.session-list { height: 100vh; }
.session-item { display: flex; align-items: center; gap: 20rpx; padding: 24rpx; margin: 16rpx 24rpx; background: #fff; border-radius: 24rpx; box-shadow: var(--shadow-sm); }
.user-avatar { width: 80rpx; height: 80rpx; border-radius: 50%; background: var(--bg-input); }
.session-info { flex: 1; min-width: 0; }
.session-name { font-size: 28rpx; font-weight: 700; color: var(--text-primary); display: block; }
.session-last { font-size: 22rpx; color: var(--text-tertiary); margin-top: 4rpx; display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.session-meta { text-align: right; flex-shrink: 0; }
.session-time { font-size: 20rpx; color: var(--text-tertiary); display: block; }
.unread-badge { background: var(--danger); color: #fff; font-size: 18rpx; padding: 2rpx 10rpx; border-radius: 20rpx; margin-top: 8rpx; display: inline-block; }
</style>
