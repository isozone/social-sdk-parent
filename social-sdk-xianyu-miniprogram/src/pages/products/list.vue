<template>
  <view class="page-products">
    <view class="header-bar">
      <text class="title">商品管理</text>
      <navigator url="/packages/products/publish/index" hover-class="navigator-hover">
        <button class="publish-btn">+ 发布</button>
      </navigator>
    </view>

    <view class="tabs-row">
      <view class="tab-item" :class="{ active: currentTab === 'xianyu' }" @click="currentTab = 'xianyu'">闲鱼</view>
      <view class="tab-item" :class="{ active: currentTab === 'local' }" @click="currentTab = 'local'">本地</view>
    </view>

    <view class="search-bar">
      <search-input v-model="keyword" placeholder="搜索商品" @search="loadProducts" />
    </view>

    <scroll-view scroll-y class="main-scroll" @refresherpulling="onPullRefresh" :refresher-enabled="true" :refresher-triggered="loading">
      <view class="product-card" v-for="product in products" :key="product.id" @click="goDetail(product.id)">
        <image class="product-image" :src="product.images?.[0] || '/static/tab/home.svg'" mode="aspectFill" />
        <view class="product-info">
          <text class="product-title">{{ product.itemTitle || product.title }}</text>
          <text class="product-price">¥ {{ product.price }}</text>
          <text class="product-meta">库存: {{ product.stock }} · {{ product.viewCount || 0 }} 浏览</text>
        </view>
        <view class="product-actions">
          <button class="action-btn" v-if="product.status !== 'ON_SALE'" @click.stop="shelfOn(product.id)">上架</button>
          <button class="action-btn" v-else @click.stop="shelfOff(product.id)">下架</button>
          <button class="action-btn polish" @click.stop="polish(product.id)">✨</button>
        </view>
      </view>
    </scroll-view>

    <empty-state v-if="products.length === 0 && !loading" text="暂无商品" action-text="发布商品" @action="() => uni.navigateTo({ url: '/packages/products/publish/index' })" />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { api } from '@/api/request'
import type { ProductItem } from '@/types/product'
import SearchInput from '@/components/common/SearchInput.vue'
import EmptyState from '@/components/common/EmptyState.vue'

const products = ref<ProductItem[]>([])
const loading = ref(false)
const currentTab = ref('xianyu')
const keyword = ref('')

async function loadProducts() {
  loading.value = true
  try {
    const res = await api.get('/api/mini/products', { keyword: keyword.value, page: 1, size: 50 }, false)
    if (Array.isArray(res)) products.value = res
    else if (res?.records) products.value = res.records
  } finally {
    loading.value = false
  }
}

async function shelfOn(id: number) {
  try { await api.post(`/api/mini/products/${id}/shelf-on`) ; await loadProducts(); uni.showToast({ title: '已上架', icon: 'success' }) } catch { uni.showToast({ title: '操作失败', icon: 'none' }) }
}

async function shelfOff(id: number) {
  try { await api.post(`/api/mini/products/${id}/shelf-off`) ; await loadProducts(); uni.showToast({ title: '已下架', icon: 'success' }) } catch { uni.showToast({ title: '操作失败', icon: 'none' }) }
}

async function polish(id: number) {
  try { await api.post(`/api/mini/products/${id}/polish`); uni.showToast({ title: '擦亮成功', icon: 'success' }) } catch { uni.showToast({ title: '擦亮失败', icon: 'none' }) }
}

function goDetail(id: number) {
  uni.navigateTo({ url: `/packages/products/detail/index?id=${id}` })
}

function onPullRefresh() {
  loadProducts()
}

onMounted(() => loadProducts())
</script>

<style scoped lang="scss">
.page-products { min-height: 100vh; background: var(--bg-page); }
.header-bar { display: flex; justify-content: space-between; align-items: center; padding: 24rpx; background: #fff; border-bottom: 1rpx solid var(--border-color); }
.title { font-size: 36rpx; font-weight: 700; color: var(--text-primary); }
.publish-btn { height: 56rpx; padding: 0 32rpx; background: var(--brand-gradient); color: #fff; border: none; border-radius: 16rpx; font-size: 26rpx; }
.tabs-row { display: flex; gap: 16rpx; padding: 24rpx; background: #fff; }
.tab-item { flex: 1; text-align: center; padding: 16rpx; border-radius: 16rpx; background: var(--bg-input); font-size: 26rpx; color: var(--text-secondary); transition: all 0.2s; &.active { background: var(--brand); color: #fff; font-weight: 600; } }
.search-bar { padding: 0 24rpx 16rpx; }
.main-scroll { height: calc(100vh - 320rpx); }
.product-card { background: #fff; border-radius: 24rpx; margin: 16rpx 24rpx; padding: 24rpx; box-shadow: var(--shadow-sm); display: flex; gap: 20rpx; }
.product-image { width: 160rpx; height: 160rpx; border-radius: 16rpx; background: var(--bg-input); }
.product-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 8rpx; }
.product-title { font-size: 28rpx; font-weight: 700; color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.product-price { font-size: 32rpx; font-weight: 800; color: var(--danger); }
.product-meta { font-size: 22rpx; color: var(--text-tertiary); }
.product-actions { display: flex; flex-direction: column; gap: 12rpx; }
.action-btn { height: 48rpx; padding: 0 20rpx; border: none; border-radius: 12rpx; font-size: 22rpx; background: var(--bg-input); color: var(--text-secondary); }
.action-btn.polish { background: rgba(245,158,11,0.1); color: #f59e0b; }
</style>
