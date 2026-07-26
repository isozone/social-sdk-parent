<template>
  <view class="page-logs">
    <view class="nav-bar">
      <view class="nav-back" @click="goBack"><text class="nav-arrow">‹</text><text>返回</text></view>
      <text class="nav-title">自动回复日志</text>
    </view>
    <view class="filter-bar">
      <input v-model="keyword" class="search-input" placeholder="搜索消息内容或会话 ID" confirm-type="search" @confirm="reload" />
    </view>
    <scroll-view scroll-y class="list-area" @scrolltolower="loadMore">
      <view v-if="loading && list.length === 0" class="loading-hint">加载中...</view>
      <view v-if="!loading && list.length === 0" class="empty-hint">
        <text class="empty-emoji">🤖</text><text>暂无自动回复记录</text>
      </view>
      <view class="log-card" v-for="l in list" :key="l.id">
        <view class="lc-head">
          <view class="lc-ai">AI</view>
          <text class="lc-session">会话 {{ l.sessionId }}</text>
          <text class="lc-time">{{ formatTime(l.createdAt) }}</text>
        </view>
        <view class="lc-content">{{ l.replyContent }}</view>
        <view class="lc-meta">
          <text class="lc-account">账号 {{ l.accountId }}</text>
          <text class="lc-rule" v-if="l.ruleId">规则 #{{ l.ruleId }}</text>
          <text class="lc-msg">消息 {{ l.messageId }}</text>
        </view>
      </view>
      <view v-if="loadingMore" class="loading-hint">加载中...</view>
      <view v-if="noMore && list.length > 0" class="end-hint">— 已到底 —</view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getList } from '@/api/replyLogs'
import type { ReplyLogItem } from '@/api/replyLogs'

const list = ref<ReplyLogItem[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const noMore = ref(false)
const page = ref(1)
const pageSize = 20
const keyword = ref('')

onMounted(() => reload())

async function reload() { page.value = 1; noMore.value = false; list.value = []; await load() }
async function load() {
  if (page.value === 1) loading.value = true; else loadingMore.value = true
  try {
    const params: any = { page: page.value, pageSize }
    if (keyword.value) params.keyword = keyword.value
    const res = await getList(params)
    const rows = res?.records || []
    if (page.value === 1) list.value = rows; else list.value.push(...rows)
    if (rows.length < pageSize) noMore.value = true
  } catch (e: any) { uni.showToast({ title: e?.message || '加载失败', icon: 'none' }) }
  finally { loading.value = false; loadingMore.value = false }
}
async function loadMore() { if (loadingMore.value || noMore.value) return; page.value += 1; await load() }

function formatTime(s: string) { return s ? s.replace('T', ' ').slice(0, 16) : '' }
function goBack() { uni.navigateBack() }
</script>

<style scoped lang="scss">
.page-logs { min-height: 100vh; background: #f5f5f7; display: flex; flex-direction: column; }
.nav-bar { display: flex; align-items: center; justify-content: space-between; height: 88rpx; padding: 0 24rpx; background: rgba(255,255,255,.92); border-bottom: 1rpx solid #e5e7eb; flex-shrink: 0; }
.nav-back { display: flex; align-items: center; gap: 8rpx; font-size: 28rpx; color: #4f46e5; font-weight: 600; }
.nav-arrow { font-size: 36rpx; font-weight: 300; }
.nav-title { font-size: 32rpx; font-weight: 700; color: #111827; }
.filter-bar { padding: 20rpx 24rpx; background: #fff; border-bottom: 1rpx solid #e5e7eb; flex-shrink: 0; }
.search-input { height: 72rpx; border: 1rpx solid #e5e7eb; border-radius: 36rpx; padding: 0 28rpx; font-size: 26rpx; background: #f9fafb; }
.list-area { flex: 1; padding: 20rpx 24rpx; }
.loading-hint, .end-hint { text-align: center; font-size: 22rpx; color: #9ca3af; padding: 30rpx 0; }
.empty-hint { display: flex; flex-direction: column; align-items: center; padding: 80rpx 0; gap: 20rpx; font-size: 24rpx; color: #9ca3af; }
.empty-emoji { font-size: 80rpx; }
.log-card { background: #fff; border-radius: 20rpx; padding: 24rpx; margin-bottom: 16rpx; box-shadow: 0 2rpx 12rpx rgba(0,0,0,.03); }
.lc-head { display: flex; align-items: center; gap: 12rpx; }
.lc-ai { width: 48rpx; height: 48rpx; border-radius: 12rpx; background: linear-gradient(135deg, #4f46e5, #7c3aed); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 20rpx; font-weight: 700; flex-shrink: 0; }
.lc-session { font-size: 22rpx; color: #4f46e5; flex: 1; }
.lc-time { font-size: 20rpx; color: #9ca3af; }
.lc-content { font-size: 26rpx; color: #111827; margin-top: 16rpx; line-height: 1.5; padding: 16rpx 20rpx; background: #f9fafb; border-radius: 12rpx; border-left: 4rpx solid #4f46e5; }
.lc-meta { display: flex; flex-wrap: wrap; gap: 12rpx; margin-top: 16rpx; }
.lc-account, .lc-rule, .lc-msg { font-size: 20rpx; color: #6b7280; background: #f3f4f6; padding: 4rpx 12rpx; border-radius: 8rpx; }
</style>
