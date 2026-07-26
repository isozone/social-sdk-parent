<template>
  <view class="page-edit">
    <view class="nav-bar">
      <view class="nav-back" @click="goBack"><text class="nav-arrow">‹</text><text>返回</text></view>
      <text class="nav-title">{{ isEdit ? '编辑规则' : '新建规则' }}</text>
      <text class="nav-action" v-if="isEdit" @click="confirmDelete">删除</text>
    </view>

    <scroll-view scroll-y class="form-area">
      <view class="section">
        <view class="section-label">关键词 <text class="req">*</text></view>
        <input v-model="form.keyword" class="field-input" placeholder="触发自动回复的关键词" />
      </view>

      <view class="section">
        <view class="section-label">回复内容 <text class="req">*</text></view>
        <textarea v-model="form.replyText" class="field-textarea" placeholder="买家消息含关键词时自动回复的内容" maxlength="500" />
        <view class="field-count">{{ form.replyText.length }} / 500</view>
      </view>

      <view class="section">
        <view class="section-label">匹配方式</view>
        <view class="seg">
          <view class="seg-btn" :class="{ active: form.matchType === 'CONTAINS' }" @click="form.matchType = 'CONTAINS'">包含</view>
          <view class="seg-btn" :class="{ active: form.matchType === 'EQUALS' }" @click="form.matchType = 'EQUALS'">完全相同</view>
          <view class="seg-btn" :class="{ active: form.matchType === 'REGEX' }" @click="form.matchType = 'REGEX'">正则</view>
        </view>
      </view>

      <view class="section">
        <view class="section-label">优先级 <text class="label-sub">数字越大越先匹配</text></view>
        <input v-model="form.priority" class="field-input" type="number" placeholder="0" />
      </view>

      <view class="section">
        <view class="section-label">适用账号</view>
        <view class="account-pick" @click="pickAccount">
          <text class="ap-name">{{ form.accountName || '全部账号' }}</text>
          <text class="ap-arrow">›</text>
        </view>
      </view>

      <view class="section">
        <view class="test-row">
          <input v-model="testText" class="test-input" placeholder="输入测试文本，验证规则是否命中" />
          <view class="test-btn" @click="runTest">测试</view>
        </view>
        <view class="test-result" v-if="testResult">
          <text class="tr-label">{{ testResult.matched ? '✓ 命中' : '✗ 未命中' }}</text>
          <text class="tr-reply" v-if="testResult.replyText">{{ testResult.replyText }}</text>
        </view>
      </view>

      <view style="height: 40rpx;" />
    </scroll-view>

    <view class="bottom-actions">
      <view class="btn-primary" :class="{ disabled: !canSave }" @click="save">{{ isEdit ? '保存修改' : '创建规则' }}</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getRule, createRule, updateRule, deleteRule, testRule } from '@/api/rules'
import type { TestRuleMatchResult } from '@/types/rule'

const isEdit = ref(false)
const ruleId = ref<number | null>(null)
const form = ref({
  keyword: '',
  replyText: '',
  matchType: 'CONTAINS' as 'CONTAINS' | 'EQUALS' | 'REGEX',
  priority: '0',
  accountId: 0 as number,
  accountName: '',
  enabled: true,
})
const testText = ref('')
const testResult = ref<TestRuleMatchResult | null>(null)

const canSave = computed(() => form.value.keyword && form.value.replyText)

onMounted(async () => {
  const pages = getCurrentPages() as any[]
  const cur = pages[pages.length - 1]?.options || {}
  if (cur.id) {
    isEdit.value = true
    ruleId.value = Number(cur.id)
    await load()
  }
})

async function load() {
  try {
    const r = await getRule(ruleId.value!)
    Object.assign(form.value, {
      keyword: r.keyword, replyText: r.replyText, matchType: r.matchType,
      priority: String(r.priority), accountId: r.accountId, enabled: r.enabled,
    })
    form.value.accountName = (r as any).accountName || ''
  } catch (e: any) {
    uni.showToast({ title: e?.message || '加载失败', icon: 'none' })
  }
}

function pickAccount() {
  uni.showToast({ title: '账号选择器待补', icon: 'none' })
}

async function runTest() {
  if (!testText.value) return
  if (!isEdit.value) {
    uni.showToast({ title: '请先创建规则再测试', icon: 'none' })
    return
  }
  try {
    testResult.value = await testRule(ruleId.value!, { text: testText.value })
  } catch (e: any) {
    uni.showToast({ title: e?.message || '测试失败', icon: 'none' })
  }
}

