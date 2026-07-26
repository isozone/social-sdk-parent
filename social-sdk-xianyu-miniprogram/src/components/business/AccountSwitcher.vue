<template>
  <view class="account-switcher" @click="openPicker">
    <view class="as-current" :class="{ active: !!current }">
      <view class="as-dot" :class="{ on: current?.status === 'ACTIVE' || current?.enabled }" />
      <text class="as-name">{{ current?.nickname || current?.name || '选择账号' }}</text>
      <text class="as-arrow">▾</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useAccountStore } from '@/store/modules/account'

const accountStore = useAccountStore()
const current = computed(() => accountStore.current)

const emit = defineEmits<{ (e: 'change', id: number): void }>()

function openPicker() {
  const list = accountStore.list || []
  if (list.length === 0) {
    uni.showToast({ title: '暂无账号', icon: 'none' })
    return
  }
  uni.showActionSheet({
    itemList: list.map((a: any) => a.nickname || a.name || `账号 ${a.id}`),
    success: async (res) => {
      const picked = list[res.tapIndex]
      if (picked) {
        await accountStore.setCurrent(picked)
        emit('change', picked.id)
      }
    },
  })
}
</script>

<style scoped lang="scss">
.account-switcher { display: inline-flex; }
.as-current { display: inline-flex; align-items: center; gap: 12rpx; padding: 12rpx 24rpx; background: #fff; border-radius: 36rpx; border: 1rpx solid #e5e7eb; font-size: 24rpx; color: #374151; }
.as-current.active { background: rgba(79,70,229,.06); border-color: rgba(79,70,229,.2); color: #4f46e5; font-weight: 600; }
.as-dot { width: 12rpx; height: 12rpx; border-radius: 50%; background: #d1d5db; }
.as-dot.on { background: #10b981; box-shadow: 0 0 0 4rpx rgba(16,185,129,.2); }
.as-name { max-width: 280rpx; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.as-arrow { font-size: 22rpx; color: #9ca3af; }
</style>
