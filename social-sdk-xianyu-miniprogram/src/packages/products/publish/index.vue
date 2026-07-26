<template>
  <view class="page-publish">
    <view class="nav-bar">
      <view class="nav-back" @click="goBack"><text class="nav-arrow">‹</text><text>返回</text></view>
      <text class="nav-title">发新商品</text>
      <text class="nav-action" @click="saveDraft">存草稿</text>
    </view>

    <scroll-view scroll-y class="form-area">
      <!-- 图片 -->
      <view class="section">
        <view class="section-label">商品图片 <text class="label-sub">最多 9 张，首张为主图</text></view>
        <view class="image-grid">
          <view class="img-cell" v-for="(img, i) in form.images" :key="i">
            <image :src="img" mode="aspectFill" class="img-preview" @click="previewImg(i)" />
            <view class="img-del" @click="removeImg(i)">×</view>
            <view v-if="i === 0" class="img-main-tag">主图</view>
          </view>
          <view class="img-add" v-if="form.images.length < 9" @click="chooseImage">
            <text class="add-plus">＋</text>
            <text class="add-text">添加图片</text>
          </view>
        </view>
      </view>

      <!-- 标题 -->
      <view class="section">
        <view class="section-label">商品标题</view>
        <input v-model="form.title" class="field-input" placeholder="30 字以内，吸引买家的标题" maxlength="30" />
        <view class="field-count">{{ form.title.length }} / 30</view>
      </view>

      <!-- 价格 + 库存 -->
      <view class="section">
        <view class="field-row">
          <view class="field-col">
            <view class="section-label">价格 ¥</view>
            <input v-model="form.price" class="field-input" type="digit" placeholder="0.00" />
          </view>
          <view class="field-col">
            <view class="section-label">数量</view>
            <input v-model="form.stock" class="field-input" type="number" placeholder="1" />
          </view>
        </view>
      </view>

      <!-- 分类 -->
      <view class="section">
        <view class="section-label">分类</view>
        <view class="category-row">
          <view class="category-chip" :class="{ selected: form.categoryId === c.id }" v-for="c in topCategories" :key="c.id" @click="pickCategory(c)">{{ c.name }}</view>
          <view class="category-chip" @click="showMoreCategory">更多 ›</view>
        </view>
        <view class="category-path" v-if="form.categoryPath">{{ form.categoryPath }}</view>
      </view>

      <!-- 成色 -->
      <view class="section">
        <view class="field-row">
          <view class="field-label">成色</view>
          <picker :range="conditionOptions" :value="conditionIndex" @change="onConditionChange" class="field-picker">
            <view class="picker-display">{{ conditionOptions[conditionIndex] }} ▾</view>
          </picker>
        </view>
      </view>

      <!-- 描述 -->
      <view class="section">
        <view class="section-label">描述</view>
        <textarea v-model="form.description" class="field-textarea" placeholder="详细描述商品的使用情况、配件、售后等..." maxlength="500" />
        <view class="field-count">{{ form.description.length }} / 500</view>
      </view>

      <!-- 标签 -->
      <view class="section">
        <view class="section-label">热门标签</view>
        <view class="tag-row">
          <view class="tag-chip" :class="{ selected: t.selected }" v-for="(t, i) in tags" :key="i" @click="toggleTag(i)">#{{ t.label }}</view>
        </view>
      </view>

      <!-- AI 优化 -->
      <view class="section">
        <view class="ai-suggest">
          <view class="ai-badge">AI</view>
          <view class="ai-text">用 AI 智能优化标题和描述，提升曝光率</view>
          <view class="ai-use-btn" :class="{ disabled: !form.title }" @click="aiPolish">立即优化</view>
        </view>
      </view>

      <view style="height: 40rpx;" />
    </scroll-view>

    <view class="bottom-actions">
      <view class="btn-outline" @click="saveDraft">保存草稿</view>
      <view class="btn-primary" :class="{ disabled: !canPublish }" @click="publish">发布商品</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { createProduct, uploadImage, getCategoryTree } from '@/api/products'
import { useAccountStore } from '@/store/modules/account'
import type { CategoryNode } from '@/types/product'

const accountStore = useAccountStore()

const form = ref({
  title: '',
  price: '',
  stock: '1',
  categoryId: '' as string,
  categoryPath: '' as string,
  description: '',
  images: [] as string[],
})

const topCategories = ref<CategoryNode[]>([])
const conditionOptions = ['全新', '9 成新', '8 成新', '7 成新', '6 成新及以下']
const conditionIndex = ref(0)
const tags = ref([
  { label: '包邮', selected: true },
  { label: '当天发货', selected: false },
  { label: '正品保障', selected: false },
  { label: '全新', selected: false },
  { label: '可小刀', selected: false },
  { label: '急出', selected: false },
])

const canPublish = computed(() => form.value.title && form.value.price && form.value.images.length > 0)

