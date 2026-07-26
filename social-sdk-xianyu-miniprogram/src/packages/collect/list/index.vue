<template>
  <view class="page-collect">
    <view class="nav-bar">
      <view class="nav-back" @click="goBack"><text class="nav-arrow">‹</text><text>返回</text></view>
      <text class="nav-title">我的收藏</text>
      <text class="nav-action" @click="reload">⟳</text>
    </view>
    <scroll-view scroll-y class="list-area" @scrolltolower="loadMore">
      <view v-if="loading && list.length === 0" class="loading-hint">加载中...</view>
      <view v-if="!loading && list.length === 0" class="empty-hint">
        <text class="empty-emoji">⭐</text><text>暂无收藏任务</text>
      </view>
      <view class="collect-item" v-for="c in list" :key="c.id">
        <view class="ci-icon" :class="c.status.toLowerCase()">{{ statusEmoji(c.status) }}</view>
        <view class="ci-body">
          <view class="ci-url" @click="openUrl(c.productUrl)">{{ c.productUrl }}</view>
          <view class="ci-meta">
            <text class="ci-account">{{ c.accountName || '账号 ' + c.accountId }}</text>
            <text class="ci-type">{{ typeLabel(c.collectType) }}</text>
          </view>
          <view class="ci-status" :class="c.status.toLowerCase()">{{ statusLabel(c.status) }}</view>
          <view class="ci-msg" v-if="c.message">{{ c.message }}</view>
        </view>
        <view class="ci-action" v-if="c.status === 'FAILED' || c.status === 'PENDING'" @click="resync(c)">↻</view>
      </view>
      <view v-if="loadingMore" class="loading-hint">加载中...</view>
      <view v-if="noMore && list.length > 0" class="end-hint">— 已到底 —</view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getList, sync } from '@/api/collect'
import type { CollectItem } from '@/api/collect'

const list = ref<CollectItem[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const noMore = ref(false)
const page = ref(1)
const pageSize = 20

onMounted(() => reload())

async function reload() { page.value = 1; noMore.value = false; list.value = []; await load() }
async function load() {
  if (page.value === 1) loading.value = true; else loadingMore.value = true
  try {
    const res = await getList({ page: page.value, pageSize })
    const rows = res?.records || []
    if (page.value === 1) list.value = rows; else list.value.push(...rows)
    if (rows.length < pageSize) noMore.value = true
  } catch (e: any) { uni.showToast({ title: e?.message || '加载失败', icon: 'none' }) }
  finally { loading.value = false; loadingMore.value = false }
}
async function loadMore() { if (loadingMore.value || noMore.value) return; page.value += 1; await load() }

async function resync(c: CollectItem) {
  uni.showLoading({ title: '同步中...' })
  try { await sync(c.id); uni.showToast({ title: '已重新触发', icon: 'success' }); await reload() }
  catch (e: any) { uni.showToast({ title: e?.message || '操作失败', icon: 'none' }) }
  finally { uni.hideLoading() }
}

function openUrl(url: string) { uni.setClipboardData({ data: url, success: () => uni.showToast({ title: '链接已复制', icon: 'none' }) }) }
function statusLabel(s: string) { return { PENDING: '排队中', RUNNING: '采集中', COMPLETED: '已完成', FAILED: '失败' }[s] || s }
function statusEmoji(s: string) { return { PENDING: '⏳', RUNNING: '🔄', COMPLETED: '✅', FAILED: '⚠️' }[s] || '📦' }
function typeLabel(t: string) { return { SNAPSHOT: '快照', PRICE: '价格', FULL: '全量' }[t] || t }
function goBack() { uni.navigateBack() }
</script>

<style scoped lang="scss">
.page-collect { min-height: 100vh; background: #f5f5f7; display: flex; flex-direction: column; }
.nav-bar { display: flex; align-items: center; justify-content: space-between; height: 88rpx; padding: 0 24rpx; background: rgba(255,255,255,.92); border-bottom: 1rpx solid #e5e7eb; flex-shrink: 0; }
.nav-back { display: flex; align-items: center; gap: 8rpx; font-size: 28rpx; color: #4f46e5; font-weight: 600; }
.nav-arrow { font-size: 36rpx; font-weight: 300; }
.nav-title { font-size: 32rpx; font-weight: 700; color: #111827; }
.nav-action { font-size: 36rpx; color: #4f46e5; }
.list-area { flex: 1; padding: 20rpx 24rpx; }
.loading-hint, .end-hint { text-align: center; font-size: 22rpx; color: #9ca3af; padding: 30rpx 0; }
.empty-hint { display: flex; flex-direction: column; align-items: center; padding: 80rpx 0; gap: 20rpx; font-size: 24rpx; color: #9ca3af; }
.empty-emoji { font-size: 80rpx; }
.collect-item { display: flex; align-items: flex-start; gap: 20rpx; background: #fff; border-radius: 20rpx; padding: 24rpx; margin-bottom: 16rpx; box-shadow: 0 2rpx 12rpx rgba(0,0,0,.03); }
.ci-icon { width: 72rpx; height: 72rpx; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 36rpx; flex-shrink: 0; background: #f3f4f6; }
.ci-icon.completed { background: rgba(16,185,129,.12); }
.ci-icon.failed { background: rgba(239,68,68,.12); }
.ci-icon.running { background: rgba(79,70,229,.12); }
.ci-body { flex: 1; min-width: 0; }
.ci-url { font-size: 26rpx; color: #4f46e5; word-break: break-all; line-height: 1.4; }
.ci-meta { display: flex; gap: 16rpx; margin-top: 8rpx; }
.ci-account { font-size: 20rpx; color: #6b7280; }
.ci-type { font-size: 20rpx; color: #4f46e5; background: rgba(79,70,229,.08); padding: 4rpx 12rpx; border-radius: 8rpx; }
.ci-status { font-size: 22rpx; font-weight: 600; margin-top: 12rpx; display: inline-block; padding: 4rpx 16rpx; border-radius: 10rpx; background: #f3f4f6; color: #6b7280; }
.ci-status.completed { background: rgba(16,185,129,.12); color: #059669; }
.ci-status.failed { background: rgba(239,68,68,.12); color: #dc2626; }
.ci-status.running { background: rgba(79,70,229,.12); color: #4f46e5; }
.ci-msg { font-size: 22rpx; color: #9ca3af; margin-top: 8rpx; }
.ci-action { width: 64rpx; height: 64rpx; border-radius: 50%; background: rgba(79,70,229,.1); color: #4f46e5; display: flex; align-items: center; justify-content: center; font-size: 32rpx; flex-shrink: 0; }
</style>
