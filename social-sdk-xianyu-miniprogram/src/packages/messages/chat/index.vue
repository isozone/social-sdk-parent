<template>
  <view class="page-chat">
    <view class="nav-bar">
      <view class="nav-back" @click="goBack"><text class="nav-arrow">‹</text><text>返回</text></view>
      <view class="nav-info">
        <text class="nav-name">{{ userName || '闲鱼用户' }}</text>
        <text class="nav-sub"><text class="online-dot" />在线 · 闲鱼买家</text>
      </view>
      <view class="nav-actions"><text class="nav-more" @click="showMenu">⋮</text></view>
    </view>

    <scroll-view scroll-y class="msg-area" :scroll-into-view="anchorId" :scroll-with-animation="true">
      <view v-if="loading" class="loading-hint">加载中...</view>
      <view v-if="!loading && messages.length === 0" class="empty-hint">暂无消息，发送第一条消息开始对话</view>
      <view class="msg-row" :class="m.direction === 'outgoing' ? 'me' : 'other'" v-for="(m, idx) in messages" :key="m.id || idx" :id="'msg-' + (m.id || idx)">
        <view class="msg-avatar" :style="{ background: m.direction === 'outgoing' ? 'linear-gradient(135deg,#4f46e5,#7c3aed)' : 'linear-gradient(135deg,#06b6d4,#22d3ee)' }">
          {{ m.direction === 'outgoing' ? '我' : (userName || '买')[0] }}
        </view>
        <view class="msg-bubble">
          <text v-if="m.msgType === 'TEXT'" class="msg-text">{{ m.content }}</text>
          <image v-else-if="m.msgType === 'IMAGE'" :src="m.content" mode="widthFix" class="msg-image" @click="previewImage(m.content)" />
          <view v-if="m.autoReplied" class="ai-tag">AI 自动回复</view>
        </view>
      </view>
      <view style="height: 20rpx;" />
    </scroll-view>

    <view class="quick-replies" v-if="quickReplies.length">
      <view class="qr-btn" v-for="(q, i) in quickReplies" :key="i" @click="useQuick(q)">{{ q }}</view>
    </view>

    <view class="input-bar">
      <view class="action-icon-btn" @click="chooseImage"><text>＋</text></view>
      <input v-model="inputText" class="input-box" placeholder="发条消息..." confirm-type="send" @confirm="sendText" />
      <view class="send-btn" @click="sendText"><text>➤</text></view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import api from '@/api'
import { getSessionHistory, sendMessage } from '@/api/messages'

const accountId = ref<number>(0)
const sessionId = ref<string>('')
const userName = ref<string>('')
const messages = ref<any[]>([])
const loading = ref(false)
const inputText = ref('')
const anchorId = ref('')
const quickReplies = ref<string[]>(['在的亲~', '当天发货哦', '包邮的', '价格就是标价'])

onMounted(async () => {
  const pages = getCurrentPages() as any[]
  const cur = pages[pages.length - 1]?.options || {}
  accountId.value = Number(cur.accountId || 0)
  sessionId.value = String(cur.sessionId || '')
  userName.value = decodeURIComponent(cur.userName || '')
  await loadHistory()
})

async function loadHistory() {
  if (!accountId.value || !sessionId.value) return
  loading.value = true
  try {
    const res = await getSessionHistory(accountId.value, sessionId.value)
    messages.value = Array.isArray(res) ? res : (res?.records || [])
    await scrollToBottom()
  } catch (e: any) {
    uni.showToast({ title: e?.message || '加载失败', icon: 'none' })
  } finally { loading.value = false }
}

async function scrollToBottom() {
  await nextTick()
  if (messages.value.length > 0) {
    const last = messages.value[messages.value.length - 1]
    anchorId.value = 'msg-' + (last.id || messages.value.length - 1)
  }
}

async function sendText() {
  const text = inputText.value.trim()
  if (!text) return
  inputText.value = ''
  try {
    await sendMessage({ accountId: accountId.value, sessionId: sessionId.value, content: text, msgType: 'TEXT' })
    messages.value.push({ id: 'local-' + Date.now(), sessionId: sessionId.value, accountId: accountId.value, content: text, direction: 'outgoing', msgType: 'TEXT', autoReplied: false, createdAt: new Date().toISOString() })
    await scrollToBottom()
  } catch (e: any) {
    uni.showToast({ title: e?.message || '发送失败', icon: 'none' })
  }
}

function useQuick(text: string) {
  inputText.value = text
  sendText()
}

function chooseImage() {
  uni.chooseImage({
    count: 1,
    success: async res => {
      const fileUrl = res.tempFilePaths[0]
      uni.showLoading({ title: '上传中...' })
      try {
        const { uploadImage } = await import('@/api/products')
        const r = await uploadImage(fileUrl)
        await sendMessage({ accountId: accountId.value, sessionId: sessionId.value, content: r.url, msgType: 'IMAGE' })
        messages.value.push({ id: 'local-img-' + Date.now(), sessionId: sessionId.value, accountId: accountId.value, content: r.url, direction: 'outgoing', msgType: 'IMAGE', autoReplied: false, createdAt: new Date().toISOString() })
        await scrollToBottom()
      } catch (e: any) {
        uni.showToast({ title: e?.message || '上传失败', icon: 'none' })
      } finally { uni.hideLoading() }
    }
  })
}

