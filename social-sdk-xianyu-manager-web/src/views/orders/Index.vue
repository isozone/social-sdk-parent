<template>
  <div class="page-root">
    <el-card shadow="never">
      <!-- ===== 卡片头部 + 工具栏 ===== -->
      <div class="card-head">
        <div class="card-head-left">
          <div class="card-chip chip-violet">
            <el-icon><List /></el-icon>
          </div>
          <div class="card-head-text">
            <div class="card-title">订单管理</div>
            <div class="card-sub">同步闲鱼订单、虚拟发货、物流追踪</div>
          </div>
        </div>
        <div class="toolbar-right">
          <el-select
            v-model="selectedAccountId"
            placeholder="选择账号"
            style="width: 200px"
            :loading="accountsLoading"
            clearable
          >
            <el-option
              v-for="acc in accounts"
              :key="acc.id"
              :label="acc.displayName || acc.accountName"
              :value="acc.id"
            />
          </el-select>
          <el-button
            type="primary"
            :loading="syncing"
            :disabled="!selectedAccountId"
            @click="syncOrders"
          >
            <el-icon><Refresh /></el-icon> 同步订单
          </el-button>
        </div>
      </div>

      <!-- Tabs + 表格 -->
      <el-tabs v-model="activeTab" class="order-tabs" @tab-change="onTabChange">
        <el-tab-pane label="我卖出的" name="SOLD" />
        <el-tab-pane label="我买到的" name="BOUGHT" />
        <el-tab-pane label="全部" name="ALL" />
      </el-tabs>

      <el-table :data="orders" stripe v-loading="loading" class="orders-table">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="orderId" label="订单号" width="180" />
        <el-table-column prop="itemId" label="商品ID" width="150" show-overflow-tooltip />
        <el-table-column prop="itemTitle" label="商品标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="关联商品" width="130">
          <template #default="{ row }">
            <el-tag v-if="row.productId" size="small" :type="row.goodsType === 'VIRTUAL' ? 'warning' : 'info'">
              #{{ row.productId }} · {{ row.goodsType || 'PHYSICAL' }}
            </el-tag>
            <span v-else style="color: #c0c4cc;">未关联</span>
          </template>
        </el-table-column>
        <el-table-column :label="activeTab === 'ALL' ? '对手方' : (activeTab === 'SOLD' ? '买家' : '卖家')" width="140">
          <template #default="{ row }">
            <span>{{ row.counterpartyName || '—' }}</span>
            <span v-if="activeTab === 'ALL'" style="color: #c0c4cc; font-size: 12px; margin-left: 4px;">（{{ row.type === 'SOLD' ? '买家' : '卖家' }}）</span>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="100">
          <template #default="{ row }">¥{{ row.amount || '0.00' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">
              {{ statusLabel(row.status, row.tradeStatusEnum) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下单时间" width="180">
          <template #default="{ row }">
            <span>{{ formatTime(row.orderTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="activeTab !== 'BOUGHT'" prop="trackingNo" label="物流单号" width="150" />
        <el-table-column v-if="activeTab !== 'BOUGHT'" label="虚拟发货" width="200">
          <template #default="{ row }">
            <span v-if="!row.requireVirtualShip && row.goodsType !== 'VIRTUAL'" style="color: #c0c4cc;">—</span>
            <span v-else>
              <el-tag :type="virtualShipTagType(row)" size="small">{{ virtualShipLabel(row) }}</el-tag>
              <el-tag v-if="row.virtualShipTaskStatus" :type="shipTaskTagType(row.virtualShipTaskStatus)" size="small" style="margin-left: 4px;">
                {{ shipTaskLabel(row.virtualShipTaskStatus) }}
              </el-tag>
              <div v-if="row.virtualShipTaskError" style="font-size: 11px; color: #F56C6C; margin-top: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 100%;" :title="row.virtualShipTaskError">
                {{ row.virtualShipTaskError }}
              </div>
              <div v-else-if="row.virtualShipExecuteAt && !row.virtualShippedAt" style="font-size: 11px; color: var(--text-3); margin-top: 2px;">
                计划：{{ formatTime(row.virtualShipExecuteAt) }}
              </div>
              <div v-if="row.virtualShippedAt" style="font-size: 11px; color: var(--text-3); margin-top: 2px;">
                {{ formatTime(row.virtualShippedAt) }}
              </div>
            </span>
          </template>
        </el-table-column>
        <el-table-column v-if="activeTab !== 'BOUGHT'" prop="deliverContent" label="发货内容快照" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <!-- 卖家视角：卖出订单未闭环时始终显示发货入口 -->
            <template v-if="row.type === 'SOLD' && !isOrderClosed(row)">
              <el-button
                v-if="row.requireVirtualShip || row.goodsType === 'VIRTUAL'"
                size="small"
                type="primary"
                :loading="row._manualShipping"
                @click="manualShip(row)"
              >
                人工发货
              </el-button>
              <el-button
                v-else
                size="small"
                type="success"
                @click="deliver(row)"
              >
                发货
              </el-button>
            </template>
            <!-- 买家视角：买入订单跳闲鱼查看 -->
            <template v-else>
              <el-button
                v-if="row.orderDetailUrl"
                size="small"
                @click="openXianyu(row.orderDetailUrl)"
              >
                查看闲鱼
              </el-button>
            </template>
            <el-button size="small" @click="showDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div style="margin-top: 16px; display: flex; justify-content: flex-end;">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadOrders"
          @current-change="loadOrders"
        />
      </div>
    </el-card>

    <!-- 发货对话框 -->
    <el-dialog v-model="showDeliverDialog" title="发货" width="400px">
      <el-form :model="deliverForm" label-width="100px">
        <el-form-item label="物流单号">
          <el-input v-model="deliverForm.trackingNo" placeholder="输入快递单号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDeliverDialog = false">取消</el-button>
        <el-button type="primary" @click="handleDelivery">确认发货</el-button>
      </template>
    </el-dialog>

    <!-- 订单详情抽屉 -->
    <el-drawer v-model="showDetailDrawer" title="订单详情" size="620px" v-loading="detailLoading">
      <template v-if="detail">
        <div v-if="detail.order?.orderDetailUrl" style="margin-bottom: 12px;">
          <el-button size="small" @click="openXianyu(detail.order.orderDetailUrl)">在闲鱼查看原订单</el-button>
        </div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="订单ID">{{ detail.order?.id }}</el-descriptions-item>
          <el-descriptions-item label="订单号">{{ detail.order?.orderId }}</el-descriptions-item>
          <el-descriptions-item label="商品ID">{{ detail.order?.itemId || '—' }}</el-descriptions-item>
          <el-descriptions-item label="商品标题">{{ detail.order?.itemTitle || '—' }}</el-descriptions-item>
          <el-descriptions-item label="方向">{{ detail.order?.type === 'SOLD' ? '我卖出的' : '我买到的' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(detail.order?.status)" size="small">
              {{ statusLabel(detail.order?.status, detail.order?.tradeStatusEnum) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="金额">¥{{ detail.order?.amount ?? '0.00' }}</el-descriptions-item>
          <el-descriptions-item label="对手方">{{ detail.order?.counterpartyName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="下单时间">{{ formatTime(detail.order?.orderTime) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ formatTime(detail.order?.updatedAt) }}</el-descriptions-item>
          <el-descriptions-item label="物流单号">{{ detail.order?.trackingNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="虚拟发货">
            <template v-if="detail.order?.goodsType === 'VIRTUAL' || detail.order?.requireVirtualShip">
              <el-tag v-if="detail.order?.virtualShippedAt" type="success" size="small">已发货 {{ formatTime(detail.order?.virtualShippedAt) }}</el-tag>
              <el-tag v-else type="warning" size="small">待发货</el-tag>
            </template>
            <span v-else style="color: #c0c4cc;">—</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detailType === 'BOUGHT'" label="自动确认收货">
            {{ formatTime(detail.order?.autoReceiptAt) }}
          </el-descriptions-item>
        </el-descriptions>

        <!-- 关联商品 -->
        <div class="detail-section-title">关联商品</div>
        <template v-if="detail.product">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="商品ID">{{ detail.product.id }}</el-descriptions-item>
            <el-descriptions-item label="标题">{{ detail.product.title }}</el-descriptions-item>
            <el-descriptions-item label="类型">
              <el-tag size="small" :type="detail.product.goodsType === 'VIRTUAL' ? 'warning' : 'info'">
                {{ detail.product.goodsType === 'VIRTUAL' ? '虚拟' : '实物' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="发货类型">{{ deliverTypeLabel(detail.product.deliverType) }}</el-descriptions-item>
            <el-descriptions-item label="售价">¥{{ detail.product.price }}</el-descriptions-item>
            <el-descriptions-item label="库存">{{ detail.product.stock }}</el-descriptions-item>
            <el-descriptions-item label="商品状态">{{ productStatusLabel(detail.product.status) }}</el-descriptions-item>
            <el-descriptions-item label="发货内容模板" :span="2">
              <div style="white-space: pre-wrap; word-break: break-all; font-family: monospace; font-size: 12px;">
                {{ detail.product.deliverContentTemplate || '—' }}
              </div>
            </el-descriptions-item>
          </el-descriptions>
        </template>
        <el-empty v-else description="未关联本地商品" :image-size="60" />

        <!-- 自动发货记录（仅卖家订单展示：记录的是本店发货任务） -->
        <template v-if="detailType === 'SOLD'">
          <div class="detail-section-title">自动发货记录</div>
          <template v-if="detail.shipTasks && detail.shipTasks.length">
            <el-table :data="detail.shipTasks" size="small" border>
              <el-table-column prop="id" label="任务ID" width="70" />
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag size="small" :type="shipTaskTagType(row.status)">{{ shipTaskLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="创建时间" width="150">
                <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column label="执行时间" width="150">
                <template #default="{ row }">{{ formatTime(row.processedAt) }}</template>
              </el-table-column>
              <el-table-column label="错误信息" min-width="150" show-overflow-tooltip>
                <template #default="{ row }">
                  <span style="color: #F56C6C;">{{ row.errorMessage || '—' }}</span>
                </template>
              </el-table-column>
            </el-table>
          </template>
          <el-empty v-else description="暂无自动发货记录" :image-size="60" />
        </template>
        <!-- 买家视角：收货信息 -->
        <template v-else>
          <div class="detail-section-title">收货信息</div>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="卖家">{{ detail.order?.counterpartyName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="物流单号">{{ detail.order?.trackingNo || '—' }}</el-descriptions-item>
            <el-descriptions-item label="自动确认收货时间">{{ formatTime(detail.order?.autoReceiptAt) }}</el-descriptions-item>
          </el-descriptions>
        </template>

        <!-- 物流轨迹（预留） -->
        <div class="detail-section-title">物流轨迹</div>
        <template v-if="detail.order?.trackingNo">
          <el-timeline>
            <el-timeline-item timestamp="已发货">
              物流单号：{{ detail.order.trackingNo }}（轨迹详情接口待接入）
            </el-timeline-item>
          </el-timeline>
        </template>
        <el-empty v-else description="暂无物流信息" :image-size="60" />
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import api from '@/api/request'
import { getOrderDetail } from '@/api/order'

// ===== 账号选择 =====
const accounts = ref([])
const accountsLoading = ref(false)
const selectedAccountId = ref(null)

// ===== 订单列表 =====
const orders = ref([])
const loading = ref(false)
const activeTab = ref('SOLD')
const page = ref(1)
const size = ref(20)
const total = ref(0)

// ===== 同步 =====
const syncing = ref(false)

// ===== 发货 =====
const showDeliverDialog = ref(false)
const deliverForm = ref({ orderId: null, trackingNo: '' })

// ===== 订单详情 =====
const showDetailDrawer = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
/** 当前详情订单方向（SOLD/BOUGHT），决定抽屉展示的信息区块 */
const detailType = ref('SOLD')

// 时间格式化
function formatTime(t) {
  if (!t) return '—'
  return t.replace('T', ' ').substring(0, 19)
}

// 状态标签
function statusLabel(status, tradeStatusEnum) {
  // 优先使用贸易枚举（更准确）
  if (tradeStatusEnum) {
    const enumMap = {
      'trade_success': '交易成功',
      'buyer_to_confirm': '买家待确认',
      'refund_success': '退款成功',
      'trade_refund': '退款中',
      'trade_in_audit': '退款审核中',
      'refund_agree': '同意退款',
      'refund_process': '退款处理中',
      'trade_closed': '交易关闭',
      'trade_cancelled': '已取消',
      'cancel': '已取消',
      'pending_pay': '待付款',
      'waiting_pay': '待付款',
      'trade_pending': '待付款',
      'trade_delivered': '已发货',
      'sent': '已发货',
      'paid': '已付款',
      'trade_paid': '已付款',
      'trade_suspended': '暂停'
    }
    return enumMap[tradeStatusEnum] || tradeStatusEnum
  }
  
  // 回退到标准化状态码
  return { 
    PENDING: '待付款', 
    PAID: '已付款', 
    SHIPPED: '已发货', 
    COMPLETED: '交易成功', 
    REFUNDING: '退款中', 
    REFUNDED: '退款成功', 
    CLOSED: '已关闭',
    BUYER_TO_CONFIRM: '买家待确认'
  }[status] || status
}

function statusTagType(status) {
  return { 
    PENDING: 'warning', 
    PAID: 'primary', 
    SHIPPED: 'info', 
    COMPLETED: 'success', 
    REFUNDING: 'danger', 
    REFUNDED: 'info', 
    CLOSED: '',
    BUYER_TO_CONFIRM: 'warning'
  }[status] || ''
}

// 虚拟发货状态展示
function virtualShipLabel(row) {
  if (row.requireVirtualShip && !row.virtualShippedAt) return '待发货'
  if (row.virtualShippedAt) return '已发货'
  if (row.goodsType === 'VIRTUAL') return '虚拟商品'
  return '—'
}
function virtualShipTagType(row) {
  if (row.requireVirtualShip && !row.virtualShippedAt) return 'warning'
  if (row.virtualShippedAt) return 'success'
  return 'info'
}
function shipTaskLabel(status) {
  return {
    PENDING: '任务待执行',
    PROCESSING: '执行中',
    SENT_PENDING_ACK: '已发出·待送达',
    SHIPPED: '任务已完成',
    SUCCESS: '已送达',
    FAILED: '任务失败',
    SKIPPED: '已跳过',
    RETRY_EXHAUSTED: '重试耗尽'
  }[status] || status
}
function shipTaskTagType(status) {
  return {
    PENDING: 'warning',
    PROCESSING: 'primary',
    SENT_PENDING_ACK: 'warning',
    SHIPPED: 'success',
    SUCCESS: 'success',
    FAILED: 'danger',
    SKIPPED: 'info',
    RETRY_EXHAUSTED: 'danger'
  }[status] || 'info'
}

// 加载账号列表
async function loadAccounts() {
  accountsLoading.value = true
  try {
    const res = await api.get('/accounts')
    if (res.success) {
      // /api/accounts 返回的是数组，不是分页对象
      const list = Array.isArray(res.data) ? res.data : (res.data?.records || [])
      accounts.value = list
      // 默认选中第一个
      if (accounts.value.length > 0 && !selectedAccountId.value) {
        selectedAccountId.value = accounts.value[0].id
      }
    }
  } catch (e) {}
  finally { accountsLoading.value = false }
}

// 加载订单列表
async function loadOrders() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (selectedAccountId.value) {
      params.accountId = selectedAccountId.value
    }
    if (activeTab.value !== 'ALL') {
      params.type = activeTab.value
    }
    const res = await api.get('/orders', { params })
    if (res.success) {
      orders.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch (e) {}
  finally { loading.value = false }
}

// 切换 tab 时重置分页到第 1 页，否则会停留在可能不存在的页码上导致列表空白
function onTabChange() {
  page.value = 1
  loadOrders()
}

// 同步订单
async function syncOrders() {
  if (!selectedAccountId.value) {
    ElMessage.warning('请先选择账号')
    return
  }
  syncing.value = true
  try {
    const res = await api.post(`/orders/accounts/${selectedAccountId.value}/sync`)
    if (res.success) {
      ElMessage.success(`同步完成：买到 ${res.data.boughtCount} 条，卖出 ${res.data.soldCount} 条`)
      await loadOrders()
    } else {
      ElMessage.error(res.message || '同步失败')
    }
  } catch (e) {
    ElMessage.error('同步请求失败')
  }
  finally { syncing.value = false }
}

// 是否已闭环（终态订单不再提供发货操作）：交易成功 / 已关闭 / 退款成功
function isOrderClosed(row) {
  return row.status === 'COMPLETED' || row.status === 'CLOSED' || row.status === 'REFUNDED'
}

// 发货
function deliver(row) {
  deliverForm.value = { orderId: row.id, trackingNo: '' }
  showDeliverDialog.value = true
}

// 打开闲鱼原订单链接（买家/卖家订单都可用）
function openXianyu(url) {
  if (!url) return
  window.open(url, '_blank')
}

async function handleDelivery() {
  if (!deliverForm.value.trackingNo) {
    ElMessage.warning('请输入物流单号')
    return
  }
  try {
    const res = await api.post(`/orders/${deliverForm.value.orderId}/delivery?trackingNo=${deliverForm.value.trackingNo}`)
    if (res.success) {
      ElMessage.success('发货成功')
      showDeliverDialog.value = false
      await loadOrders()
    }
  } catch (e) {}
}

// 人工发货：按订单创建/触发虚拟发货任务并立即执行（走自动发货链路）
async function manualShip(row) {
  if (!row.id) return ElMessage.warning('订单缺少 ID')
  row._manualShipping = true
  try {
    const res = await api.post('/virtual-ship/cards/send', { orderId: row.id })
    if (res.success) {
      ElMessage.success('已触发人工发货，发货结果稍后更新')
      await loadOrders()
    } else {
      ElMessage.error(res.message || '人工发货失败')
    }
  } catch (e) {
    ElMessage.error('人工发货失败：' + (e?.message || ''))
  } finally {
    row._manualShipping = false
  }
}

// 商品发货类型标签
function deliverTypeLabel(t) {
  return { CARD: '卡密', ACCOUNT: '账号', LINK: '链接', FILE: '网盘文件' }[t] || t || '—'
}

// 商品状态标签
function productStatusLabel(s) {
  return { ON_SALE: '在售', OFF_SALE: '已下架', DRAFT: '草稿', PENDING: '待发布' }[s] || s || '—'
}

// 订单详情：拉订单 + 商品 + 自动发货记录 + 物流
async function showDetail(row) {
  if (!row.id) return ElMessage.warning('订单缺少 ID')
  detailType.value = row.type === 'BOUGHT' ? 'BOUGHT' : 'SOLD'
  showDetailDrawer.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const res = await getOrderDetail(row.id)
    if (res.success) {
      detail.value = res.data || {}
    } else {
      ElMessage.error(res.message || '加载详情失败')
    }
  } catch (e) {
    ElMessage.error('加载详情失败：' + (e?.message || ''))
  } finally {
    detailLoading.value = false
  }
}

onMounted(async () => {
  await loadAccounts()
  await loadOrders()
})
</script>

<style scoped>
.order-tabs :deep(.el-tabs__item) {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-2);
  transition: all 0.2s ease;
}
.order-tabs :deep(.el-tabs__item.is-active) {
  color: var(--brand);
}
.orders-table {
  margin-top: 16px;
}
.toolbar-right {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
}
.page-toolbar {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  margin-bottom: 16px; flex-wrap: wrap;
}
.page-toolbar .toolbar-left {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
}
.page-toolbar .toolbar-right {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
}
.card-head {
  display: flex; align-items: center; justify-content: space-between;
  gap: 14px; flex-wrap: wrap; margin-bottom: 16px;
}
.card-head-left {
  display: flex; align-items: center; gap: 14px; min-width: 0;
}
.card-chip {
  width: 44px; height: 44px; border-radius: 13px;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 20px; flex-shrink: 0;
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  box-shadow: 0 8px 18px rgba(79, 70, 229, 0.22);
}
.card-head-text {
  display: flex; flex-direction: column; gap: 3px; min-width: 0;
}
.card-title {
  font-size: 16px; font-weight: 600; color: var(--text-1);
}
.card-sub {
  font-size: 12px; color: var(--text-3);
}
.order-tabs :deep(.el-tabs__nav) {
  display: inline-flex;
}
</style>
