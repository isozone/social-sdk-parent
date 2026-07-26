<template>
  <view class="page-rules">
    <view class="nav-bar">
      <view class="nav-back" @click="goBack"><text class="nav-arrow">‹</text><text>返回</text></view>
      <text class="nav-title">关键词规则</text>
      <text class="nav-action" @click="addRule">＋</text>
    </view>

    <view class="filter-bar">
      <input v-model="keyword" class="search-input" placeholder="搜索关键词" confirm-type="search" @confirm="reload" />
      <view class="seg">
        <view class="seg-btn" :class="{ active: filter === 'ALL' }" @click="switchFilter('ALL')">全部</view>
        <view class="seg-btn" :class="{ active: filter === 'ON' }" @click="switchFilter('ON')">启用</view>
        <view class="seg-btn" :class="{ active: filter === 'OFF' }" @click="switchFilter('OFF')">停用</view>
      </view>
    </view>

    <scroll-view scroll-y class="list-area" @scrolltolower="loadMore">
      <view v-if="loading && list.length === 0" class="loading-hint">加载中...</view>
      <view v-if="!loading && list.length === 0" class="empty-hint">
        <text class="empty-emoji">🗂</text>
        <text>暂无规则，新建一条自动回复规则</text>
        <view class="empty-btn" @click="addRule">＋ 新建规则</view>
      </view>

      <view class="rule-card" v-for="r in list" :key="r.id" @click="editRule(r)">
        <view class="rc-head">
          <view class="rc-kw">{{ r.keyword }}</view>
          <view class="rc-switch" :class="{ on: r.enabled }" @click.stop="toggle(r)">
            <view class="rc-knob" />
          </view>
        </view>
        <view class="rc-reply">{{ r.replyText }}</view>
        <view class="rc-meta">
          <text class="rc-tag">{{ matchTypeLabel(r.matchType) }}</text>
          <text class="rc-tag">优先级 {{ r.priority }}</text>
          <text class="rc-tag">命中 {{ r.hitCount }}</text>
          <text class="rc-account">{{ r.accountName || '全部账号' }}</text>
        </view>
      </view>

      <view v-if="loadingMore" class="loading-hint">加载中...</view>
      <view v-if="noMore && list.length > 0" class="end-hint">— 已到底 —</view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getRules, toggleRule, deleteRule } from '@/api/rules'
import type { RuleItem } from '@/types/rule'

const list = ref<RuleItem[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const noMore = ref(false)
const page = ref(1)
const pageSize = 20
const keyword = ref('')
const filter = ref<'ALL' | 'ON' | 'OFF'>('ALL')

onMounted(() => reload())

async function reload() {
  page.value = 1
  noMore.value = false
  list.value = []
  await load()
}

async function load() {
  if (page.value === 1) loading.value = true
  else loadingMore.value = true
  try {
    const params: any = { page: page.value, pageSize }
    if (keyword.value) params.keyword = keyword.value
    if (filter.value === 'ON') params.enabled = true
    else if (filter.value === 'OFF') params.enabled = false
    const res = await getRules(params)
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

function switchFilter(f: 'ALL' | 'ON' | 'OFF') {
  if (filter.value === f) return
  filter.value = f
  reload()
}

async function toggle(r: RuleItem) {
  try {
    await toggleRule(r.id, !r.enabled)
    r.enabled = !r.enabled
    uni.showToast({ title: r.enabled ? '已启用' : '已停用', icon: 'none' })
  } catch (e: any) {
    uni.showToast({ title: e?.message || '操作失败', icon: 'none' })
  }
}

function matchTypeLabel(t: string) {
  return { CONTAINS: '包含', EQUALS: '完全匹配', REGEX: '正则' }[t] || t
}

function addRule() {
  uni.navigateTo({ url: '/packages/rules/edit/index' })
}

function editRule(r: RuleItem) {
  uni.navigateTo({ url: `/packages/rules/edit/index?id=${r.id}` })
}

function goBack() { uni.navigateBack() }
</script>

<style scoped lang="scss">
.page-rules { min-height: 100vh; background: #f5f5f7; display: flex; flex-direction: column; }
.nav-bar { display: flex; align-items: center; justify-content: space-between; height: 88rpx; padding: 0 24rpx; background: rgba(255,255,255,.92); backdrop-filter: blur(20rpx); border-bottom: 1rpx solid #e5e7eb; flex-shrink: 0; }
.nav-back { display: flex; align-items: center; gap: 8rpx; font-size: 28rpx; color: #4f46e5; font-weight: 600; }
.nav-arrow { font-size: 36rpx; font-weight: 300; }
.nav-title { font-size: 32rpx; font-weight: 700; color: #111827; }
.nav-action { font-size: 44rpx; color: #4f46e5; font-weight: 300; }

.filter-bar { padding: 20rpx 24rpx; background: #fff; border-bottom: 1rpx solid #e5e7eb; flex-shrink: 0; }
.search-input { height: 72rpx; border: 1rpx solid #e5e7eb; border-radius: 36rpx; padding: 0 28rpx; font-size: 26rpx; background: #f9fafb; }
.seg { display: flex; gap: 8rpx; margin-top: 16rpx; }
.seg-btn { flex: 1; height: 64rpx; line-height: 64rpx; text-align: center; font-size: 24rpx; color: #6b7280; background: #f3f4f6; border-radius: 12rpx; }
.seg-btn.active { background: linear-gradient(135deg, #4f46e5, #7c3aed); color: #fff; font-weight: 600; }

.list-area { flex: 1; padding: 20rpx 24rpx; }
.loading-hint, .end-hint { text-align: center; font-size: 22rpx; color: #9ca3af; padding: 30rpx 0; }
.empty-hint { display: flex; flex-direction: column; align-items: center; padding: 80rpx 0; gap: 20rpx; font-size: 24rpx; color: #9ca3af; }
.empty-emoji { font-size: 80rpx; }
.empty-btn { margin-top: 20rpx; padding: 16rpx 40rpx; border-radius: 36rpx; background: linear-gradient(135deg, #4f46e5, #7c3aed); color: #fff; font-size: 26rpx; font-weight: 600; }

.rule-card { background: #fff; border-radius: 24rpx; padding: 28rpx 24rpx; margin-bottom: 20rpx; box-shadow: 0 2rpx 12rpx rgba(0,0,0,.03); }
.rc-head { display: flex; align-items: center; justify-content: space-between; }
.rc-kw { font-size: 30rpx; font-weight: 700; color: #111827; }
.rc-switch { width: 80rpx; height: 44rpx; border-radius: 22rpx; background: #d1d5db; position: relative; transition: background .2s; }
.rc-switch.on { background: linear-gradient(135deg, #4f46e5, #7c3aed); }
.rc-knob { position: absolute; top: 4rpx; left: 4rpx; width: 36rpx; height: 36rpx; border-radius: 50%; background: #fff; box-shadow: 0 1rpx 4rpx rgba(0,0,0,.2); transition: transform .2s; }
.rc-switch.on .rc-knob { transform: translateX(36rpx); }
.rc-reply { font-size: 26rpx; color: #4b5563; margin-top: 12rpx; line-height: 1.5; }
.rc-meta { display: flex; flex-wrap: wrap; gap: 12rpx; margin-top: 16rpx; align-items: center; }
.rc-tag { font-size: 20rpx; color: #6b7280; background: #f3f4f6; padding: 6rpx 16rpx; border-radius: 10rpx; }
.rc-account { font-size: 20rpx; color: #4f46e5; margin-left: auto; }
</style>