onMounted(async () => {
  await loadCategories()
})

async function loadCategories() {
  try {
    const res = await getCategoryTree()
    const list = Array.isArray(res) ? res : (res?.records || [])
    topCategories.value = list.slice(0, 6)
  } catch {}
}

function pickCategory(c: CategoryNode) {
  form.value.categoryId = c.id
  form.value.categoryPath = c.name
}

function showMoreCategory() {
  uni.showActionSheet({
    itemList: topCategories.value.map(c => c.name),
    success: res => {
      const c = topCategories.value[res.tapIndex]
      if (c) pickCategory(c)
    }
  })
}

function onConditionChange(e: any) {
  conditionIndex.value = e.detail.value
}

function toggleTag(i: number) {
  tags.value[i].selected = !tags.value[i].selected
}

function chooseImage() {
  uni.chooseImage({
    count: 9 - form.value.images.length,
    success: async res => {
      uni.showLoading({ title: '上传中...' })
      const newUrls: string[] = []
      for (const fileUrl of res.tempFilePaths) {
        try {
          const r = await uploadImage(fileUrl)
          newUrls.push(r.url)
        } catch (e: any) {
          uni.showToast({ title: e?.message || '图片上传失败', icon: 'none' })
        }
      }
      form.value.images.push(...newUrls)
      uni.hideLoading()
    }
  })
}

function removeImg(i: number) {
  form.value.images.splice(i, 1)
}

function previewImg(i: number) {
  uni.previewImage({ urls: form.value.images, current: form.value.images[i] })
}

async function aiPolish() {
  if (!form.value.title) {
    uni.showToast({ title: '请先填标题', icon: 'none' })
    return
  }
  uni.showLoading({ title: 'AI 优化中...' })
  try {
    // 标题润色：拼上已选标签
    const selectedTags = tags.value.filter(t => t.selected).map(t => t.label).join(' ')
    if (selectedTags && !form.value.title.includes('#')) {
      form.value.title = form.value.title + ' #' + tags.value.filter(t => t.selected).map(t => t.label).join(' #')
    }
    uni.showToast({ title: '已优化', icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: e?.message || '优化失败', icon: 'none' })
  } finally { uni.hideLoading() }
}

function buildPayload(draft = false) {
  const tagText = tags.value.filter(t => t.selected).map(t => '#' + t.label).join(' ')
  const desc = form.value.description + (tagText ? '\n' + tagText : '')
  return {
    accountId: accountStore.current?.id || 0,
    title: form.value.title,
    description: desc,
    price: Number(form.value.price) || 0,
    stock: Number(form.value.stock) || 1,
    categoryId: form.value.categoryId || undefined,
    images: form.value.images,
    status: draft ? 'DRAFT' : 'ON_SALE',
    condition: conditionOptions[conditionIndex.value],
  }
}

function saveDraft() {
  try {
    uni.setStorageSync('mini_publish_draft', JSON.stringify(buildPayload(true)))
    uni.showToast({ title: '草稿已保存', icon: 'success' })
  } catch {
    uni.showToast({ title: '保存失败', icon: 'none' })
  }
}

