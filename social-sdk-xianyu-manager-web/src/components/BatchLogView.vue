<!--
  通用批次日志视图：Cookie 刷新、登录续期、等批量任务均可复用。

  Props:
    - title             : 页面标题（必填）
    - apiPrefix         : 批次接口前缀，如 /account/cookie-renew → GET /xxx/logs + POST /xxx/run
    - alert             : 可选的 el-alert 配置 { type, title, closable }
    - resultExtraKey    : 可选，附加显示的字段 key（如 'waitingQrCount'）
    - resultExtraLabel  : 可选，附加字段的显示标签
    - columns           : 可选，额外自定义列配置 [{ prop, label, width, key }]，用于覆盖/追加标准列
-->
<template>
  <div class="page-root">
    <el-card style="margin: 0;">
      <template #header>
        <div class="card-header">
          <span>{{ title }}</span>
          <div>
            <el-button size="small" type="primary" :loading="running" @click="runManually">手动触发批次</el-button>
            <el-button size="small" @click="loadLogs">刷新</el-button>
          </div>
        </div>
      </template>

      <el-alert
        v-if="alert"
        :type="alert.type || 'info'"
        :closable="alert.closable !== false"
        :title="alert.title"
        show-icon
        class="log-alert"
      />

      <el-table :data="logs" stripe v-loading="loading">
        <el-table-column prop="id" label="批次 ID" width="80" />
        <el-table-column prop="triggerSource" label="触发来源" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="triggerTagType(row.triggerSource)">{{ triggerLabel(row.triggerSource) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="结果" min-width="260">
          <template #default="{ row }">
            <span class="result-text">
              共 {{ row.totalCount }} · 成功 {{ row.successCount }} ·
              <span class="text-danger">失败 {{ row.failedCount }}</span> · 跳过 {{ row.skippedCount }}
              <template v-if="resultExtraKey && row[resultExtraKey] != null">
                · {{ resultExtraLabel || resultExtraKey }} {{ row[resultExtraKey] }}
              </template>
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开始时间" width="180">
          <template #default="{ row }">{{ formatTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="180">
          <template #default="{ row }">{{ formatTime(row.endedAt) }}</template>
        </el-table-column>
        <el-table-column prop="failureSummary" label="失败原因聚合" min-width="280" show-overflow-tooltip />
        <slot name="extra-columns" />
        <el-table-column label="明细" width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link @click="showDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadLogs"
          @size-change="loadLogs"
        />
      </div>
    </el-card>

    <el-drawer v-model="detailVisible" :title="detailTitle" size="60%">
      <el-table :data="items" stripe v-loading="itemsLoading">
        <el-table-column prop="itemLabel" label="账号" min-width="180" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="durationMs" label="耗时(ms)" width="110" />
        <el-table-column prop="failureReason" label="失败原因" min-width="280" show-overflow-tooltip />
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '@/api/request'
import { ElMessage } from 'element-plus'

const props = defineProps({
  title: { type: String, required: true },
  apiPrefix: { type: String, required: true },
  alert: { type: Object, default: null },
  resultExtraKey: { type: String, default: '' },
  resultExtraLabel: { type: String, default: '' },
})

const logs = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const running = ref(false)

const detailVisible = ref(false)
const detailTitle = ref('批次明细')
const items = ref([])
const itemsLoading = ref(false)

async function loadLogs() {
  loading.value = true
  try {
    const res = await api.get(props.apiPrefix + '/logs', { params: { page: page.value, size: size.value } })
    if (res.success) {
      logs.value = res.data?.records || []
      total.value = res.data?.total || 0
    }
  } catch (e) {}
  finally { loading.value = false }
}

async function runManually() {
  running.value = true
  try {
    const res = await api.post(props.apiPrefix + '/run')
    if (res.success) {
      ElMessage.success('已触发，批次 ID=' + res.data)
      await loadLogs()
    } else {
      ElMessage.warning(res.message || '触发失败')
    }
  } catch (e) {
    ElMessage.error('触发失败：' + (e.message || '未知错误'))
  } finally {
    running.value = false
  }
}

async function showDetail(row) {
  detailTitle.value = `批次 ${row.id} 明细`
  detailVisible.value = true
  itemsLoading.value = true
  try {
    const res = await api.get('/batch/' + row.batchJobId + '/items')
    if (res.success) items.value = res.data || []
  } catch (e) { items.value = [] }
  finally { itemsLoading.value = false }
}

function statusLabel(s) {
  return { RUNNING: '执行中', SUCCESS: '成功', PARTIAL: '部分失败', FAILED: '失败', CANCELLED: '已取消' }[s] || s
}
function statusTagType(s) {
  return { SUCCESS: 'success', PARTIAL: 'warning', FAILED: 'danger', RUNNING: 'primary', CANCELLED: 'info' }[s] || 'info'
}
function triggerLabel(t) {
  return { SCHEDULER: '定时', MANUAL: '手动', SYSTEM: '系统事件' }[t] || t
}
function triggerTagType(t) {
  return { SCHEDULER: 'info', MANUAL: 'primary', SYSTEM: 'warning' }[t] || 'info'
}
function formatTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').substring(0, 19)
}

onMounted(loadLogs)
</script>

<style scoped>
.page-root { padding: 0; }
.card-header {
  display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap;
}
.log-alert { margin-bottom: var(--space-3); }
.result-text { font-size: 12px; line-height: 1.5; }
.text-danger { color: var(--color-danger); }
.pagination-wrap {
  display: flex; justify-content: flex-end; margin-top: var(--space-4); padding-right: var(--space-4);
}
</style>
