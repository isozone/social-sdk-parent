<template>
  <div class="page-root">
    <el-card shadow="never">
      <div class="card-head">
        <div class="card-head-left">
          <div class="card-chip chip-green"><el-icon><ChatDotRound /></el-icon></div>
          <div class="card-head-text">
            <div class="card-title">评价模板</div>
            <div class="card-sub">多条评价模板随机选用，避免重复评价被风控（BOT-B1）</div>
          </div>
        </div>
        <el-button type="primary" @click="openCreate"><el-icon><Plus /></el-icon> 新建模板</el-button>
      </div>

      <el-form inline style="margin-top: 12px">
        <el-form-item label="账号">
          <el-select v-model="filter.accountId" placeholder="全部账号" clearable filterable style="width: 220px" @change="loadList">
            <el-option v-for="a in accounts" :key="a.id" :label="a.accountName || a.id" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="filter.category" placeholder="全部分类" clearable style="width: 160px" @change="loadList">
            <el-option label="好评（POSITIVE）" value="POSITIVE" />
            <el-option label="中评（NEUTRAL）" value="NEUTRAL" />
            <el-option label="追评回复（REPLY）" value="REPLY" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="loadList"><el-icon><Search /></el-icon> 查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="rows" v-loading="loading" border stripe style="margin-top: 8px">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="账号" width="140">
          <template #default="{ row }">{{ accountName(row.accountId) || '全局' }}</template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="100" />
        <el-table-column prop="name" label="模板名称" width="160" show-overflow-tooltip />
        <el-table-column prop="content" label="评价文本" min-width="280" show-overflow-tooltip />
        <el-table-column prop="priority" label="优先级" width="80" />
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled === 1" @change="onToggle(row)" />
          </template>
        </el-table-column>
        <el-table-column prop="useCount" label="使用次数" width="90" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除？" @confirm="onDelete(row.id)">
              <template #reference><el-button size="small" type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination style="margin-top: 12px" :current-page="page" :page-size="size" :total="total"
        layout="total, prev, pager, next" @current-change="onPage" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editing.id ? '编辑模板' : '新建模板'" width="560px">
      <el-form :model="editing" label-width="100px">
        <el-form-item label="账号">
          <el-select v-model="editing.accountId" placeholder="全局模板（不限账号）" clearable filterable style="width: 100%">
            <el-option v-for="a in accounts" :key="a.id" :label="a.accountName || a.id" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="editing.category" style="width: 100%">
            <el-option label="好评（POSITIVE）" value="POSITIVE" />
            <el-option label="中评（NEUTRAL）" value="NEUTRAL" />
            <el-option label="追评回复（REPLY）" value="REPLY" />
          </el-select>
        </el-form-item>
        <el-form-item label="模板名称">
          <el-input v-model="editing.name" placeholder="便于管理端识别" />
        </el-form-item>
        <el-form-item label="评价文本">
          <el-input v-model="editing.content" type="textarea" :rows="4" placeholder="支持占位符：{商品名} {买家昵称}" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="editing.priority" :min="0" :max="9999" style="width: 100%" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="editing.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editing.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '@/api/request.js'
import { listCommentTemplates, createCommentTemplate, updateCommentTemplate, deleteCommentTemplate, toggleCommentTemplateEnabled } from '@/api/bot.js'

const accounts = ref([])
const rows = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const filter = reactive({ accountId: null, category: '' })
const dialogVisible = ref(false)
const editing = reactive({})
const saving = ref(false)

const accountName = (id) => {
  const a = accounts.value.find(x => x.id === id)
  return a ? (a.accountName || a.id) : ''
}

async function loadAccounts() {
  try { const res = await api.get('/accounts'); if (res.success) accounts.value = res.data } catch (e) {}
}

async function loadList() {
  loading.value = true
  try {
    const res = await listCommentTemplates({ page: page.value, size: size.value, accountId: filter.accountId, category: filter.category })
    if (res.success) { rows.value = res.data.records || []; total.value = res.data.total || 0 }
    else ElMessage.error(res.message || '加载失败')
  } catch (e) {} finally { loading.value = false }
}

function onPage(p) { page.value = p; loadList() }

function openCreate() {
  Object.assign(editing, { id: null, accountId: null, category: 'POSITIVE', name: '', content: '', priority: 100, enabled: 1, remark: '' })
  dialogVisible.value = true
}

function openEdit(row) { Object.assign(editing, JSON.parse(JSON.stringify(row))); dialogVisible.value = true }

async function onSave() {
  if (!editing.content) { ElMessage.warning('评价文本必填'); return }
  saving.value = true
  try {
    const res = editing.id ? await updateCommentTemplate(editing.id, editing) : await createCommentTemplate(editing)
    if (res.success) { ElMessage.success('已保存'); dialogVisible.value = false; await loadList() }
    else ElMessage.error(res.message || '保存失败')
  } catch (e) {} finally { saving.value = false }
}

async function onToggle(row) {
  try {
    const res = await toggleCommentTemplateEnabled(row.id, row.enabled !== 1)
    if (res.success) { ElMessage.success(row.enabled === 1 ? '已停用' : '已启用'); await loadList() }
    else ElMessage.error(res.message || '切换失败')
  } catch (e) {}
}

async function onDelete(id) {
  try {
    const res = await deleteCommentTemplate(id)
    if (res.success) { ElMessage.success('已删除'); await loadList() }
    else ElMessage.error(res.message || '删除失败')
  } catch (e) {}
}

onMounted(async () => { await loadAccounts(); await loadList() })
</script>

<style scoped>
.page-root { padding: 16px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.card-head-left { display: flex; align-items: center; gap: 12px; }
.card-chip { width: 40px; height: 40px; border-radius: 8px; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 20px; }
.chip-green { background: #10b981; }
.card-title { font-size: 18px; font-weight: 600; }
.card-sub { font-size: 12px; color: #6b7280; margin-top: 2px; }
</style>
