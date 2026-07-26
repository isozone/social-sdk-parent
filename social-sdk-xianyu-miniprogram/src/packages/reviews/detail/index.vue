<template>
  <view class="page-detail">
    <view class="nav-bar">
      <view class="nav-back" @click="goBack"><text class="nav-arrow">‹</text><text>返回</text></view>
      <text class="nav-title">评价详情</text>
    </view>
    <scroll-view scroll-y class="content-area">
      <view v-if="loading" class="loading-hint">加载中...</view>
      <view v-if="!loading && data">
        <view class="product-card">
          <view class="pc-title">{{ data.productTitle }}</view>
          <view class="pc-sub">买家：{{ data.buyerName }} · 订单 #{{ data.orderId }}</view>
        </view>

        <view class="section">
          <view class="section-label">买家评价</view>
          <view class="rating-row">
            <text v-for="i in 5" :key="i" class="star" :class="{ on: i <= data.rating }">★</text>
            <text class="rating-num">{{ data.rating }}.0</text>
          </view>
          <view class="review-content">{{ data.content || '买家未留言' }}</view>
          <view class="review-time">{{ formatTime(data.createdAt) }}</view>
        </view>

        <view class="section" v-if="data.status === 'PENDING'">
          <view class="section-label">回复评价</view>
          <textarea v-model="replyContent" class="field-textarea" placeholder="感谢买家的支持，主动回复可提升复购..." maxlength="500" />
          <view class="field-count">{{ replyContent.length }} / 500</view>
          <view class="rating-pick">
            <text class="rp-label">卖家评分</text>
            <view class="rp-stars">
              <text v-for="i in 5" :key="i" class="star big" :class="{ on: i <= myRating }" @click="myRating = i">★</text>
            </view>
          </view>
          <view class="btn-primary" @click="submitReview">提交评价</view>
        </view>

        <view class="section" v-if="data.status === 'PENDING'">
          <view class="section-label">申请退款</view>
          <input v-model="refundReason" class="field-input" placeholder="退款原因（如：商品瑕疵、买家协商）" />
          <view class="btn-danger" @click="applyRefund">申请退款</view>
        </view>

        <view class="section" v-if="data.status === 'REVIEWED'">
          <view class="section-label">已回复</view>
          <view class="review-content">{{ data.content || '—' }}</view>
        </view>

        <view class="section" v-if="data.status === 'REFUNDED'">
          <view class="section-label">退款状态</view>
          <view class="refund-info">该订单已退款，评价已关闭</view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getList, submitReview, refund } from '@/api/reviews'
import type { ReviewItem } from '@/api/reviews'

const data = ref<ReviewItem | null>(null)
const loading = ref(false)
const reviewId = ref<number>(0)
const replyContent = ref('')
const myRating = ref(5)
const refundReason = ref('')

onMounted(async () => {
  const pages = getCurrentPages() as any[]
  const cur = pages[pages.length - 1]?.options || {}
  reviewId.value = Number(cur.id || 0)
  await load()
})

async function load() {
  loading.value = true
  try {
    const res = await getList({ id: reviewId.value, pageSize: 1 })
    data.value = (res?.records || [])[0] || null
    if (data.value) replyContent.value = data.value.content || ''
  } catch (e: any) { uni.showToast({ title: e?.message || '加载失败', icon: 'none' }) }
  finally { loading.value = false }
}

async function submitReview() {
  if (!replyContent.value) { uni.showToast({ title: '请填写回复内容', icon: 'none' }); return }
  uni.showLoading({ title: '提交中...' })
  try {
    await submitReview({ reviewId: reviewId.value, content: replyContent.value, rating: myRating.value })
    uni.showToast({ title: '已提交', icon: 'success' })
    await load()
  } catch (e: any) { uni.showToast({ title: e?.message || '提交失败', icon: 'none' }) }
  finally { uni.hideLoading() }
}

