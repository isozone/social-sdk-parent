<template>
  <div class="page-root">
    <el-card shadow="never">
      <div class="card-head">
        <div class="card-head-left">
          <div class="card-chip chip-blue"><el-icon><Filter /></el-icon></div>
          <div class="card-head-text">
            <div class="card-title">发货匹配规则</div>
            <div class="card-sub">买家消息/订单关键词 → 命中卡券，决定发哪张卡、发几次（BOT-O3）</div>
          </div>
        </div>
        <el-button type="primary" @click="openCreate"><el-icon><Plus /></el-icon> 新建规则</el-button>
      </div>

      <el-form inline style="margin-top: 12px">
        <el-form-item label="账号">
          <el-select v-model="filter.accountId" placeholder="全部账号" clearable filterable style="width: 220px" @change="loadList">
            <el-option v-for="a in accounts" :key="a.id" :label="a.accountName || a.id" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="商品ID">
          <el-input v-model="filter.itemId" placeholder="精确 itemId" clearable style="width: 220px" @change="loadList" />
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
        <el-table-column prop="itemId" label="商品ID" width="160" />
        <el-table-column prop="keyword" label="关键词" min-width="180" show-overflow-tooltip />
        <el-table-column prop="matchMode" label="匹配模式" width="100" />
        <el-table-column prop="cardId" label="卡券ID" width="90" />
        <el-table-column prop="deliveryCount" label="发几张" width="80" />
        <el-table-column prop="priority" label="优先级" width="80" />
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="hitCount" label="命中次数" width="90" />
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
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

    <el-dialog v-model="dialogVisible" :title="editing.id ? '编辑规则' : '新建规则'" width="560px">
      <el-form :model="editing" label-width="100px">
        <el-form-item label="账号">
          <el-select v-model="editing.accountId" placeholder="全局规则（不限账号）" clearable filterable style="width: 100%">
            <el-option v-for="a in accounts" :key="a.id" :label="a.accountName || a.id" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="商品ID">
          <el-input v-model="editing.itemId" placeholder="留空=不限商品" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="editing.keyword" placeholder="多个用英文逗号分隔，命中任一即匹配" />
        </el-form-item>
        <el-form-item label="匹配模式">
          <el-select v-model="editing.matchMode" style="width: 100%">
            <el-option label="包含任一关键词（CONTAINS）" value="CONTAINS" />
            <el-option label="完全相等（EXACT）" value="EXACT" />
            <el-option label="正则匹配（REGEX）" value="REGEX" />
          </el-select>
        </el-form-item>
        <el-form-item label="卡券ID">
          <el-input-number v-model="editing.cardId" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="发几张">
          <el-input-number v-model="editing.deliveryCount" :min="1" :max="99" style="width: 100%" />
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
import { listDeliveryRules, createDeliveryRule, updateDeliveryRule, deleteDeliveryRule } from '@/api/bot.js'

const accounts = ref([])
const rows = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const filter = reactive({ accountId: null, itemId: '' })
const dialogVisible = ref(false)
const editing = reactive({})
const saving = ref(false)

const accountName = (id) => {
  const a = accounts.value.find(x => x.id === id)
  return a ? (a.accountName || a.id) : ''
}

async function loadAccounts() {
  try {
    const res = await api.get('/accounts')
    if (res.success) accounts.value = res.data
  } catch (e) { /* 拦截器已提示 */ }
}

async function loadList() {
  loading.value = true
  try {
    const res = await listDeliveryRules({ page: page.value, size: size.value, accountId: filter.accountId, itemId: filter.itemId })
    if (res.success) {
      rows.value = res.data.records || []
      total.value = res.data.total || 0
    } else ElMessage.error(res.message || '加载失败')
  } catch (e) { /* 拦截器已提示 */ }
  finally { loading.value = false }
}

function onPage(p) { page.value = p; loadList() }

function openCreate() {
  Object.assign(editing, { id: null, accountId: null, itemId: '', keyword: '', matchMode: 'CONTAINS', cardId: null, deliveryCount: 1, priority: 100, enabled: 1, remark: '' })
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(editing, JSON.parse(JSON.stringify(row)))
  dialogVisible.value = true
}

async function onSave() {
  if (!editing.keyword || !editing.cardId) { ElMessage.warning('关键词和卡券ID必填'); return }
  saving.value = true
  try {
    const res = editing.id ? await updateDeliveryRule(editing.id, editing) : await createDeliveryRule(editing)
    if (res.success) { ElMessage.success('已保存'); dialogVisible.value = false; await loadList() }
    else ElMessage.error(res.message || '保存失败')
  } catch (e) { /* 拦截器已提示 */ }
  finally { saving.value = false }
}

async function onDelete(id) {
  try {
    const res = await deleteDeliveryRule(id)
    if (res.success) { ElMessage.success('已删除'); await loadList() }
    else ElMessage.error(res.message || '删除失败')
  } catch (e) { /* 拦截器已提示 */ }
}

onMounted(async () => { await loadAccounts(); await loadList() })
</script>

<style scoped>
.page-root { padding: 16px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.card-head-left { display: flex; align-items: center; gap: 12px; }
.card-chip { width: 40px; height: 40px; border-radius: 8px; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 20px; }
.chip-blue { background: #3b82f6; }
.card-title { font-size: 18px; font-weight: 600; }
.card-sub { font-size: 12px; color: #6b7280; margin-top: 2px; }
</style>
