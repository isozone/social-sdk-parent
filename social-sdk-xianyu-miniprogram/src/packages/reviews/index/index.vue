<template>
  <view class="page-reviews">
    <view class="nav-bar">
      <view class="nav-back" @click="goBack"><text class="nav-arrow">‹</text><text>返回</text></view>
      <text class="nav-title">评价退款</text>
    </view>

    <view class="seg-bar">
      <view class="seg-btn" :class="{ active: filter === 'ALL' }" @click="switchFilter('ALL')">全部</view>
      <view class="seg-btn" :class="{ active: filter === 'PENDING' }" @click="switchFilter('PENDING')">待处理</view>
      <view class="seg-btn" :class="{ active: filter === 'REVIEWED' }" @click="switchFilter('REVIEWED')">已评价</view>
      <view class="seg-btn" :class="{ active: filter === 'REFUNDED' }" @click="switchFilter('REFUNDED')">已退款</view>
    </view>

    <scroll-view scroll-y class="list-area" @scrolltolower="loadMore">
      <view v-if="loading && list.length === 0" class="loading-hint">加载中...</view>
      <view v-if="!loading && list.length === 0" class="empty-hint">
        <text class="empty-emoji">��</text><text>暂无评价记录</text>
      </view>

      <view class="review-card" v-for="r in list" :key="r.id" @click="openDetail(r)">
        <view class="rc-head">
          <view class="rc-rating">
            <text v-for="i in 5" :key="i" class="star" :class="{ on: i <= r.rating }">★</text>
          </view>
          <view class="rc-status" :class="r.status.toLowerCase()">{{ statusLabel(r.status) }}</view>
        </view>
        <view class="rc-product">{{ r.productTitle }}</view>
        <view class="rc-content" v-if="r.content">「{{ r.content }}」</view>
        <view class="rc-content empty" v-else>买家未留言</view>
        <view class="rc-meta">
          <text class="rc-buyer">{{ r.buyerName }}</text>
          <text class="rc-time">{{ formatTime(r.createdAt) }}</text>
        </view>
      </view>

      <view v-if="loadingMore" class="loading-hint">加载中...</view>
      <view v-if="noMore && list.length > 0" class="end-hint">— 已到底 —</view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getList } from '@/api/reviews'
import type { ReviewItem } from '@/api/reviews'

const list = ref<ReviewItem[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const noMore = ref(false)
const page = ref(1)
const pageSize = 20
const filter = ref<'ALL' | 'PENDING' | 'REVIEWED' | 'REFUNDED'>('ALL')

onMounted(() => reload())

async function reload() {
  page.value = 1; noMore.value = false; list.value = []
  await load()
}

async function load() {
  if (page.value === 1) loading.value = true; else loadingMore.value = true
  try {
    const params: any = { page: page.value, pageSize }
    if (filter.value !== 'ALL') params.status = filter.value
    const res = await getList(params)
    const rows = res?.records || []
    if (page.value === 1) list.value = rows; else list.value.push(...rows)
    if (rows.length < pageSize) noMore.value = true
  } catch (e: any) { uni.showToast({ title: e?.message || '加载失败', icon: 'none' }) }
  finally { loading.value = false; loadingMore.value = false }
}

async function loadMore() { if (loadingMore.value || noMore.value) return; page.value += 1; await load() }

function switchFilter(f: any) { if (filter.value === f) return; filter.value = f; reload() }

function openDetail(r: ReviewItem) { uni.navigateTo({ url: `/packages/reviews/detail/index?id=${r.id}` }) }
function statusLabel(s: string) { return { PENDING: '待处理', REVIEWED: '已评价', REFUNDED: '已退款' }[s] || s }
function formatTime(s: string) { return s ? s.replace('T', ' ').slice(0, 16) : '' }
function goBack() { uni.navigateBack() }
</script>

<style scoped lang="scss">
.page-reviews { min-height: 100vh; background: #f5f5f7; display: flex; flex-direction: column; }
.nav-bar { display: flex; align-items: center; justify-content: space-between; height: 88rpx; padding: 0 24rpx; background: rgba(255,255,255,.92); border-bottom: 1rpx solid #e5e7eb; flex-shrink: 0; }
.nav-back { display: flex; align-items: center; gap: 8rpx; font-size: 28rpx; color: #4f46e5; font-weight: 600; }
.nav-arrow { font-size: 36rpx; font-weight: 300; }
.nav-title { font-size: 32rpx; font-weight: 700; color: #111827; }
.seg-bar { display: flex; gap: 8rpx; padding: 20rpx 24rpx; background: #fff; border-bottom: 1rpx solid #e5e7eb; flex-shrink: 0; }
.seg-btn { flex: 1; height: 64rpx; line-height: 64rpx; text-align: center; font-size: 22rpx; color: #6b7280; background: #f3f4f6; border-radius: 12rpx; }
.seg-btn.active { background: linear-gradient(135deg, #4f46e5, #7c3aed); color: #fff; font-weight: 600; }
.list-area { flex: 1; padding: 20rpx 24rpx; }
.loading-hint, .end-hint { text-align: center; font-size: 22rpx; color: #9ca3af; padding: 30rpx 0; }
.empty-hint { display: flex; flex-direction: column; align-items: center; padding: 80rpx 0; gap: 20rpx; font-size: 24rpx; color: #9ca3af; }
.empty-emoji { font-size: 80rpx; }
.review-card { background: #fff; border-radius: 20rpx; padding: 28rpx 24rpx; margin-bottom: 16rpx; box-shadow: 0 2rpx 12rpx rgba(0,0,0,.03); }
.rc-head { display: flex; align-items: center; justify-content: space-between; }
.rc-rating { display: flex; gap: 4rpx; }
.star { font-size: 28rpx; color: #d1d5db; }
.star.on { color: #f59e0b; }
.rc-status { font-size: 20rpx; padding: 6rpx 16rpx; border-radius: 10rpx; background: #f3f4f6; color: #6b7280; }
.rc-status.pending { background: rgba(245,158,11,.12); color: #d97706; }
.rc-status.reviewed { background: rgba(16,185,129,.12); color: #059669; }
.rc-status.refunded { background: rgba(239,68,68,.12); color: #dc2626; }
.rc-product { font-size: 28rpx; font-weight: 600; color: #111827; margin-top: 16rpx; }
.rc-content { font-size: 24rpx; color: #6b7280; margin-top: 8rpx; line-height: 1.5; }
.rc-content.empty { color: #9ca3af; font-style: italic; }
.rc-meta { display: flex; align-items: center; justify-content: space-between; margin-top: 16rpx; }
.rc-buyer { font-size: 20rpx; color: #4f46e5; }
.rc-time { font-size: 20rpx; color: #9ca3af; }
</style>