async function save() {
  if (!canSave.value) {
    uni.showToast({ title: '请补全关键词和回复内容', icon: 'none' })
    return
  }
  uni.showLoading({ title: '保存中...' })
  try {
    const payload = {
      keyword: form.value.keyword,
      replyText: form.value.replyText,
      matchType: form.value.matchType,
      priority: Number(form.value.priority) || 0,
      accountId: form.value.accountId || 0,
      enabled: form.value.enabled,
    }
    if (isEdit.value) {
      await updateRule(ruleId.value!, payload)
    } else {
      await createRule(payload)
    }
    uni.showToast({ title: '保存成功', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 1000)
  } catch (e: any) {
    uni.showToast({ title: e?.message || '保存失败', icon: 'none' })
  } finally { uni.hideLoading() }
}

async function confirmDelete() {
  uni.showModal({
    title: '确认删除', content: '该规则将无法恢复', success: async r => {
      if (!r.confirm) return
      try {
        await deleteRule(ruleId.value!)
        uni.showToast({ title: '已删除', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 1000)
      } catch (e: any) { uni.showToast({ title: e?.message || '删除失败', icon: 'none' }) }
    }
  })
}

function goBack() { uni.navigateBack() }
</script>

<style scoped lang="scss">
.page-edit { min-height: 100vh; background: #f5f5f7; display: flex; flex-direction: column; }
.nav-bar { display: flex; align-items: center; justify-content: space-between; height: 88rpx; padding: 0 24rpx; background: rgba(255,255,255,.92); backdrop-filter: blur(20rpx); border-bottom: 1rpx solid #e5e7eb; flex-shrink: 0; }
.nav-back { display: flex; align-items: center; gap: 8rpx; font-size: 28rpx; color: #4f46e5; font-weight: 600; }
.nav-arrow { font-size: 36rpx; font-weight: 300; }
.nav-title { font-size: 32rpx; font-weight: 700; color: #111827; }
.nav-action { font-size: 26rpx; color: #ef4444; }

.form-area { flex: 1; padding: 20rpx 24rpx; }
.section { background: #fff; border-radius: 24rpx; padding: 28rpx 24rpx; margin-bottom: 20rpx; box-shadow: 0 2rpx 12rpx rgba(0,0,0,.03); }
.section-label { font-size: 26rpx; font-weight: 600; color: #374151; margin-bottom: 16rpx; }
.req { color: #ef4444; }
.label-sub { font-size: 20rpx; color: #9ca3af; font-weight: 400; margin-left: 12rpx; }

.field-input { width: 100%; height: 80rpx; border: 1rpx solid #e5e7eb; border-radius: 16rpx; padding: 0 20rpx; font-size: 28rpx; background: #fafafa; }
.field-textarea { width: 100%; min-height: 200rpx; border: 1rpx solid #e5e7eb; border-radius: 16rpx; padding: 16rpx 20rpx; font-size: 28rpx; background: #fafafa; }
.field-count { text-align: right; font-size: 20rpx; color: #9ca3af; margin-top: 8rpx; }

.seg { display: flex; gap: 8rpx; }
.seg-btn { flex: 1; height: 72rpx; line-height: 72rpx; text-align: center; font-size: 24rpx; color: #6b7280; background: #f3f4f6; border-radius: 16rpx; }
.seg-btn.active { background: linear-gradient(135deg, #4f46e5, #7c3aed); color: #fff; font-weight: 600; }

.account-pick { display: flex; align-items: center; justify-content: space-between; padding: 20rpx; background: #fafafa; border: 1rpx solid #e5e7eb; border-radius: 16rpx; }
.ap-name { font-size: 28rpx; color: #111827; }
.ap-arrow { font-size: 32rpx; color: #9ca3af; }

.test-row { display: flex; gap: 16rpx; }
.test-input { flex: 1; height: 72rpx; border: 1rpx solid #e5e7eb; border-radius: 16rpx; padding: 0 20rpx; font-size: 26rpx; background: #fafafa; }
.test-btn { padding: 0 32rpx; line-height: 72rpx; border-radius: 16rpx; background: linear-gradient(135deg, #4f46e5, #7c3aed); color: #fff; font-size: 26rpx; font-weight: 600; }
.test-result { margin-top: 16rpx; padding: 20rpx; background: rgba(79,70,229,.06); border-radius: 16rpx; }
.tr-label { font-size: 26rpx; font-weight: 600; color: #4f46e5; display: block; }
.tr-reply { font-size: 26rpx; color: #4b5563; margin-top: 8rpx; display: block; }

.bottom-actions { padding: 20rpx 24rpx 40rpx; background: #fff; border-top: 1rpx solid #e5e7eb; flex-shrink: 0; }
.btn-primary { height: 88rpx; line-height: 88rpx; text-align: center; background: linear-gradient(135deg, #4f46e5, #7c3aed); color: #fff; border-radius: 44rpx; font-size: 30rpx; font-weight: 600; box-shadow: 0 6rpx 16rpx rgba(79,70,229,.3); }
.btn-primary.disabled { opacity: .5; }
</style>
