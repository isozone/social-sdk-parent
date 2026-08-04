<template>
  <div class="page-root">
    <!-- 头部操作栏 -->
    <el-card shadow="never" style="margin-bottom: 16px;">
      <div class="card-head">
        <div class="card-head-left">
          <div class="card-chip chip-cyan"><el-icon><ShoppingBag /></el-icon></div>
          <div class="card-head-text">
            <div class="card-title">商品管理</div>
            <div class="card-sub">同步闲鱼商品、切换在售/全部，管理虚拟发货配置</div>
          </div>
        </div>
        <div class="card-head-right">
          <el-select
            v-model="selectedAccountId"
            placeholder="选择账号"
            style="width: 220px;"
            :loading="accountsLoading"
            clearable
          >
            <el-option v-for="a in accounts" :key="a.id" :label="a.displayName || a.accountName" :value="a.id" />
          </el-select>
          <el-button type="primary" :loading="syncing" :disabled="!selectedAccountId" @click="syncProducts">
            <el-icon><RefreshRight /></el-icon>同步商品
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 主内容区 -->
    <el-card shadow="never" style="margin: 0;">
      <el-tabs v-model="activeTab" @tab-change="loadProducts">
        <el-tab-pane label="在售" name="ON_SALE" />
        <el-tab-pane label="全部" name="ALL" />
      </el-tabs>

      <el-table :data="products" stripe v-loading="loading" style="margin-top: 12px;">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="图片" width="80">
          <template #default="{ row }">
            <el-image :src="row.imageUrl" style="width: 50px; height: 50px; border-radius: 8px;" fit="cover" />
          </template>
        </el-table-column>
        <el-table-column prop="title" label="商品标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="description" label="内容描述" min-width="260" show-overflow-tooltip />
        <el-table-column label="价格" width="100">
          <template #default="{ row }">¥{{ row.amount || row.price || '0.00' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="{ ON_SALE: 'success', OFF_SALE: 'info' }[row.status] || 'info'">
              {{ { ON_SALE: '在售', OFF_SALE: '下架' }[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="viewCount" label="浏览" width="80" />
        <el-table-column prop="favoriteCount" label="收藏" width="80" />
        <el-table-column label="操作" width="260">
          <template #default="{ row }">
            <el-button size="small" @click="editProduct(row)">编辑</el-button>
            <el-button size="small" type="primary" @click="openVirtualShipConfig(row)">虚拟发货配置</el-button>
            <el-button size="small" type="danger" :disabled="row.status !== 'ON_SALE'" @click="offShelf(row)">下架</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" layout="total, prev, pager, next" @current-change="loadProducts" />
      </div>
    </el-card>

    <!-- 同步进度弹窗 -->
    <el-dialog v-model="syncProgressVisible" title="同步商品" width="420px" :close-on-click-modal="false" :show-close="false">
      <div style="text-align: center; padding: 12px 0;">
        <el-progress :percentage="syncProgress && syncProgress.total ? Math.round((syncProgress.current / syncProgress.total) * 100) : 0" :stroke-width="16" style="margin-bottom: 16px;" />
        <div v-if="syncProgress" style="font-size: 14px; color: var(--text-1);">
          <div style="margin-bottom: 6px;">{{ syncProgress.message || '正在同步...' }}</div>
          <div v-if="syncProgress.phase === 'DETAILING'" style="font-size: 12px; color: var(--text-3);">
            已处理 {{ syncProgress.current }} / {{ syncProgress.total }} 件
          </div>
        </div>
        <div v-else style="font-size: 14px; color: var(--text-3);">正在启动同步任务...</div>
      </div>
    </el-dialog>

    <!-- 虚拟发货配置弹窗 -->
    <el-dialog v-model="vsConfigVisible" title="商品虚拟发货配置" width="640px">
      <el-form :model="vsConfigForm" label-width="120px">
        <el-form-item label="商品">
          <span style="font-weight: 600;">{{ vsConfigForm.title }}</span>
        </el-form-item>
        <el-form-item label="商品类型">
          <el-radio-group v-model="vsConfigForm.goodsType">
            <el-radio-button value="VIRTUAL">虚拟商品</el-radio-button>
            <el-radio-button value="PHYSICAL">实物商品</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="发货类型" v-if="vsConfigForm.goodsType === 'VIRTUAL'">
          <el-radio-group v-model="vsConfigForm.deliverType" @change="onVsDeliverTypeChange">
            <el-radio-button value="CARD">卡密</el-radio-button>
            <el-radio-button value="ACCOUNT">账号</el-radio-button>
            <el-radio-button value="LINK">链接文本</el-radio-button>
            <el-radio-button value="FILE">网盘文件</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <!-- 动态发货内容表单：与本地商品新建弹窗同构，保存时组合成 JSON -->
        <template v-if="vsConfigForm.goodsType === 'VIRTUAL' && vsConfigForm.deliverType">
          <el-form-item v-if="vsConfigForm.deliverType === 'LINK'" label="发货链接">
            <el-input v-model="vsDeliverForm.link" placeholder="https://pan.quark.cn/s/xxx（买家直接点击的下载链接）" />
          </el-form-item>
          <el-form-item v-if="vsConfigForm.deliverType === 'CARD'" label="卡密列表">
            <el-input v-model="vsDeliverForm.cardsText" type="textarea" :rows="5" placeholder="每行一条：卡号|密码（密码可省略）&#10;ABC123|pwd1&#10;DEF456" />
          </el-form-item>
          <el-form-item v-if="vsConfigForm.deliverType === 'ACCOUNT'" label="账号列表">
            <el-input v-model="vsDeliverForm.accountsText" type="textarea" :rows="5" placeholder="每行一条：账号|密码|服务器（服务器可省略）&#10;user1|pwd1|srv1" />
          </el-form-item>
          <el-form-item v-if="vsConfigForm.deliverType === 'FILE'" label="文件路径">
            <el-input v-model="vsDeliverForm.filePath" placeholder="/data/files/xxx.zip（本地文件路径，发布后自动上传网盘）" />
          </el-form-item>
          <el-form-item label="发货消息模板">
            <el-input v-model="vsDeliverForm.message" type="textarea" :rows="3" :placeholder="vsDeliverMessagePlaceholder" />
            <div style="color: var(--text-3); font-size: 12px; margin-top: 6px; line-height: 1.6;">{{ vsDeliverMessageHint }}</div>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="vsConfigVisible = false">取消</el-button>
        <el-button type="primary" @click="saveVirtualShipConfig" :loading="vsConfigSaving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { RefreshRight, ShoppingBag } from '@element-plus/icons-vue'
import api from '@/api/request'
import { saveProductVirtualShipConfig } from '@/api/virtualShip'

const accounts = ref([])
const accountsLoading = ref(false)
const selectedAccountId = ref(null)
const products = ref([])
const loading = ref(false)
const activeTab = ref('ON_SALE')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const syncing = ref(false)
const syncProgress = ref(null)
const syncProgressVisible = ref(false)
let syncTimer = null

async function loadAccounts() {
  accountsLoading.value = true
  try {
    const res = await api.get('/accounts')
    if (res.success) {
      const list = Array.isArray(res.data) ? res.data : (res.data?.records || [])
      accounts.value = list
      if (list.length > 0 && !selectedAccountId.value) selectedAccountId.value = list[0].id
    }
  } catch (e) {}
  finally { accountsLoading.value = false }
}

async function loadProducts() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (selectedAccountId.value) params.accountId = selectedAccountId.value
    if (activeTab.value !== 'ALL') params.status = activeTab.value
    const res = await api.get('/products', { params })
    if (res.success) {
      products.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch (e) {}
  finally { loading.value = false }
}

async function syncProducts() {
  if (!selectedAccountId.value) return
  syncing.value = true
  syncProgressVisible.value = true
  syncProgress.value = { phase: 'PENDING', total: 0, current: 0, inserted: 0, updated: 0, failed: 0, message: '正在启动同步...' }
  try {
    const res = await api.post('/products/sync', null, { params: { accountId: selectedAccountId.value } })
    if (res.success) {
      const syncId = res.data.syncId
      syncTimer = setInterval(async () => {
        try {
          const prog = await api.get('/products/sync/progress', { params: { syncId } })
          if (prog.success) {
            syncProgress.value = prog.data
            if (prog.data.phase === 'COMPLETED' || prog.data.phase === 'FAILED') {
              clearInterval(syncTimer)
              syncTimer = null
              setTimeout(() => {
                syncProgressVisible.value = false
                syncing.value = false
                if (prog.data.phase === 'COMPLETED') {
                  ElMessage.success(prog.data.message || '同步完成')
                  loadProducts()
                } else {
                  ElMessage.error(prog.data.message || '同步失败')
                }
              }, 1500)
            }
          }
        } catch (e) {}
      }, 1500)
    } else {
      ElMessage.error(res.message || '同步失败')
      syncing.value = false
      syncProgressVisible.value = false
    }
  } catch (e) {
    syncing.value = false
    syncProgressVisible.value = false
  }
}

function editProduct(row) { ElMessage.info('编辑功能待 UI 完善') }
async function offShelf(row) {
  await ElMessageBox.confirm('确认下架？', '提示', { type: 'warning' })
  try {
    const res = await api.post(`/products/${row.id}/shelf-off`)
    if (res.success) {
      ElMessage.success('已下架')
      loadProducts()
    } else {
      ElMessage.error(res.message || '下架失败')
    }
  } catch (e) {}
}

const vsConfigVisible = ref(false)
const vsConfigSaving = ref(false)
const vsConfigForm = ref({
  id: null,
  title: '',
  goodsType: 'PHYSICAL',
  deliverType: 'CARD',
  deliverContentTemplate: ''
})

// 动态发货内容表单：与本地商品新建弹窗同构，保存时组合成 JSON 存入 deliverContentTemplate
const vsDeliverForm = ref({
  link: '',
  cardsText: '',
  accountsText: '',
  filePath: '',
  message: ''
})

const onVsDeliverTypeChange = () => {
  // 切换发货类型时清空上次的字段，避免类型间串数据
  Object.assign(vsDeliverForm.value, { link: '', cardsText: '', accountsText: '', filePath: '', message: '' })
}

const vsDeliverMessagePlaceholder = computed(() => {
  const t = vsConfigForm.value.deliverType
  if (t === 'CARD') return '卡号：${cardCode}\n密码：${cardPassword}（留空走默认格式）'
  if (t === 'ACCOUNT') return '账号：${account}\n密码：${password}\n服务器：${server}（留空走默认格式）'
  if (t === 'LINK') return '感谢购买【${itemTitle}】，下载链接：${link}\n订单号：${orderId}'
  if (t === 'FILE') return '下载链接：${link}\n提取码：${extractCode}\n有效期：7天'
  return ''
})

const vsDeliverMessageHint = computed(() => {
  const t = vsConfigForm.value.deliverType
  if (t === 'CARD') return '可用占位符：${cardCode} ${cardPassword}；每行一张卡密，格式 卡号|密码'
  if (t === 'ACCOUNT') return '可用占位符：${account} ${password} ${server}；每行一个账号，格式 账号|密码|服务器'
  if (t === 'LINK') return '可用占位符：${link} ${itemTitle} ${orderId}'
  if (t === 'FILE') return '可用占位符：${link} ${extractCode} ${fileName}；文件路径为服务器本地路径'
  return ''
})

// 把动态表单组合成 JSON 字符串（保存到 deliverContentTemplate）
function buildVsDeliverJson() {
  const t = vsConfigForm.value.deliverType
  if (!t) return ''
  const f = vsDeliverForm.value
  const obj = { type: t, message: f.message || '' }
  if (t === 'LINK') {
    obj.link = f.link || ''
  } else if (t === 'CARD') {
    obj.cards = f.cardsText.split('\n').map(s => s.trim()).filter(Boolean)
  } else if (t === 'ACCOUNT') {
    obj.accounts = f.accountsText.split('\n').map(s => s.trim()).filter(Boolean)
  } else if (t === 'FILE') {
    obj.filePath = f.filePath || ''
  }
  return JSON.stringify(obj)
}

// 打开配置时把 JSON 解析回动态表单（兼容旧格式：纯文本/数组 → 当作 message）
function parseVsDeliverJson(str) {
  Object.assign(vsDeliverForm.value, { link: '', cardsText: '', accountsText: '', filePath: '', message: '' })
  if (!str) return
  try {
    const obj = JSON.parse(str)
    if (obj && typeof obj === 'object' && !Array.isArray(obj) && obj.type) {
      vsDeliverForm.value.link = obj.link || ''
      vsDeliverForm.value.cardsText = Array.isArray(obj.cards) ? obj.cards.join('\n') : ''
      vsDeliverForm.value.accountsText = Array.isArray(obj.accounts) ? obj.accounts.join('\n') : ''
      vsDeliverForm.value.filePath = obj.filePath || ''
      vsDeliverForm.value.message = obj.message || ''
      return
    }
  } catch { /* fallthrough */ }
  // 旧格式：整体当消息模板
  vsDeliverForm.value.message = str
}

const openVirtualShipConfig = (row) => {
  vsConfigForm.value = {
    id: row.id,
    title: row.title,
    goodsType: row.goodsType || 'PHYSICAL',
    deliverType: row.deliverType || 'CARD',
    deliverContentTemplate: row.deliverContentTemplate || ''
  }
  parseVsDeliverJson(row.deliverContentTemplate || '')
  vsConfigVisible.value = true
}

const saveVirtualShipConfig = async () => {
  vsConfigSaving.value = true
  try {
    const isVirtual = vsConfigForm.value.goodsType === 'VIRTUAL'
    const t = vsConfigForm.value.deliverType
    if (isVirtual && t === 'CARD' && !vsDeliverForm.value.cardsText.trim()) return ElMessage.warning('请填写卡密列表（每行一条）')
    if (isVirtual && t === 'ACCOUNT' && !vsDeliverForm.value.accountsText.trim()) return ElMessage.warning('请填写账号列表（每行一个）')
    if (isVirtual && t === 'LINK' && !vsDeliverForm.value.link.trim()) return ElMessage.warning('请填写发货链接')
    if (isVirtual && t === 'FILE' && !vsDeliverForm.value.filePath.trim()) return ElMessage.warning('请填写文件路径')
    await saveProductVirtualShipConfig(vsConfigForm.value.id, {
      goodsType: vsConfigForm.value.goodsType,
      deliverType: isVirtual ? vsConfigForm.value.deliverType : null,
      deliverContentTemplate: isVirtual ? buildVsDeliverJson() : null
    })
    ElMessage.success('配置已保存')
    vsConfigVisible.value = false
    loadProducts()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '保存配置失败')
  } finally { vsConfigSaving.value = false }
}

onMounted(async () => { await loadAccounts(); await loadProducts() })
</script>

<style scoped>
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