function previewImage(url: string) {
  uni.previewImage({ urls: [url], current: url })
}

function goBack() { uni.navigateBack() }

function showMenu() {
  uni.showActionSheet({ itemList: ['同步消息', '清空当前会话', '标记已读'], success: async res => {
    if (res.tapIndex === 0) {
      try { await api.post('/api/mini/messages/sync', { accountId: accountId.value }); uni.showToast({ title: '同步成功', icon: 'success' }); await loadHistory() } catch {}
    } else if (res.tapIndex === 1) {
      uni.showModal({ title: '提示', content: '确定清空本地显示？', success: () => { messages.value = [] } })
    }
  }})
}
</script>

<style scoped lang="scss">
.page-chat { min-height: 100vh; background: #f5f5f7; display: flex; flex-direction: column; }
.nav-bar { display: flex; align-items: center; justify-content: space-between; height: 88rpx; padding: 0 24rpx; background: rgba(255,255,255,.92); backdrop-filter: blur(20rpx); border-bottom: 1rpx solid #e5e7eb; flex-shrink: 0; }
.nav-back { display: flex; align-items: center; gap: 8rpx; font-size: 28rpx; color: #4f46e5; font-weight: 600; }
.nav-arrow { font-size: 36rpx; font-weight: 300; }
.nav-info { flex: 1; text-align: center; }
.nav-name { font-size: 30rpx; font-weight: 600; color: #111827; display: block; }
.nav-sub { font-size: 20rpx; color: #9ca3af; display: flex; align-items: center; justify-content: center; gap: 6rpx; margin-top: 2rpx; }
.online-dot { width: 10rpx; height: 10rpx; border-radius: 50%; background: #10b981; }
.nav-more { font-size: 36rpx; color: #6b7280; }

.msg-area { flex: 1; padding: 20rpx 24rpx; }
.loading-hint, .empty-hint { text-align: center; font-size: 22rpx; color: #9ca3af; padding: 40rpx 0; }
.msg-row { display: flex; align-items: flex-end; gap: 12rpx; margin-bottom: 20rpx; }
.msg-row.me { flex-direction: row-reverse; }
.msg-avatar { width: 64rpx; height: 64rpx; border-radius: 16rpx; display: flex; align-items: center; justify-content: center; font-size: 22rpx; color: #fff; flex-shrink: 0; box-shadow: 0 2rpx 6rpx rgba(0,0,0,.08); }
.msg-bubble { max-width: 68%; padding: 18rpx 22rpx; font-size: 28rpx; line-height: 1.55; color: #111827; border-radius: 28rpx; }
.msg-row.other .msg-bubble { background: #fff; border: 1rpx solid #f0f0f0; border-bottom-left-radius: 8rpx; box-shadow: 0 1rpx 3rpx rgba(0,0,0,.04); }
.msg-row.me .msg-bubble { background: linear-gradient(135deg, #4f46e5, #7c3aed); color: #fff; border-bottom-right-radius: 8rpx; box-shadow: 0 2rpx 8rpx rgba(79,70,229,.25); }
.msg-text { display: block; white-space: pre-wrap; word-break: break-all; }
.msg-image { width: 320rpx; border-radius: 18rpx; display: block; }
.ai-tag { display: inline-flex; align-items: center; gap: 6rpx; font-size: 18rpx; background: rgba(79,70,229,.1); color: #4f46e5; padding: 4rpx 12rpx; border-radius: 8rpx; margin-top: 10rpx; font-weight: 600; }
.ai-tag::before { content: ''; width: 8rpx; height: 8rpx; border-radius: 50%; background: #7c3aed; }

.quick-replies { background: #fff; border-top: 1rpx solid #e5e7eb; padding: 16rpx 24rpx; overflow-x: auto; display: flex; gap: 12rpx; flex-shrink: 0; }
.quick-replies::-webkit-scrollbar { display: none; }
.qr-btn { flex-shrink: 0; padding: 10rpx 24rpx; border-radius: 24rpx; font-size: 22rpx; font-weight: 500; background: rgba(79,70,229,.06); color: #4f46e5; border: 1rpx solid rgba(79,70,229,.12); }
.qr-btn:active { background: rgba(79,70,229,.12); transform: scale(.96); }

.input-bar { background: #fff; border-top: 1rpx solid #e5e7eb; padding: 16rpx 24rpx 32rpx; display: flex; align-items: center; gap: 16rpx; flex-shrink: 0; }
.action-icon-btn { width: 72rpx; height: 72rpx; border-radius: 50%; background: #f3f4f6; display: flex; align-items: center; justify-content: center; font-size: 32rpx; flex-shrink: 0; }
.action-icon-btn:active { background: #e5e7eb; }
.input-box { flex: 1; height: 72rpx; border: 1rpx solid #e8e8e8; border-radius: 36rpx; padding: 0 28rpx; font-size: 28rpx; color: #111827; background: #fafafa; }
.send-btn { width: 72rpx; height: 72rpx; border-radius: 50%; background: linear-gradient(135deg, #4f46e5, #7c3aed); border: none; color: #fff; font-size: 32rpx; box-shadow: 0 4rpx 12rpx rgba(79,70,229,.3); display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.send-btn:active { transform: scale(.92); }
</style>
