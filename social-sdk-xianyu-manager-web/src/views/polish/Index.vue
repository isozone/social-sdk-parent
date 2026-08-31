<template>
  <div class="page-root">
    <el-card shadow="never">
      <!-- 卡片头 -->
      <div class="card-head">
        <div class="card-head-left">
          <div class="card-chip chip-amber"><el-icon><Promotion /></el-icon></div>
          <div class="card-head-text">
            <div class="card-title">商品擦亮</div>
            <div class="card-sub">单擦、批量擦亮与超级擦亮，提升商品曝光优先级</div>
          </div>
        </div>
        <el-select
          v-model="accountId" placeholder="选择账号" :loading="accountsLoading" clearable
          @change="onAccountChange"
        >
          <el-option v-for="a in accounts" :key="a.id" :label="a.accountName || a.id" :value="a.id" />
        </el-select>
      </div>

      <!-- 三层 Tab -->
      <el-tabs v-model="activeTab">
        <el-tab-pane label="单擦" name="single">
          <el-form :model="singleForm" inline>
            <el-form-item label="商品">
              <el-select v-model="singleForm.itemId" placeholder="选择商品" filterable
                style="width: 100%" :loading="productLoading" clearable
                :disabled="!singleForm.accountId">
                <el-option v-for="p in productOptions" :key="p.itemId"
                  :label="`${p.title} · ${p.itemId}`" :value="p.itemId" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="loading" @click="doPolish">
                <el-icon><RefreshLeft /></el-icon> 擦亮
              </el-button>
            </el-form-item>
          </el-form>

          <div v-if="singleResult">
            <el-alert :type="singleResult.success ? 'success' : 'warning'" :closable="false" show-icon
              style="margin-top: 14px;">
              {{ singleResult.success ? `擦亮成功${singleItem && singleItem.firstModified ? ' · ' + formatTime(singleResult.polishedAt) : ''}` : '擦亮已提交，请查看详情确认' }}
            </el-alert>

            <div v-if="singleItem" class="item-detail-panel">
              <div class="sub-title">商品信息</div>
              <el-descriptions :column="2" border size="default">
                <el-descriptions-item label="商品标题" :span="2">
                  <span class="polish-item-title">{{ singleItem.title }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="商品ID">{{ singleItem.id || singleResult.itemId }}</el-descriptions-item>
                <el-descriptions-item label="卖家">{{ singleItem.userNick || '—' }}</el-descriptions-item>
                <el-descriptions-item label="价格">
                  <span class="price-now">¥{{ singleItem.price }}</span>
                  <span v-if="singleItem.originalPrice && singleItem.originalPrice !== singleItem.price" class="price-old">
                    (原价 ¥{{ singleItem.originalPrice }})
                  </span>
                </el-descriptions-item>
                <el-descriptions-item label="状态">
                  <el-tag v-if="!singleItem.itemDeleted && singleItem.itemStatus === 0" type="success" size="small">在售</el-tag>
                  <el-tag v-else-if="singleItem.itemDeleted" type="danger" size="small">已删除</el-tag>
                  <el-tag v-else type="info" size="small">状态: {{ singleItem.itemStatus }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="所在城市">{{ singleItem.city || '—' }} / {{ singleItem.province || '—' }}</el-descriptions-item>
                <el-descriptions-item label="地区">{{ singleItem.area || singleItem.divisionId || '—' }}</el-descriptions-item>
                <el-descriptions-item label="浏览量">{{ singleItem.browseCount ?? 0 }}</el-descriptions-item>
                <el-descriptions-item label="收藏数">{{ singleItem.favorNum ?? singleItem.collectNum ?? 0 }}</el-descriptions-item>
                <el-descriptions-item label="评论数">{{ singleItem.commentNum ?? 0 }}</el-descriptions-item>
                <el-descriptions-item label="同城支持">{{ singleItem.locationAware ? '支持同城' : '普通' }}</el-descriptions-item>
                <el-descriptions-item label="取货方式">
                  {{ singleItem.onlyTakeSelf ? '仅自提' : (singleItem.onlyInSameCity ? '同城' : '支持邮寄') }}
                </el-descriptions-item>
                <el-descriptions-item label="首次上架" :span="2">{{ singleItem.firstModified || '—' }}</el-descriptions-item>
                <el-descriptions-item label="下架时间" :span="2">{{ singleItem.outStockTime || '—' }}</el-descriptions-item>
              </el-descriptions>

              <div v-if="singleItem.imageUrls?.length" class="polish-images">
                <div class="section-label">商品图片</div>
                <div class="image-grid">
                  <el-image v-for="(url, idx) in singleItem.imageUrls" :key="idx"
                    :src="url" :preview-src-list="singleItem.imageUrls" :initial-index="idx"
                    fit="cover" class="polish-image" />
                </div>
              </div>
            </div>

            <el-collapse style="margin-top: 16px">
              <el-collapse-item title="原始响应 JSON">
                <pre class="json-block">{{ JSON.stringify(singleResult, null, 2) }}</pre>
              </el-collapse-item>
            </el-collapse>
          </div>
        </el-tab-pane>

        <el-tab-pane label="批量擦" name="batch">
          <el-form :model="batchForm" :inline="true" style="gap: 10px;">
            <el-form-item label="商品">
              <el-select v-model="batchForm.itemIds" placeholder="选择商品（可多选）" multiple filterable
                collapse-tags collapse-tags-tooltip style="width: 100%" :loading="productLoading" clearable
                :disabled="!batchForm.accountId">
                <el-option v-for="p in productOptions" :key="p.itemId"
                  :label="`${p.title} · ${p.itemId}`" :value="p.itemId" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="loading" @click="doBatchPolish">
                <el-icon><Grid /></el-icon> 批量擦亮
              </el-button>
            </el-form-item>
          </el-form>
          <div v-if="batchResult" class="result-json-wrap">
            <pre class="json-block">{{ JSON.stringify(batchResult, null, 2) }}</pre>
          </div>
        </el-tab-pane>

        <el-tab-pane label="超级擦亮" name="super">
          <el-alert type="warning" :closable="false" show-icon
            title="超级擦亮 = 同一商品连续多次 polish，间隔 60s 防风控，顶到搜索前列。耗时较长，请耐心等待。" />
          <el-form :model="superForm" inline style="margin-top: 14px;">
            <el-form-item label="商品">
              <el-select v-model="superForm.itemId" placeholder="选择商品" filterable
                style="width: 100%" :loading="productLoading" clearable
                :disabled="!superForm.accountId">
                <el-option v-for="p in productOptions" :key="p.itemId"
                  :label="`${p.title} · ${p.itemId}`" :value="p.itemId" />
              </el-select>
            </el-form-item>
            <el-form-item label="次数">
              <el-input-number v-model="superForm.times" :min="1" :max="10" style="width: 120px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="loading" @click="doSuperPolish">
                <el-icon><TrendCharts /></el-icon> 超级擦亮
              </el-button>
            </el-form-item>
          </el-form>
          <div v-if="superResult" class="result-json-wrap">
            <pre class="json-block">{{ JSON.stringify(superResult, null, 2) }}</pre>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { polishItem, batchPolish, superPolish } from '@/api/polish'
import { listAccounts } from '@/api/account'
import { Promotion, RefreshLeft, Grid, TrendCharts } from '@element-plus/icons-vue'

const activeTab = ref('single')
const accounts = ref([])
const accountsLoading = ref(false)
const loading = ref(false)
const productLoading = ref(false)
const productOptions = ref([])
const accountId = ref(null)

const singleForm = ref({ itemId: '' })
const singleResult = ref(null)
const batchForm = ref({ itemIds: [] })
const batchResult = ref(null)
const superForm = ref({ itemId: '', times: 3 })
const superResult = ref(null)

const singleItem = computed(() => {
  return singleResult.value?.response?.data?.itemDO || null
})

function formatTime(t) {
  if (!t) return ''
  try {
    const d = new Date(t.replace('T', ' ').replace('Z', ''))
    if (isNaN(d.getTime())) return t
    return d.toLocaleString('zh-CN', { hour12: false })
  } catch { return t }
}

onMounted(async () => {
  accountsLoading.value = true
  try {
    const res = await listAccounts()
    accounts.value = res.data || []
    if (accounts.value.length > 0) {
      accountId.value = accounts.value[0].id
      singleForm.value.accountId = accounts.value[0].id
      batchForm.value.accountId = accounts.value[0].id
      superForm.value.accountId = accounts.value[0].id
      await onAccountChange()
    }
  } catch (e) {
    ElMessage.error('拉账号列表失败')
  } finally {
    accountsLoading.value = false
  }
})

async function onAccountChange() {
  singleForm.value.itemId = ''
  batchForm.value.itemIds = []
  superForm.value.itemId = ''
  productOptions.value = []
  singleResult.value = null; batchResult.value = null; superResult.value = null
  const selectedId = accountId.value
  singleForm.value.accountId = selectedId
  batchForm.value.accountId = selectedId
  superForm.value.accountId = selectedId
  if (!selectedId) return
  productLoading.value = true
  try {
    const res = await request.get('/products', { params: { accountId: selectedId, status: 'ON_SALE', page: 1, size: 200 } })
    productOptions.value = (res.data?.records || []).filter(p => p.itemId)
    if (!productOptions.value.length) {
      ElMessage.warning('该账号暂无已同步的在售商品，请先在「商品管理」同步闲鱼')
    }
  } catch (e) {
    ElMessage.error('拉取商品列表失败')
  } finally {
    productLoading.value = false
  }
}

async function doPolish() {
  if (!singleForm.value.accountId || !singleForm.value.itemId) return ElMessage.warning('请选择账号和商品')
  loading.value = true
  try {
    const res = await polishItem(singleForm.value.accountId, singleForm.value.itemId)
    singleResult.value = res.data
    ElMessage.success(res.data?.success ? '擦亮成功' : '擦亮已发（请看响应）')
  } catch (e) { ElMessage.error('擦亮失败: ' + e.message) }
  finally { loading.value = false }
}

async function doBatchPolish() {
  if (!batchForm.value.accountId || !batchForm.value.itemIds.length) return ElMessage.warning('请选择账号和商品')
  loading.value = true
  try {
    const res = await batchPolish(batchForm.value.accountId, batchForm.value.itemIds)
    batchResult.value = res.data
    ElMessage.success(`批量擦亮完成：成功 ${res.data?.success || 0} / ${res.data?.total || 0}`)
  } catch (e) { ElMessage.error('批量擦亮失败: ' + e.message) }
  finally { loading.value = false }
}

async function doSuperPolish() {
  if (!superForm.value.accountId || !superForm.value.itemId) return ElMessage.warning('请选择账号和商品')
  loading.value = true
  try {
    const res = await superPolish(superForm.value.accountId, superForm.value.itemId, superForm.value.times)
    superResult.value = res.data
    ElMessage.success(`超级擦亮完成：成功 ${res.data?.success || 0} / ${res.data?.times || 0}`)
  } catch (e) { ElMessage.error('超级擦亮失败: ' + e.message) }
  finally { loading.value = false }
}
</script>

<style scoped>
.page-root { padding: 0; }

.item-detail-panel { margin-top: 16px; }
.sub-title {
  font-size: 14px; font-weight: 600; color: var(--text-1);
  margin-bottom: 10px; padding-left: 8px;
  border-left: 3px solid var(--brand);
}
.section-label {
  font-size: 14px; font-weight: 600; color: var(--text-1);
  margin-bottom: 10px; padding-left: 8px;
  border-left: 3px solid var(--brand);
}

.polish-item-title { font-weight: 600; color: var(--text-1); }
.price-now { color: var(--color-danger); font-weight: 700; font-size: 16px; }
.price-old { color: var(--text-3); text-decoration: line-through; margin-left: 8px; font-size: 13px; }

.polish-images { margin-top: 16px; padding-top: 16px; border-top: 1px solid var(--border); }
.image-grid { display: flex; gap: 10px; flex-wrap: wrap; }
.polish-image { width: 100px; height: 100px; border-radius: var(--radius-md); border: 1px solid var(--border); object-fit: cover; }

.result-json-wrap { margin-top: 16px; }
.json-block {
  background: var(--bg-soft); padding: 16px;
  border-radius: var(--radius-md); max-height: 400px;
  overflow: auto; font-size: 12px; line-height: 1.5;
}
</style>
