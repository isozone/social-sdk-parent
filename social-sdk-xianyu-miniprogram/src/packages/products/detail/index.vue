<template>
  <view class="page-product-detail">
    <view class="nav-bar">
      <navigator url="/packages/products/list/index" hover-class="none" class="nav-back"><text class="nav-arrow">‹</text><text>返回</text></navigator>
      <text class="nav-title">商品详情</text>
      <view class="nav-actions"></view>
    </view>
    <scroll-view scroll-y class="main-scroll">
      <view class="carousel">
        <image v-if="product.images?.length" :src="product.images[0]" mode="aspectFill" class="carousel-img" />
        <view v-else class="carousel-placeholder">🐟</view>
        <view class="dots"><text v-for="(img,i) in product.images" :key="i" class="dot" :class="{active:i===0}" /></view>
      </view>
      <view class="info-card">
        <view class="tags">
          <text class="chip" :class="product.status==='ON_SALE'?'chip-green':product.status==='OFF_SALE'?'chip-gray':'chip-amber'">{{ statusText }}</text>
          <text class="chip chip-violet">闲鱼商品</text>
        </view>
        <text class="p-title">{{ product.itemTitle || product.title }}</text>
        <view class="price-row"><text class="price">¥{{ product.price }}</text><text v-if="product.originalPrice" class="original">¥{{ product.originalPrice }}</text></view>
        <view class="stats-grid">
          <view class="stat-cell"><text class="val">{{ product.viewCount || 0 }}</text><text class="lbl">浏览</text></view>
          <view class="stat-cell"><text class="val">{{ product.favoriteCount || 0 }}</text><text class="lbl">收藏</text></view>
          <view class="stat-cell"><text class="val">{{ product.stock }}</text><text class="lbl">库存</text></view>
          <view class="stat-cell"><text class="val">{{ product.categoryName || '--' }}</text><text class="lbl">分类</text></view>
        </view>
        <view v-if="product.description" class="desc-box"><text class="desc-title">商品描述</text><text class="desc-text">{{ product.description }}</text></view>
      </view>
      <view class="action-panel">
        <view class="a-btn" @click="polish"><text class="a-icon">✨</text><text>擦亮</text></view>
        <view class="a-btn" @click="shelfToggle"><text class="a-icon">{{ product.status === 'ON_SALE' ? '⏸' : '▶' }}</text><text>{{ product.status === 'ON_SALE' ? '下架' : '上架' }}</text></view>
        <view class="a-btn" @click="editProduct"><text class="a-icon">✏️</text><text>编辑</text></view>
        <view class="a-btn" @click="deleteProduct"><text class="a-icon">🗑</text><text class="del-text">删除</text></view>
      </view>
    </scroll-view>
  </view>
</template>
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import api from '@/api'
const product = ref<any>({})
const statusText = computed(() => ({ ON_SALE: '在售', OFF_SALE: '已下架', DRAFT: '草稿' }[product.value.status] || product.value.status))
onMounted(async () => {
  const pages = getCurrentPages() as any[]
  const prev = pages[pages.length - 2]?.options || {}
  if (prev.id) await loadDetail(Number(prev.id))
})
async function loadDetail(id: number) {
  try { product.value = await api.get(`/api/mini/products/${id}`, undefined, false) }
  catch (e: any) { uni.showToast({ title: e?.message || '加载失败', icon: 'none' }) }
}
async function polish() { try { await api.post(`/api/mini/products/${product.value.id}/polish`); uni.showToast({ title: '擦亮成功', icon: 'success' }) } catch {} }
async function shelfToggle() {
  const action = product.value.status === 'ON_SALE' ? 'off' : 'on'
  try { await api.post(`/api/mini/products/${product.value.id}/shelf-${action}`); uni.showToast({ title: action === 'on' ? '已上架' : '已下架', icon: 'success' }); await loadDetail(product.value.id) }
  catch (e: any) { uni.showToast({ title: e?.message || '操作失败', icon: 'none' }) }
}
function editProduct() { uni.showToast({ title: '编辑功能开发中', icon: 'none' }) }
function deleteProduct() {
  uni.showModal({ title: '删除确认', content: '确定删除此商品？', confirmColor: '#ef4444', success: async res => {
    if (!res.confirm) return
    try { await api.delete(`/api/mini/products/${product.value.id}`); uni.navigateBack() } catch {}
  }})
}
</script>
<style scoped lang="scss">
.page-product-detail{min-height:100vh;background:#f5f5f7}
.nav-bar{display:flex;align-items:center;justify-content:space-between;height:88rpx;padding:0 24rpx;background:#fff;border-bottom:1rpx solid #e5e7eb}
.nav-back{display:flex;align-items:center;gap:8rpx;font-size:28rpx;color:#4f46e5;font-weight:600;text-decoration:none}.nav-arrow{font-size:36rpx;font-weight:300}.nav-title{font-size:32rpx;font-weight:700;color:#111827}.nav-actions{display:flex;gap:16rpx}
.main-scroll{height:calc(100vh - 88rpx)}
.carousel{height:480rpx;background:linear-gradient(135deg,#e0e7ff,#c7d2fe);display:flex;align-items:center;justify-content:center;position:relative}
.carousel-img{width:100%;height:100%}.carousel-placeholder{font-size:120rpx}
.dots{position:absolute;bottom:20rpx;left:50%;transform:translateX(-50%);display:flex;gap:10rpx}.dot{width:12rpx;height:12rpx;border-radius:6rpx;background:rgba(255,255,255,.4)}.dot.active{width:32rpx;background:#fff;border-radius:8rpx}
.info-card{background:#fff;border-radius:28rpx 28rpx 0 0;margin-top:-28rpx;padding:28rpx 24rpx;position:relative;z-index:1}
.tags{display:flex;gap:10rpx;margin-bottom:16rpx}.chip{font-size:20rpx;padding:6rpx 14rpx;border-radius:8rpx;font-weight:600}.chip-green{background:rgba(16,185,129,.1);color:#10b981}.chip-violet{background:rgba(124,58,237,.1);color:#7c3aed}.chip-amber{background:rgba(245,158,11,.1);color:#f59e0b}.chip-gray{background:rgba(156,163,175,.1);color:#9ca3af}
.p-title{font-size:34rpx;font-weight:700;color:#111827;line-height:1.5;display:block;margin-bottom:14rpx}
.price-row{display:flex;align-items:baseline;gap:12rpx;margin-bottom:24rpx}.price{font-size:44rpx;font-weight:800;color:#ef4444}.original{font-size:26rpx;color:#9ca3af;text-decoration:line-through}
.stats-grid{display:flex;gap:12rpx;margin-bottom:20rpx}.stat-cell{flex:1;text-align:center;background:#f5f5f7;border-radius:12rpx;padding:16rpx 8rpx}
.val{font-size:28rpx;font-weight:800;color:#111827;line-height:1}.lbl{font-size:20rpx;color:#9ca3af;margin-top:6rpx;display:block}
.desc-box{background:#f5f5f7;border-radius:16rpx;padding:20rpx}.desc-title{font-size:26rpx;font-weight:700;color:#111827;display:block;margin-bottom:10rpx}.desc-text{font-size:24rpx;color:#6b7280;line-height:1.7;display:block}
.action-panel{display:flex;gap:12rpx;padding:20rpx 24rpx}.a-btn{flex:1;background:#fff;border-radius:20rpx;padding:24rpx 8rpx;text-align:center;border:1rpx solid #e5e7eb}.a-btn:active{transform:scale(.95)}.a-icon{font-size:32rpx;display:block;margin-bottom:8rpx}.del-text{color:#ef4444}
</style>
