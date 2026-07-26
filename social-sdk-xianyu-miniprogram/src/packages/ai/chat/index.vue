<template>
  <view class="page-ai-chat">
    <view class="nav-bar">
      <view class="nav-back" @click="goBack"><text class="nav-arrow">‹</text><text>返回</text></view>
      <text class="nav-title">AI 对话测试</text>
      <text class="nav-action" @click="clearChat">清空</text>
    </view>
    <scroll-view scroll-y class="chat-area" :scroll-into-view="anchorId" :scroll-with-animation="true">
      <view v-if="messages.length === 0" class="empty-hint">
        <text class="empty-emoji">💬</text>
        <text>发送消息测试 AI 回复效果</text>
        <view class="quick-row">
          <view class="qr" v-for="(q, i) in quickPrompts" :key="i" @click="usePrompt(q)">{{ q }}</view>
        </view>
      </view>
      <view class="msg-row" :class="m.role" v-for="(m, i) in messages" :key="i" :id="'m-' + i">
        <view class="msg-avatar" :class="m.role">{{ m.role === 'user' ? '我' : 'AI' }}</view>
        <view class="msg-bubble">{{ m.content }}</view>
      </view>
      <view style="height: 20rpx;" />
    </scroll-view>
    <view class="input-bar">
      <input v-model="input" class="input-box" placeholder="发消息给 AI..." confirm-type="send" @confirm="send" />
      <view class="send-btn" :class="{ disabled: !input || sending }" @click="send"><text>➤</text></view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { testChat } from '@/api/ai'

const messages = ref<{ role: 'user' | 'assistant'; content: string }[]>([])
const input = ref('')
const sending = ref(false)
const anchorId = ref('')
const quickPrompts = ['写一段商品描述', '帮我还价 80', '生成发货提醒', '优化我的标题']

async function send() {
  const text = input.value.trim()
  if (!text || sending.value) return
  input.value = ''
  messages.value.push({ role: 'user', content: text })
  await scrollToBottom()
  sending.value = true
  try {
    const res: any = await testChat(messages.value)
    const reply = res?.content || res?.reply || (typeof res === 'string' ? res : '（AI 暂未返回内容）')
    messages.value.push({ role: 'assistant', content: reply })
    await scrollToBottom()
  } catch (e: any) {
    uni.showToast({ title: e?.message || 'AI 调用失败', icon: 'none' })
  } finally { sending.value = false }
}

async function scrollToBottom() {
  await nextTick()
  if (messages.value.length > 0) anchorId.value = 'm-' + (messages.value.length - 1)
}

function usePrompt(p: string) { input.value = p; send() }
function clearChat() {
  uni.showModal({ title: '提示', content: '清空当前对话？', success: r => { if (r.confirm) messages.value = [] } })
}
function goBack() { uni.navigateBack() }
</script>

<style scoped lang="scss">
.page-ai-chat { min-height: 100vh; background: #f5f5f7; display: flex; flex-direction: column; }
.nav-bar { display: flex; align-items: center; justify-content: space-between; height: 88rpx; padding: 0 24rpx; background: rgba(255,255,255,.92); border-bottom: 1rpx solid #e5e7eb; flex-shrink: 0; }
.nav-back { display: flex; align-items: center; gap: 8rpx; font-size: 28rpx; color: #4f46e5; font-weight: 600; }
.nav-arrow { font-size: 36rpx; font-weight: 300; }
.nav-title { font-size: 32rpx; font-weight: 700; color: #111827; }
.nav-action { font-size: 26rpx; color: #6b7280; }
.chat-area { flex: 1; padding: 20rpx 24rpx; }
.empty-hint { display: flex; flex-direction: column; align-items: center; padding: 100rpx 0; gap: 20rpx; font-size: 24rpx; color: #9ca3af; }
.empty-emoji { font-size: 80rpx; }
.quick-row { display: flex; flex-wrap: wrap; justify-content: center; gap: 12rpx; margin-top: 20rpx; }
.qr { padding: 12rpx 24rpx; border-radius: 24rpx; background: rgba(79,70,229,.08); color: #4f46e5; font-size: 22rpx; }
.msg-row { display: flex; align-items: flex-end; gap: 12rpx; margin-bottom: 20rpx; }
.msg-row.user { flex-direction: row-reverse; }
.msg-avatar { width: 64rpx; height: 64rpx; border-radius: 16rpx; display: flex; align-items: center; justify-content: center; font-size: 22rpx; color: #fff; flex-shrink: 0; font-weight: 600; }
.msg-avatar.user { background: linear-gradient(135deg,#4f46e5,#7c3aed); }
.msg-avatar.assistant { background: linear-gradient(135deg,#06b6d4,#22d3ee); }
.msg-bubble { max-width: 76%; padding: 18rpx 22rpx; font-size: 28rpx; line-height: 1.55; color: #111827; border-radius: 28rpx; }
.msg-row.assistant .msg-bubble { background: #fff; border: 1rpx solid #f0f0f0; border-bottom-left-radius: 8rpx; }
.msg-row.user .msg-bubble { background: linear-gradient(135deg,#4f46e5,#7c3aed); color: #fff; border-bottom-right-radius: 8rpx; }
.input-bar { background: #fff; border-top: 1rpx solid #e5e7eb; padding: 16rpx 24rpx 32rpx; display: flex; align-items: center; gap: 16rpx; flex-shrink: 0; }
.input-box { flex: 1; height: 72rpx; border: 1rpx solid #e8e8e8; border-radius: 36rpx; padding: 0 28rpx; font-size: 28rpx; background: #fafafa; }
.send-btn { width: 72rpx; height: 72rpx; border-radius: 50%; background: linear-gradient(135deg,#4f46e5,#7c3aed); color: #fff; font-size: 32rpx; display: flex; align-items: center; justify-content: center; flex-shrink: 0; box-shadow: 0 4rpx 12rpx rgba(79,70,229,.3); }
.send-btn.disabled { opacity: .5; }
</style>
