<template>
  <view class="chat-bubble" :class="direction">
    <view v-if="showAvatar" class="cb-avatar" :class="direction" :style="avatarStyle">{{ avatarText }}</view>
    <view class="cb-content">
      <view class="cb-bubble">
        <text v-if="msgType === 'TEXT'" class="cb-text">{{ content }}</text>
        <image v-else-if="msgType === 'IMAGE'" :src="content" mode="widthFix" class="cb-image" @click="$emit('preview', content)" />
      </view>
      <view v-if="autoReplied" class="cb-ai-tag">AI 自动回复</view>
      <view v-if="timestamp" class="cb-time">{{ formatTime(timestamp) }}</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  content: string
  direction?: 'outgoing' | 'incoming'
  msgType?: 'TEXT' | 'IMAGE'
  autoReplied?: boolean
  timestamp?: string
  showAvatar?: boolean
  avatarText?: string
}>(), {
  direction: 'incoming',
  msgType: 'TEXT',
  autoReplied: false,
  showAvatar: true,
  avatarText: '',
})

defineEmits<{ (e: 'preview', url: string): void }>()

const avatarText = computed(() => props.avatarText || (props.direction === 'outgoing' ? '我' : '买'))
const avatarStyle = computed(() => ({
  background: props.direction === 'outgoing'
    ? 'linear-gradient(135deg,#4f46e5,#7c3aed)'
    : 'linear-gradient(135deg,#06b6d4,#22d3ee)',
}))

function formatTime(s: string) {
  if (!s) return ''
  return s.replace('T', ' ').slice(11, 16)
}
</script>

<style scoped lang="scss">
.chat-bubble { display: flex; align-items: flex-end; gap: 12rpx; margin-bottom: 20rpx; }
.chat-bubble.outgoing { flex-direction: row-reverse; }
.cb-avatar { width: 64rpx; height: 64rpx; border-radius: 16rpx; display: flex; align-items: center; justify-content: center; font-size: 22rpx; color: #fff; flex-shrink: 0; box-shadow: 0 2rpx 6rpx rgba(0,0,0,.08); }
.cb-content { max-width: 68%; display: flex; flex-direction: column; }
.chat-bubble.outgoing .cb-content { align-items: flex-end; }
.cb-bubble { padding: 18rpx 22rpx; font-size: 28rpx; line-height: 1.55; color: #111827; border-radius: 28rpx; }
.chat-bubble.incoming .cb-bubble { background: #fff; border: 1rpx solid #f0f0f0; border-bottom-left-radius: 8rpx; box-shadow: 0 1rpx 3rpx rgba(0,0,0,.04); }
.chat-bubble.outgoing .cb-bubble { background: linear-gradient(135deg, #4f46e5, #7c3aed); color: #fff; border-bottom-right-radius: 8rpx; box-shadow: 0 2rpx 8rpx rgba(79,70,229,.25); }
.cb-text { display: block; white-space: pre-wrap; word-break: break-all; }
.cb-image { width: 320rpx; border-radius: 18rpx; display: block; }
.cb-ai-tag { display: inline-flex; align-items: center; gap: 6rpx; font-size: 18rpx; background: rgba(79,70,229,.1); color: #4f46e5; padding: 4rpx 12rpx; border-radius: 8rpx; margin-top: 10rpx; font-weight: 600; }
.cb-ai-tag::before { content: ''; width: 8rpx; height: 8rpx; border-radius: 50%; background: #7c3aed; }
.cb-time { font-size: 18rpx; color: #9ca3af; margin-top: 6rpx; }
</style>
