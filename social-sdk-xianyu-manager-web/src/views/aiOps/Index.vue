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

    <!-- 运营知识库卡片 -->
    <el-card shadow="never" style="margin-top: 20px;">
      <div class="card-head">
        <div class="card-head-left">
          <div class="card-chip chip-violet"><el-icon><Collection /></el-icon></div>
          <div class="card-head-text">
            <div class="card-title">AI 运营知识库</div>
            <div class="card-sub">沉淀定价、文案、发布时间、关键词等运营经验，供批量上品与周报复用（每日任务自动归档 AI 生成项）</div>
          </div>
        </div>
        <div class="card-head-right">
          <el-button type="primary" @click="openCreateKnowledge">
            <el-icon><Plus /></el-icon> 新增知识
          </el-button>
        </div>
      </div>

      <div class="filter-row">
        <el-input v-model="knowledgeQuery.category" placeholder="品类筛选" clearable style="width: 160px;" @keyup.enter="reloadKnowledge" />
        <el-select v-model="knowledgeQuery.knowledgeType" placeholder="知识类型" clearable style="width: 160px;" @change="reloadKnowledge">
          <el-option v-for="o in knowledgeTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-button @click="reloadKnowledge"><el-icon><Search /></el-icon> 查询</el-button>
      </div>

      <el-table :data="knowledgeList" v-loading="knowledgeLoading" style="margin-top: 12px;" empty-text="暂无知识">
        <el-table-column prop="category" label="品类" width="120" />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">{{ labelOf(knowledgeTypeOptions, row.knowledgeType) }}</template>
        </el-table-column>
        <el-table-column label="来源" width="110">
          <template #default="{ row }">{{ labelOf(sourceOptions, row.source) }}</template>
        </el-table-column>
        <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
        <el-table-column prop="updatedAt" label="更新时间" width="170" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-popconfirm title="确认删除该知识？" @confirm="removeKnowledge(row.id)">
              <template #reference>
                <el-button type="danger" link><el-icon><Delete /></el-icon> 删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-button :disabled="knowledgeQuery.page <= 1" @click="knowledgeQuery.page--; reloadKnowledge()">上一页</el-button>
        <span class="pager-text">第 {{ knowledgeQuery.page }} 页</span>
        <el-button :disabled="knowledgeList.length < knowledgeQuery.size" @click="knowledgeQuery.page++; reloadKnowledge()">下一页</el-button>
      </div>
    </el-card>

    <!-- 运营建议卡片 -->
    <el-card shadow="never" style="margin-top: 20px;">
      <div class="card-head">
        <div class="card-head-left">
          <div class="card-chip chip-green"><el-icon><ChatDotRound /></el-icon></div>
          <div class="card-head-text">
            <div class="card-title">AI 运营建议</div>
            <div class="card-sub">基于成交、浏览与关键词数据生成的优化建议，可一键采纳或忽略</div>
          </div>
        </div>
      </div>

      <div class="filter-row">
        <el-select v-model="suggestionQuery.accountId" placeholder="账号（全部）" clearable style="width: 180px;" @change="reloadSuggestions">
          <el-option v-for="acc in accounts" :key="acc.id" :label="acc.accountName" :value="acc.id" />
        </el-select>
        <el-select v-model="suggestionQuery.adopted" placeholder="状态（全部）" clearable style="width: 140px;" @change="reloadSuggestions">
          <el-option label="未采纳" :value="false" />
          <el-option label="已采纳" :value="true" />
        </el-select>
        <el-button @click="reloadSuggestions"><el-icon><Search /></el-icon> 查询</el-button>
      </div>

      <el-table :data="suggestionList" v-loading="suggestionLoading" style="margin-top: 12px;" empty-text="暂无建议">
        <el-table-column label="账号" width="140">
          <template #default="{ row }">{{ accountName(row.accountId) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="120">
          <template #default="{ row }">{{ labelOf(suggestionTypeOptions, row.suggestionType) }}</template>
        </el-table-column>
        <el-table-column prop="suggestionContent" label="建议内容" min-width="240" show-overflow-tooltip />
        <el-table-column label="置信度" width="100">
          <template #default="{ row }">{{ row.confidence != null ? (row.confidence * 100).toFixed(0) + '%' : '-' }}</template>
        </el-table-column>
        <el-table-column prop="expectedImpact" label="预期影响" width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.adopted ? 'success' : 'info'" size="small">{{ row.adopted ? '已采纳' : '未采纳' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!row.adopted" type="success" link @click="doAdopt(row)"><el-icon><Check /></el-icon> 采纳</el-button>
            <el-button v-if="!row.adopted" type="warning" link @click="doIgnore(row)"><el-icon><Close /></el-icon> 忽略</el-button>
            <span v-else class="adopted-text">已处理</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-button :disabled="suggestionQuery.page <= 1" @click="suggestionQuery.page--; reloadSuggestions()">上一页</el-button>
        <span class="pager-text">第 {{ suggestionQuery.page }} 页</span>
        <el-button :disabled="suggestionList.length < suggestionQuery.size" @click="suggestionQuery.page++; reloadSuggestions()">下一页</el-button>
      </div>
    </el-card>

    <!-- 新增知识对话框 -->
    <el-dialog v-model="knowledgeDialog" title="新增运营知识" width="560px">
      <el-form :model="knowledgeForm" label-width="90px">
        <el-form-item label="品类">
          <el-input v-model="knowledgeForm.category" placeholder="如：数码、美妆（可不填）" />
        </el-form-item>
        <el-form-item label="知识类型" required>
          <el-select v-model="knowledgeForm.knowledgeType" placeholder="选择类型" style="width: 100%;">
            <el-option v-for="o in knowledgeTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="knowledgeForm.source" style="width: 100%;">
            <el-option v-for="o in sourceOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input v-model="knowledgeForm.content" type="textarea" :rows="5" placeholder="填写运营经验内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="knowledgeDialog = false">取消</el-button>
        <el-button type="primary" :loading="knowledgeSubmitting" @click="submitKnowledge">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { MagicStick, Refresh, DataAnalysis, Collection, Plus, Delete, Search, Check, Close, ChatDotRound } from '@element-plus/icons-vue'
