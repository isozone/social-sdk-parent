<template>
  <div class="page-root noflex">
    <!-- 批量上品卡片 -->
    <el-card shadow="never" style="margin-bottom: 20px;">
      <div class="card-head">
        <div class="card-head-left">
          <div class="card-chip chip-violet"><el-icon><MagicStick /></el-icon></div>
          <div class="card-head-text">
            <div class="card-title">AI 批量上品</div>
            <div class="card-sub">基于商品种子，调用 AI 生成标题、关键词与描述，批量上架闲鱼</div>
          </div>
        </div>
      </div>

      <el-form :model="batchForm" label-width="100px" class="aiops-form">
        <el-form-item label="闲鱼账号">
          <el-select v-model="batchForm.accountId" placeholder="选择闲鱼账号" style="width: 250px;">
            <el-option v-for="acc in accounts" :key="acc.id" :label="acc.accountName" :value="acc.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="商品品类">
          <el-input v-model="batchForm.category" placeholder="如：数码、美妆" style="width: 250px;" />
        </el-form-item>
        <el-form-item label="AI 模型">
          <el-select v-model="batchForm.modelId" placeholder="选择 AI 模型（可选）" clearable style="width: 250px;">
            <el-option v-for="m in aiModels" :key="m.id" :label="`${m.displayName} (${m.modelName})`" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="商品种子">
          <el-input
            v-model="batchSeedText"
            type="textarea"
            :rows="6"
            placeholder="每行一个商品，格式：商品名 | 关键词1,关键词2 | 成色"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="startBatchCreate" :loading="batchLoading">
            <el-icon><MagicStick /></el-icon> 启动批量上品
          </el-button>
        </el-form-item>
      </el-form>

      <div v-if="batchTask" style="margin-top: 20px;">
        <el-alert :title="`任务 #${batchTask.id} - ${batchTask.status}`" :type="taskAlertType(batchTask.status)" show-icon>
          <div v-if="batchTask.resultSummary">{{ batchTask.resultSummary }}</div>
        </el-alert>
      </div>
    </el-card>

    <!-- 多账号同步卡片 -->
    <el-card shadow="never" style="margin-bottom: 20px;">
      <div class="card-head">
        <div class="card-head-left">
          <div class="card-chip chip-cyan"><el-icon><Refresh /></el-icon></div>
          <div class="card-head-text">
            <div class="card-title">多账号同步</div>
            <div class="card-sub">将一个源账号的商品同步到多个目标账号，自动错开发布间隔</div>
          </div>
        </div>
      </div>

      <el-form :model="syncForm" label-width="120px" class="aiops-form">
        <el-form-item label="源账号">
          <el-select v-model="syncForm.sourceAccountId" placeholder="选择源账号" style="width: 250px;" @change="loadSourceProducts">
            <el-option v-for="acc in accounts" :key="acc.id" :label="acc.accountName" :value="acc.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="源商品">
          <el-select v-model="syncForm.productId" placeholder="选择要同步的商品" style="width: 300px;">
            <el-option v-for="p in sourceProducts" :key="p.id" :label="p.title" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标账号">
          <el-select v-model="syncForm.targetAccountIds" multiple placeholder="选择目标账号" style="width: 100%;">
            <el-option v-for="acc in accounts.filter(a => a.id !== syncForm.sourceAccountId)" :key="acc.id" :label="acc.accountName" :value="acc.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="错开时间(分)">
          <el-input-number v-model="syncForm.delayMinutesPerAccount" :min="0" :max="120" />
          <span style="color: var(--text-3); font-size: 12px; margin-left: 8px;">每个账号之间错开的分钟数</span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="startMultiSync" :loading="syncLoading">
            <el-icon><Refresh /></el-icon> 启动多账号同步
          </el-button>
        </el-form-item>
      </el-form>

      <div v-if="syncTask" style="margin-top: 20px;">
        <el-alert :title="`同步任务 #${syncTask.id} - ${syncTask.status}`" :type="taskAlertType(syncTask.status)" show-icon>
          <div v-if="syncTask.resultSummary">{{ syncTask.resultSummary }}</div>
        </el-alert>
      </div>
    </el-card>

    <!-- 运营周报卡片 -->
    <el-card shadow="never">
      <div class="card-head">
        <div class="card-head-left">
          <div class="card-chip chip-amber"><el-icon><DataAnalysis /></el-icon></div>
          <div class="card-head-text">
            <div class="card-title">AI 运营周报</div>
            <div class="card-sub">按账号汇总本周商品、浏览、成交与营收指标，并生成 AI 优化建议</div>
          </div>
        </div>
        <div class="card-head-right">
          <el-select v-model="reportAccountId" placeholder="选择账号" style="width: 180px;">
            <el-option v-for="acc in accounts" :key="acc.id" :label="acc.accountName" :value="acc.id" />
          </el-select>
          <el-button type="primary" @click="generateReport" :loading="reportLoading">
            <el-icon><DataAnalysis /></el-icon>生成周报
          </el-button>
        </div>
      </div>

      <div v-if="report">
        <el-descriptions :column="4" border style="margin-bottom: 20px;">
          <el-descriptions-item label="周期">{{ report.weekStart }} ~ {{ report.weekEnd }}</el-descriptions-item>
          <el-descriptions-item label="商品总数">{{ report.totalProducts }}</el-descriptions-item>
          <el-descriptions-item label="上架">{{ report.onSaleProducts }}</el-descriptions-item>
          <el-descriptions-item label="下架">{{ report.offSaleProducts }}</el-descriptions-item>
          <el-descriptions-item label="草稿">{{ report.draftProducts }}</el-descriptions-item>
          <el-descriptions-item label="浏览量">{{ report.totalViews }}</el-descriptions-item>
          <el-descriptions-item label="收藏">{{ report.totalFavorites }}</el-descriptions-item>
          <el-descriptions-item label="成交">{{ report.completedOrders }} 单</el-descriptions-item>
          <el-descriptions-item label="待发货">{{ report.pendingOrders }}</el-descriptions-item>
          <el-descriptions-item label="营收">¥{{ report.totalRevenue }}</el-descriptions-item>
        </el-descriptions>

        <el-card v-if="report.suggestions && report.suggestions.length > 0" shadow="never" class="suggestion-card">
          <div class="card-head" style="margin-bottom: 12px;">
            <div class="card-head-left">
              <div class="card-chip chip-green"><el-icon><ChatDotRound /></el-icon></div>
              <div class="card-head-text">
                <div class="card-title">AI 运营建议</div>
              </div>
            </div>
          </div>
          <ul class="suggestion-list">
            <li v-for="(s, i) in report.suggestions" :key="i">{{ s }}</li>
          </ul>
        </el-card>
      </div>

      <el-empty v-else description="请选择账号并生成周报" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { MagicStick, Refresh, DataAnalysis } from '@element-plus/icons-vue'
