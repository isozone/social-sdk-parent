<template>
  <view class="page-cs">
    <view class="nav-bar">
      <view class="nav-back" @click="goBack"><text class="nav-arrow">‹</text><text>返回</text></view>
      <text class="nav-title">AI 客服会话</text>
      <text class="nav-action" @click="reload">⟳</text>
    </view>
    <view class="filter-bar">
      <view class="account-pick" @click="pickAccount">
        <text class="ap-name">{{ accountName || '全部账号' }}</text>
        <text class="ap-arrow">▾</text>
      </view>
    </view>
    <scroll-view scroll-y class="list-area" @scrolltolower="loadMore">
      <view v-if="loading && list.length === 0" class="loading-hint">加载中...</view>
      <view v-if="!loading && list.length === 0" class="empty-hint">
        <text class="empty-emoji">🎧</text><text>暂无 AI 客服接管记录</text>
      </view>
      <view class="cs-card" v-for="(c, i) in list" :key="c.id || i" @click="openSession(c)">
        <view class="cc-head">
          <view class="cc-avatar">{{ (c.buyerName || '买')[0] }}</view>
          <view class="cc-info">
            <view class="cc-name">{{ c.buyerName || '闲鱼买家' }}</view>
            <view class="cc-sub">AI 已接管 {{ c.messageCount || 0 }} 条对话</view>
          </view>
          <view class="cc-status" :class="{ active: c.active }">{{ c.active ? '服务中' : '已结束' }}</view>
        </view>
        <view class="cc-last" v-if="c.lastMessage">{{ c.lastMessage }}</view>
        <view class="cc-time">{{ formatTime(c.updatedAt || c.createdAt) }}</view>
      </view>
      <view v-if="loadingMore" class="loading-hint">加载中...</view>
      <view v-if="noMore && list.length > 0" class="end-hint">— 已到底 —</view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getCsSessions } from '@/api/ai'
import { useAccountStore } from '@/store/modules/account'

const accountStore = useAccountStore()
const list = ref<any[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const noMore = ref(false)
const page = ref(1)
const pageSize = 20
const accountName = ref('')

onMounted(() => reload())

async function reload() { page.value = 1; noMore.value = false; list.value = []; await load() }
async function load() {
  if (page.value === 1) loading.value = true; else loadingMore.value = true
  try {
    const acc = accountStore.current?.id
    const res: any = await getCsSessions(acc || 0)
    const rows = Array.isArray(res) ? res : (res?.records || [])
    if (page.value === 1) list.value = rows; else list.value.push(...rows)
    if (rows.length < pageSize) noMore.value = true
  } catch (e: any) { uni.showToast({ title: e?.message || '加载失败', icon: 'none' }) }
  finally { loading.value = false; loadingMore.value = false }
}
async function loadMore() { if (loadingMore.value || noMore.value) return; page.value += 1; await load() }

function pickAccount() {
  uni.showToast({ title: '账号选择器待补', icon: 'none' })
}
function openSession(c: any) {
  if (c.sessionId && c.accountId) {
    uni.navigateTo({ url: `/packages/messages/chat/index?accountId=${c.accountId}&sessionId=${c.sessionId}&userName=${encodeURIComponent(c.buyerName || '买家')}` })
  }
}
function formatTime(s: string) { return s ? s.replace('T', ' ').slice(0, 16) : '' }
function goBack() { uni.navigateBack() }
</script>

<style scoped lang="scss">
.page-cs { min-height: 100vh; background: #f5f5f7; display: flex; flex-direction: column; }
.nav-bar { display: flex; align-items: center; justify-content: space-between; height: 88rpx; padding: 0 24rpx; background: rgba(255,255,255,.92); border-bottom: 1rpx solid #e5e7eb; flex-shrink: 0; }
.nav-back { display: flex; align-items: center; gap: 8rpx; font-size: 28rpx; color: #4f46e5; font-weight: 600; }
.nav-arrow { font-size: 36rpx; font-weight: 300; }
.nav-title { font-size: 32rpx; font-weight: 700; color: #111827; }
.nav-action { font-size: 36rpx; color: #4f46e5; }
.filter-bar { padding: 20rpx 24rpx; background: #fff; border-bottom: 1rpx solid #e5e7eb; flex-shrink: 0; }
.account-pick { display: flex; align-items: center; justify-content: space-between; padding: 16rpx 24rpx; background: #f9fafb; border-radius: 16rpx; }
.ap-name { font-size: 26rpx; color: #111827; }
.ap-arrow { font-size: 24rpx; color: #9ca3af; }
.list-area { flex: 1; padding: 20rpx 24rpx; }
.loading-hint, .end-hint { text-align: center; font-size: 22rpx; color: #9ca3af; padding: 30rpx 0; }
.empty-hint { display: flex; flex-direction: column; align-items: center; padding: 80rpx 0; gap: 20rpx; font-size: 24rpx; color: #9ca3af; }
.empty-emoji { font-size: 80rpx; }
.cs-card { background: #fff; border-radius: 20rpx; padding: 24rpx; margin-bottom: 16rpx; box-shadow: 0 2rpx 12rpx rgba(0,0,0,.03); }
.cc-head { display: flex; align-items: center; gap: 20rpx; }
.cc-avatar { width: 72rpx; height: 72rpx; border-radius: 50%; background: linear-gradient(135deg,#06b6d4,#22d3ee); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 28rpx; font-weight: 600; flex-shrink: 0; }
.cc-info { flex: 1; }
.cc-name { font-size: 28rpx; font-weight: 600; color: #111827; }
.cc-sub { font-size: 22rpx; color: #6b7280; margin-top: 6rpx; }
.cc-status { font-size: 20rpx; padding: 6rpx 16rpx; border-radius: 10rpx; background: #f3f4f6; color: #6b7280; }
.cc-status.active { background: rgba(16,185,129,.12); color: #059669; }
.cc-last { font-size: 24rpx; color: #6b7280; margin-top: 16rpx; padding: 16rpx 20rpx; background: #f9fafb; border-radius: 12rpx; border-left: 4rpx solid #06b6d4; }
.cc-time { font-size: 20rpx; color: #9ca3af; margin-top: 12rpx; }
</style>