import api from '@/api/request'
import { batchCreate, multiSync, getWeeklyReport,
  listKnowledge, createKnowledge, deleteKnowledge,
  listSuggestions, adoptSuggestion, ignoreSuggestion } from '@/api/aiOps'

const accounts = ref([])
const aiModels = ref([])
const sourceProducts = ref([])

const accountNameMap = ref({})
const accountName = id => accountNameMap.value[id] || ('账号#' + id)

const labelOf = (options, value) => (options.find(o => o.value === value) || {}).label || value || '-'

const knowledgeTypeOptions = [
  { label: '定价策略', value: 'PRICING' },
  { label: '文案风格', value: 'DESCRIPTION_STYLE' },
  { label: '发布时间', value: 'POSTING_TIME' },
  { label: '关键词', value: 'KEYWORD' }
]
const sourceOptions = [
  { label: '手动录入', value: 'MANUAL' },
  { label: 'AI 生成', value: 'AI_GENERATED' },
  { label: '平台规则', value: 'PLATFORM_RULE' }
]
const suggestionTypeOptions = [
  { label: '调价', value: 'PRICE_ADJUST' },
  { label: '刷新时间', value: 'REFRESH_TIME' },
  { label: '上架优化', value: 'LISTING_OPTIMIZE' }
]

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
    const map = {}
    list.forEach(a => { map[a.id] = a.accountName })
    accountNameMap.value = map
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

// ==================== 运营知识库 ====================
const knowledgeList = ref([])
const knowledgeLoading = ref(false)
const knowledgeQuery = ref({ category: '', knowledgeType: '', page: 1, size: 20 })
const knowledgeDialog = ref(false)
const knowledgeSubmitting = ref(false)
const knowledgeForm = ref({ category: '', knowledgeType: '', content: '', source: 'MANUAL' })

const reloadKnowledge = async () => {
  knowledgeLoading.value = true
  try {
    const res = await listKnowledge({
      category: knowledgeQuery.value.category || undefined,
      knowledgeType: knowledgeQuery.value.knowledgeType || undefined,
      page: knowledgeQuery.value.page,
      size: knowledgeQuery.value.size
    })
    knowledgeList.value = res.data || []
  } catch {
    ElMessage.error('加载知识库失败')
  } finally {
    knowledgeLoading.value = false
  }
}

const openCreateKnowledge = () => {
  knowledgeForm.value = { category: '', knowledgeType: '', content: '', source: 'MANUAL' }
  knowledgeDialog.value = true
}

const submitKnowledge = async () => {
  if (!knowledgeForm.value.knowledgeType) return ElMessage.warning('请选择知识类型')
  if (!knowledgeForm.value.content || !knowledgeForm.value.content.trim()) return ElMessage.warning('请填写内容')
  knowledgeSubmitting.value = true
  try {
    await createKnowledge({ ...knowledgeForm.value, content: knowledgeForm.value.content.trim() })
    ElMessage.success('知识已保存')
    knowledgeDialog.value = false
    reloadKnowledge()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    knowledgeSubmitting.value = false
  }
}

const removeKnowledge = async (id) => {
  try {
    await deleteKnowledge(id)
    ElMessage.success('已删除')
    reloadKnowledge()
  } catch {
    ElMessage.error('删除失败')
  }
}

// ==================== 运营建议 ====================
const suggestionList = ref([])
const suggestionLoading = ref(false)
const suggestionQuery = ref({ accountId: null, adopted: null, page: 1, size: 20 })

const reloadSuggestions = async () => {
  suggestionLoading.value = true
  try {
    const res = await listSuggestions({
      accountId: suggestionQuery.value.accountId || undefined,
      adopted: suggestionQuery.value.adopted,
      page: suggestionQuery.value.page,
      size: suggestionQuery.value.size
    })
    suggestionList.value = res.data || []
  } catch {
    ElMessage.error('加载建议失败')
  } finally {
    suggestionLoading.value = false
  }
}

const doAdopt = async (row) => {
  try {
    await adoptSuggestion(row.id)
    ElMessage.success('已采纳')
    reloadSuggestions()
  } catch {
    ElMessage.error('操作失败')
  }
}

const doIgnore = async (row) => {
  try {
    await ignoreSuggestion(row.id)
    ElMessage.success('已忽略')
    reloadSuggestions()
  } catch {
    ElMessage.error('操作失败')
  }
}

onMounted(() => {
  loadAccounts()
  loadAiModels()
  reloadKnowledge()
  reloadSuggestions()
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
.filter-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
  flex-wrap: wrap;
}
.pager {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 14px;
}
.pager-text {
  color: var(--text-3);
  font-size: 13px;
}
.adopted-text {
  color: var(--text-3);
  font-size: 13px;
}
</style>