import api from '@/api/request'
import { batchCreate, multiSync, getWeeklyReport } from '@/api/aiOps'

const accounts = ref([])
const aiModels = ref([])
const sourceProducts = ref([])

const batchForm = ref({ accountId: null, category: '', modelId: null })
const batchSeedText = ref('')
const batchLoading = ref(false)
const batchTask = ref(null)

const syncForm = ref({ sourceAccountId: null, productId: null, targetAccountIds: [], delayMinutesPerAccount: 30 })
const syncLoading = ref(false)
const syncTask = ref(null)

const reportAccountId = ref(null)
const reportLoading = ref(false)
const report = ref(null)

const taskAlertType = s => ({ COMPLETED: 'success', RUNNING: 'warning', FAILED: 'error', PENDING: 'info' }[s] || 'info')

const loadAccounts = async () => {
  try {
    const res = await api.get('/accounts')
    const list = Array.isArray(res.data) ? res.data : (res.data?.records || [])
    accounts.value = list.filter(a => a.status === 'ACTIVE')
  } catch {}
}

const loadAiModels = async () => {
  try {
    const res = await api.get('/ai/models', { params: { size: 100 } })
    const list = Array.isArray(res.data) ? res.data : (res.data?.records || [])
    aiModels.value = list.filter(m => m.enabled !== false)
  } catch {}
}

const loadSourceProducts = async () => {
  syncForm.value.productId = null
  if (!syncForm.value.sourceAccountId) { sourceProducts.value = []; return }
  try {
    const res = await api.get('/products', { params: { accountId: syncForm.value.sourceAccountId, status: 'ON_SALE', size: 100 } })
    const list = Array.isArray(res.data) ? res.data : (res.data?.records || [])
    sourceProducts.value = list
  } catch {}
}

const startBatchCreate = async () => {
  if (!batchForm.value.accountId) return ElMessage.warning('请选择闲鱼账号')
  if (!batchSeedText.value.trim()) return ElMessage.warning('请填写商品种子')

  const products = batchSeedText.value.split('\n').filter(l => l.trim()).map(line => {
    const parts = line.split('|').map(p => p.trim())
    return {
      source: parts[0],
      keywords: parts[1] ? parts[1].split(',').map(k => k.trim()) : [],
      imageUrls: [],
      condition: parts[2] || '九成新'
    }
  })

  batchLoading.value = true
  try {
    const res = await batchCreate({ ...batchForm.value, products })
    batchTask.value = res.data
    ElMessage.success('批量上品任务已启动')
  } catch {
    ElMessage.error('启动失败')
  } finally {
    batchLoading.value = false
  }
}

const startMultiSync = async () => {
  if (!syncForm.value.sourceAccountId || !syncForm.value.productId || syncForm.value.targetAccountIds.length === 0) {
    return ElMessage.warning('请完整填写同步信息')
  }
  syncLoading.value = true
  try {
    const res = await multiSync(syncForm.value)
    syncTask.value = res.data
    ElMessage.success('同步任务已启动')
  } catch {
    ElMessage.error('启动失败')
  } finally {
    syncLoading.value = false
  }
}

const generateReport = async () => {
  if (!reportAccountId.value) return ElMessage.warning('请选择账号')
  reportLoading.value = true
  try {
    const res = await getWeeklyReport(reportAccountId.value)
    report.value = res.data
    ElMessage.success('周报已生成')
  } catch {
    ElMessage.error('生成失败')
  } finally {
    reportLoading.value = false
  }
}

onMounted(() => {
  loadAccounts()
  loadAiModels()
})
</script>

<style scoped>
.aiops-form :deep(.el-form-item) {
  margin-bottom: 18px;
}
.suggestion-list {
  padding-left: 20px;
}
.suggestion-list li {
  margin-bottom: 8px;
  line-height: 1.7;
  color: var(--text-2);
}
.suggestion-card :deep(.el-card__body) {
  padding: 0;
}
</style>
