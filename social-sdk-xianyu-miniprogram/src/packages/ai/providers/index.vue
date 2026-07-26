<template>
  <view class="page-providers">
    <view class="nav-bar">
      <view class="nav-back" @click="goBack"><text class="nav-arrow">‹</text><text>返回</text></view>
      <text class="nav-title">AI 厂商</text>
      <text class="nav-action" @click="reload">⟳</text>
    </view>
    <scroll-view scroll-y class="content-area">
      <view v-if="loading" class="loading-hint">加载中...</view>
      <view v-if="!loading && list.length === 0" class="empty-hint">
        <text class="empty-emoji">🤖</text><text>暂未配置任何 AI 厂商</text>
      </view>
      <view class="provider-card" v-for="p in list" :key="p.id || p.name" @click="expand(p)">
        <view class="pc-head">
          <view class="pc-logo" :style="{ background: logoGradient(p.name) }">{{ (p.name || '?')[0] }}</view>
          <view class="pc-info">
            <view class="pc-name">{{ p.name }}</view>
            <view class="pc-sub">{{ p.models?.length || 0 }} 个模型 · {{ p.enabled ? '已启用' : '停用' }}</view>
          </view>
          <view class="pc-status" :class="{ on: p.enabled }">{{ p.enabled ? '●' : '○' }}</view>
        </view>
        <view class="pc-models" v-if="p.expanded && p.models?.length">
          <view class="model-chip" v-for="m in p.models" :key="m.id || m.name" :class="{ default: m.default }">
            <text class="mc-name">{{ m.name }}</text>
            <text class="mc-tag" v-if="m.default">默认</text>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getProviders, getModels } from '@/api/ai'

const list = ref<any[]>([])
const loading = ref(false)

onMounted(() => reload())

async function reload() {
  loading.value = true
  try {
    const res = await getProviders()
    list.value = (res || []).map((p: any) => ({ ...p, expanded: false, models: [] }))
  } catch (e: any) { uni.showToast({ title: e?.message || '加载失败', icon: 'none' }) }
  finally { loading.value = false }
}

async function expand(p: any) {
  if (p.expanded) { p.expanded = false; return }
  if (!p.models?.length) {
    try { p.models = await getModels(p.name) } catch {}
  }
  p.expanded = true
}

function logoGradient(name: string) {
  const palettes: Record<string, string> = {
    OpenAI: 'linear-gradient(135deg,#10a37f,#1abc9c)',
    DeepSeek: 'linear-gradient(135deg,#4f46e5,#7c3aed)',
    Qwen: 'linear-gradient(135deg,#6366f1,#a855f7)',
    Moonshot: 'linear-gradient(135deg,#f97316,#ef4444)',
  }
  return palettes[name] || 'linear-gradient(135deg,#4f46e5,#7c3aed)'
}
function goBack() { uni.navigateBack() }
</script>

<style scoped lang="scss">
.page-providers { min-height: 100vh; background: #f5f5f7; display: flex; flex-direction: column; }
.nav-bar { display: flex; align-items: center; justify-content: space-between; height: 88rpx; padding: 0 24rpx; background: rgba(255,255,255,.92); border-bottom: 1rpx solid #e5e7eb; flex-shrink: 0; }
.nav-back { display: flex; align-items: center; gap: 8rpx; font-size: 28rpx; color: #4f46e5; font-weight: 600; }
.nav-arrow { font-size: 36rpx; font-weight: 300; }
.nav-title { font-size: 32rpx; font-weight: 700; color: #111827; }
.nav-action { font-size: 36rpx; color: #4f46e5; }
.content-area { flex: 1; padding: 20rpx 24rpx; }
.loading-hint { text-align: center; font-size: 22rpx; color: #9ca3af; padding: 60rpx 0; }
.empty-hint { display: flex; flex-direction: column; align-items: center; padding: 80rpx 0; gap: 20rpx; font-size: 24rpx; color: #9ca3af; }
.empty-emoji { font-size: 80rpx; }
.provider-card { background: #fff; border-radius: 20rpx; padding: 24rpx; margin-bottom: 16rpx; box-shadow: 0 2rpx 12rpx rgba(0,0,0,.03); }
.pc-head { display: flex; align-items: center; gap: 20rpx; }
.pc-logo { width: 72rpx; height: 72rpx; border-radius: 20rpx; display: flex; align-items: center; justify-content: center; font-size: 32rpx; color: #fff; font-weight: 700; flex-shrink: 0; }
.pc-info { flex: 1; }
.pc-name { font-size: 30rpx; font-weight: 700; color: #111827; }
.pc-sub { font-size: 22rpx; color: #6b7280; margin-top: 6rpx; }
.pc-status { font-size: 32rpx; color: #d1d5db; }
.pc-status.on { color: #10b981; }
.pc-models { display: flex; flex-wrap: wrap; gap: 12rpx; margin-top: 20rpx; padding-top: 20rpx; border-top: 1rpx solid #f3f4f6; }
.model-chip { display: flex; align-items: center; gap: 8rpx; padding: 12rpx 24rpx; border-radius: 16rpx; background: #f3f4f6; font-size: 24rpx; color: #6b7280; }
.model-chip.default { background: rgba(79,70,229,.1); color: #4f46e5; font-weight: 600; }
.mc-tag { font-size: 18rpx; background: #4f46e5; color: #fff; padding: 2rpx 10rpx; border-radius: 8rpx; }
</style>
