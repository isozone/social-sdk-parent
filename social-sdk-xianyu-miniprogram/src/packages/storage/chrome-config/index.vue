<template>
  <view class="page">
    <view class="nav">
      <text class="back" @click="goBack">‹</text>
      <text class="title">Chrome 配置</text>
      <text class="act" @click="detectAll">⟳</text>
    </view>
    <view class="card">
      <text class="title-text">检测与保存</text>
      <button @click="detectOne">检测当前环境</button>
      <button @click="detectAll">检测全部账号</button>
      <button @click="saveCfg">保存配置</button>
      <button @click="validateCfg">校验配置</button>
    </view>
    <view class="card" v-if="result">
      <text class="title-text">检测结果</text>
      <text class="json">{{ pretty }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { detect, detectAll as detectAllApi, save, validate } from '@/api/chromeConfig'

const result = ref<any>(null)
const pretty = computed(() => {
  try { return JSON.stringify(result.value, null, 2) } catch { return String(result.value) }
})

async function detectOne() {
  try {
    result.value = await detect()
    uni.showToast({ title: '检测完成', icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: e?.message || '检测失败', icon: 'none' })
  }
}

async function detectAll() {
  try {
    result.value = await detectAllApi()
    uni.showToast({ title: '检测完成', icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: e?.message || '检测失败', icon: 'none' })
  }
}

async function saveCfg() {
  try {
    result.value = await save(result.value || {})
    uni.showToast({ title: '已保存', icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: e?.message || '保存失败', icon: 'none' })
  }
}

async function validateCfg() {
  try {
    result.value = await validate(result.value || {})
    uni.showToast({ title: '校验完成', icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: e?.message || '校验失败', icon: 'none' })
  }
}

function goBack() {
  uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/profile/index' }) })
}
</script>

<style scoped lang="scss">
.page { min-height: 100vh; background: #f5f5f7; }
.nav { height: 88rpx; padding: 0 24rpx; background: #fff; display: flex; align-items: center; justify-content: space-between; border-bottom: 1rpx solid #e5e7eb; }
.back { font-size: 48rpx; color: #6b7280; }
.title { font-size: 32rpx; font-weight: 700; }
.act { font-size: 34rpx; color: #4f46e5; }
.card { margin: 24rpx; background: #fff; border-radius: 24rpx; padding: 28rpx; display: flex; flex-direction: column; gap: 16rpx; }
.title-text { font-size: 30rpx; font-weight: 700; color: #111827; }
button { height: 72rpx; line-height: 72rpx; border-radius: 16rpx; background: #4f46e5; color: #fff; font-size: 26rpx; }
.json { white-space: pre-wrap; word-break: break-all; font-size: 22rpx; color: #6b7280; }
</style>