async function publish() {
  if (!canPublish.value) {
    uni.showToast({ title: '请补全标题、价格和首图', icon: 'none' })
    return
  }
  uni.showLoading({ title: '发布中...' })
  try {
    await createProduct(buildPayload(false))
    uni.removeStorageSync('mini_publish_draft')
    uni.showToast({ title: '发布成功', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 1200)
  } catch (e: any) {
    uni.showToast({ title: e?.message || '发布失败', icon: 'none' })
  } finally { uni.hideLoading() }
}

function goBack() {
  if (form.value.title || form.value.images.length > 0) {
    uni.showModal({
      title: '提示',
      content: '尚未保存的内容会丢失，确认离开？',
      success: r => { if (r.confirm) uni.navigateBack() }
    })
  } else {
    uni.navigateBack()
  }
}
</script>

<style scoped lang="scss">
.page-publish { min-height: 100vh; background: #f5f5f7; display: flex; flex-direction: column; }
.nav-bar { display: flex; align-items: center; justify-content: space-between; height: 88rpx; padding: 0 24rpx; background: rgba(255,255,255,.92); backdrop-filter: blur(20rpx); border-bottom: 1rpx solid #e5e7eb; flex-shrink: 0; }
.nav-back { display: flex; align-items: center; gap: 8rpx; font-size: 28rpx; color: #4f46e5; font-weight: 600; }
.nav-arrow { font-size: 36rpx; font-weight: 300; }
.nav-title { font-size: 32rpx; font-weight: 700; color: #111827; }
.nav-action { font-size: 26rpx; color: #6b7280; }

.form-area { flex: 1; padding: 20rpx 24rpx; }
.section { background: #fff; border-radius: 24rpx; padding: 28rpx 24rpx; margin-bottom: 20rpx; box-shadow: 0 2rpx 12rpx rgba(0,0,0,.03); }
.section-label { font-size: 26rpx; font-weight: 600; color: #374151; margin-bottom: 16rpx; display: block; }
.label-sub { font-size: 20rpx; color: #9ca3af; font-weight: 400; margin-left: 12rpx; }

.image-grid { display: flex; flex-wrap: wrap; gap: 16rpx; }
.img-cell { position: relative; width: 200rpx; height: 200rpx; border-radius: 16rpx; overflow: hidden; }
.img-preview { width: 100%; height: 100%; }
.img-del { position: absolute; top: 4rpx; right: 4rpx; width: 36rpx; height: 36rpx; border-radius: 50%; background: rgba(0,0,0,.5); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 24rpx; }
.img-main-tag { position: absolute; bottom: 0; left: 0; right: 0; background: rgba(79,70,229,.85); color: #fff; font-size: 18rpx; text-align: center; padding: 2rpx 0; }
.img-add { width: 200rpx; height: 200rpx; border: 2rpx dashed #d1d5db; border-radius: 16rpx; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8rpx; background: #fafafa; }
.add-plus { font-size: 56rpx; color: #9ca3af; font-weight: 300; }
.add-text { font-size: 22rpx; color: #9ca3af; }

.field-input { width: 100%; height: 80rpx; border: 1rpx solid #e5e7eb; border-radius: 16rpx; padding: 0 20rpx; font-size: 28rpx; color: #111827; background: #fafafa; }
.field-textarea { width: 100%; min-height: 200rpx; border: 1rpx solid #e5e7eb; border-radius: 16rpx; padding: 16rpx 20rpx; font-size: 28rpx; color: #111827; background: #fafafa; }
.field-count { text-align: right; font-size: 20rpx; color: #9ca3af; margin-top: 8rpx; }
.field-row { display: flex; gap: 20rpx; }
.field-col { flex: 1; }
.field-label { font-size: 26rpx; font-weight: 600; color: #374151; margin-bottom: 12rpx; }
.field-picker { flex: 1; }
.picker-display { height: 80rpx; line-height: 80rpx; border: 1rpx solid #e5e7eb; border-radius: 16rpx; padding: 0 20rpx; font-size: 28rpx; color: #111827; background: #fafafa; }

.category-row { display: flex; flex-wrap: wrap; gap: 12rpx; }
.category-chip { padding: 12rpx 28rpx; border-radius: 32rpx; font-size: 24rpx; background: #f3f4f6; color: #6b7280; border: 1rpx solid transparent; }
.category-chip.selected { background: rgba(79,70,229,.1); color: #4f46e5; border-color: rgba(79,70,229,.3); font-weight: 600; }
.category-path { font-size: 22rpx; color: #9ca3af; margin-top: 12rpx; }

.tag-row { display: flex; flex-wrap: wrap; gap: 12rpx; }
.tag-chip { padding: 12rpx 28rpx; border-radius: 32rpx; font-size: 24rpx; background: #f3f4f6; color: #6b7280; }
.tag-chip.selected { background: linear-gradient(135deg, #4f46e5, #7c3aed); color: #fff; }

.ai-suggest { display: flex; align-items: center; gap: 16rpx; padding: 20rpx; background: linear-gradient(135deg, rgba(79,70,229,.06), rgba(124,58,237,.06)); border-radius: 20rpx; }
.ai-badge { width: 56rpx; height: 56rpx; border-radius: 16rpx; background: linear-gradient(135deg, #4f46e5, #7c3aed); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 22rpx; font-weight: 700; flex-shrink: 0; }
.ai-text { flex: 1; font-size: 24rpx; color: #4b5563; }
.ai-use-btn { padding: 14rpx 28rpx; border-radius: 32rpx; background: linear-gradient(135deg, #4f46e5, #7c3aed); color: #fff; font-size: 24rpx; font-weight: 600; }
.ai-use-btn.disabled { opacity: .5; }

.bottom-actions { display: flex; gap: 20rpx; padding: 20rpx 24rpx 40rpx; background: #fff; border-top: 1rpx solid #e5e7eb; flex-shrink: 0; }
.btn-outline { flex: 1; height: 88rpx; line-height: 88rpx; text-align: center; border: 2rpx solid #4f46e5; color: #4f46e5; border-radius: 44rpx; font-size: 30rpx; font-weight: 600; background: #fff; }
.btn-primary { flex: 2; height: 88rpx; line-height: 88rpx; text-align: center; background: linear-gradient(135deg, #4f46e5, #7c3aed); color: #fff; border-radius: 44rpx; font-size: 30rpx; font-weight: 600; box-shadow: 0 6rpx 16rpx rgba(79,70,229,.3); }
.btn-primary.disabled { opacity: .5; }
</style>
