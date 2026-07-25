<template>
  <div class="page-root">
    <el-card shadow="never" style="margin-bottom: var(--space-4);">
      <template #header>
        <div class="card-head">
          <div class="card-head-left">
            <div class="card-chip chip-violet"><el-icon><User /></el-icon></div>
            <div class="card-head-text">
              <div class="card-title">买家画像</div>
              <div class="card-sub">搜索、筛选与分析已交互买家</div>
            </div>
          </div>
          <el-button size="small" @click="loadData"><el-icon><Refresh /></el-icon> 刷新</el-button>
        </div>
      </template>
      <div class="page-toolbar" style="margin-bottom: var(--space-4);">
        <el-input v-model="searchKeyword" placeholder="搜索昵称或买家ID" clearable @keyup.enter="loadData" />
        <el-button type="primary" @click="loadData">搜索</el-button>
      </div>
      <el-table :data="buyers" stripe v-loading="loading" size="small">
        <el-table-column prop="nickname" label="昵称" width="140" />
        <el-table-column prop="buyerId" label="买家ID" width="140" />
        <el-table-column prop="credibilityScore" label="可信度" width="100" sortable>
          <template #default="{ row }">
            <el-tag :type="credType(row.credibilityScore)" size="small">{{ row.credibilityScore || 50 }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalSessions" label="会话数" sortable width="100" />
        <el-table-column prop="totalMessages" label="消息数" sortable width="100" />
        <el-table-column prop="totalOrders" label="成交数" sortable width="100" />
        <el-table-column prop="totalSpent" label="成交金额" sortable width="120" />
        <el-table-column prop="bargainCount" label="议价次数" sortable width="100" />
        <el-table-column label="标签" min-width="200">
          <template #default="{ row }">
            <el-tag v-for="t in parseTags(row.tags)" :key="t" size="small" class="tag-gap">{{ t }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="viewDetail(row)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无买家" /></template>
      </el-table>
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        layout="prev, pager, next, total"
        @current-change="loadData"
      />
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="买家详情" width="600px">
      <div v-if="currentBuyer">
        <el-descriptions :column="1" border size="default" class="detail-desc">
          <el-descriptions-item label="买家ID">{{ currentBuyer.buyerId }}</el-descriptions-item>
          <el-descriptions-item label="昵称">{{ currentBuyer.nickname }}</el-descriptions-item>
          <el-descriptions-item label="可信度">
            <el-tag :type="credType(currentBuyer.credibilityScore)">{{ currentBuyer.credibilityScore || 50 }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="首次交互账号">{{ currentBuyer.firstAccountId }}</el-descriptions-item>
          <el-descriptions-item label="总会话/消息/成交">
            {{ currentBuyer.totalSessions }} / {{ currentBuyer.totalMessages }} / {{ currentBuyer.totalOrders }}
          </el-descriptions-item>
          <el-descriptions-item label="累计成交金额">¥{{ currentBuyer.totalSpent || 0 }}</el-descriptions-item>
          <el-descriptions-item label="议价次数">{{ currentBuyer.bargainCount }}</el-descriptions-item>
          <el-descriptions-item label="标签">
            <el-tag v-for="t in parseTags(currentBuyer.tags)" :key="t" size="small" class="tag-gap">{{ t }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <div style="margin-top: var(--space-3);">
          <p class="detail-label">运营备注</p>
          <el-input v-model="notes" type="textarea" :rows="3" />
          <el-button type="primary" @click="saveNotes" style="margin-top: var(--space-2);">保存备注</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { User, Refresh } from '@element-plus/icons-vue'
import { getBuyerList, setBuyerNotes } from '@/api/market'

const buyers = ref([])
const loading = ref(false)
const searchKeyword = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const detailVisible = ref(false)
const currentBuyer = ref(null)
const notes = ref('')

function parseTags(tags) {
  if (!tags) return []
  try { return JSON.parse(tags) } catch { return [] }
}
function credType(score) {
  if (score >= 80) return 'success'
  if (score >= 50) return 'warning'
  return 'danger'
}

async function loadData() {
  loading.value = true
  try {
    const r = await getBuyerList(page.value - 1, size.value, searchKeyword.value)
    if (r.success) {
      buyers.value = r.data || []
      total.value = buyers.value.length
    }
  } catch (e) {}
  loading.value = false
}

function viewDetail(row) {
  currentBuyer.value = row
  notes.value = row.notes || ''
  detailVisible.value = true
}

async function saveNotes() {
  if (!currentBuyer.value) return
  try {
    await setBuyerNotes(currentBuyer.value.buyerId, notes.value)
    ElMessage.success('备注已保存')
    currentBuyer.value.notes = notes.value
  } catch (e) {}
}

onMounted(loadData)
</script>

<style scoped>
.tag-gap { margin-right: var(--space-1); }
.detail-desc { margin-bottom: var(--space-3); }
.detail-label { font-weight: 600; color: var(--text-2); margin-bottom: var(--space-2); }
</style>