async function applyRefund() {
  if (!refundReason.value) { uni.showToast({ title: '请填写退款原因', icon: 'none' }); return }
  uni.showModal({
    title: '确认退款', content: '退款后评价将关闭，且无法撤销', success: async r => {
      if (!r.confirm) return
      uni.showLoading({ title: '处理中...' })
      try {
        await refund({ reviewId: reviewId.value, reason: refundReason.value })
        uni.showToast({ title: '退款已申请', icon: 'success' })
        await load()
      } catch (e: any) { uni.showToast({ title: e?.message || '操作失败', icon: 'none' }) }
      finally { uni.hideLoading() }
    }
  })
}

function formatTime(s: string) { return s ? s.replace('T', ' ').slice(0, 16) : '' }
function goBack() { uni.navigateBack() }
</script>

<style scoped lang="scss">
.page-detail { min-height: 100vh; background: #f5f5f7; display: flex; flex-direction: column; }
.nav-bar { display: flex; align-items: center; justify-content: space-between; height: 88rpx; padding: 0 24rpx; background: rgba(255,255,255,.92); border-bottom: 1rpx solid #e5e7eb; flex-shrink: 0; }
.nav-back { display: flex; align-items: center; gap: 8rpx; font-size: 28rpx; color: #4f46e5; font-weight: 600; }
.nav-arrow { font-size: 36rpx; font-weight: 300; }
.nav-title { font-size: 32rpx; font-weight: 700; color: #111827; }
.content-area { flex: 1; padding: 20rpx 24rpx; }
.loading-hint { text-align: center; font-size: 22rpx; color: #9ca3af; padding: 60rpx 0; }
.product-card { background: #fff; border-radius: 20rpx; padding: 28rpx 24rpx; margin-bottom: 16rpx; box-shadow: 0 2rpx 12rpx rgba(0,0,0,.03); }
.pc-title { font-size: 30rpx; font-weight: 700; color: #111827; }
.pc-sub { font-size: 22rpx; color: #6b7280; margin-top: 8rpx; }
.section { background: #fff; border-radius: 20rpx; padding: 28rpx 24rpx; margin-bottom: 16rpx; box-shadow: 0 2rpx 12rpx rgba(0,0,0,.03); }
.section-label { font-size: 26rpx; font-weight: 600; color: #374151; margin-bottom: 16rpx; }
.rating-row { display: flex; align-items: center; gap: 4rpx; }
.star { font-size: 28rpx; color: #d1d5db; }
.star.on { color: #f59e0b; }
.star.big { font-size: 44rpx; }
.rating-num { font-size: 24rpx; color: #6b7280; margin-left: 12rpx; }
.review-content { font-size: 26rpx; color: #4b5563; margin-top: 16rpx; line-height: 1.6; }
.review-time { font-size: 20rpx; color: #9ca3af; margin-top: 12rpx; }
.field-textarea { width: 100%; min-height: 200rpx; border: 1rpx solid #e5e7eb; border-radius: 16rpx; padding: 16rpx 20rpx; font-size: 28rpx; background: #fafafa; }
.field-count { text-align: right; font-size: 20rpx; color: #9ca3af; margin-top: 8rpx; }
.rating-pick { display: flex; align-items: center; justify-content: space-between; margin: 20rpx 0; }
.rp-label { font-size: 26rpx; color: #374151; }
.rp-stars { display: flex; gap: 8rpx; }
.field-input { width: 100%; height: 80rpx; border: 1rpx solid #e5e7eb; border-radius: 16rpx; padding: 0 20rpx; font-size: 28rpx; background: #fafafa; }
.btn-primary { height: 88rpx; line-height: 88rpx; text-align: center; background: linear-gradient(135deg, #4f46e5, #7c3aed); color: #fff; border-radius: 44rpx; font-size: 30rpx; font-weight: 600; margin-top: 20rpx; }
.btn-danger { height: 88rpx; line-height: 88rpx; text-align: center; border: 2rpx solid #ef4444; color: #ef4444; border-radius: 44rpx; font-size: 30rpx; font-weight: 600; margin-top: 20rpx; }
.refund-info { font-size: 26rpx; color: #ef4444; padding: 20rpx; background: rgba(239,68,68,.06); border-radius: 16rpx; }
</style>
