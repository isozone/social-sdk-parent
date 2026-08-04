<template>
  <div class="page-root">
    <el-tabs v-model="activeTab" type="border-card" class="ship-tabs">
      <!-- ===== Tab 1：全局配置 ===== -->
      <el-tab-pane label="全局配置" name="config">
        <el-card shadow="never" style="margin: 0;">
          <div class="card-head">
            <div class="card-head-left">
              <div class="card-chip chip-violet"><el-icon><Setting /></el-icon></div>
              <div class="card-head-text">
                <div class="card-title">虚拟发货全局配置</div>
                <div class="card-sub">控制自动发货、延迟策略、确认收货与通知规则</div>
              </div>
            </div>
            <div class="card-head-right">
              <el-button type="primary" size="small" @click="saveConfig" :loading="configLoading">
                <el-icon><Check /></el-icon>保存配置
              </el-button>
            </div>
          </div>

          <el-form :model="configForm" label-width="160px" class="config-form">
            <el-form-item label="启用自动发货">
              <el-switch v-model="configForm.enabled" />
            </el-form-item>
            <el-form-item label="发货延迟(秒)">
              <el-input-number v-model="configForm.delaySeconds" :min="0" />
              <span style="color: var(--text-3); font-size: 12px; margin-left: 8px;">支付成功后延时发货（防风控）</span>
            </el-form-item>
            <el-form-item label="自动确认收货">
              <span style="color: var(--text-3); font-size: 12px;">
                订单在
                <el-input-number v-model="configForm.autoConfirmDays" :min="1" :max="30" size="small" style="width: 90px; margin: 0 8px;" />
                天后自动确认收货
              </span>
            </el-form-item>
            <el-form-item label="发货后通知">
              <el-switch v-model="configForm.notifyAfterShip" />
              <span style="color: var(--text-3); font-size: 12px; margin-left: 8px;">发货后站内通知运营</span>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <!-- ===== Tab 2：商品发货配置 ===== -->
      <el-tab-pane label="商品发货配置" name="product">
        <el-card shadow="never" style="margin: 0;">
          <div class="card-head">
            <div class="card-head-left">
              <div class="card-chip chip-cyan"><el-icon><ShoppingBag /></el-icon></div>
              <div class="card-head-text">
                <div class="card-title">商品虚拟发货配置</div>
                <div class="card-sub">管理商品发货类型、内容模板与虚拟商品切换</div>
              </div>
            </div>
            <div class="card-head-right">
              <el-button size="small" @click="loadProductList" :loading="productLoading">
                <el-icon><RefreshRight /></el-icon>刷新
              </el-button>
            </div>
          </div>
          <el-alert type="info" :closable="false" class="tip-alert">
            列出全部商品。类型为「虚拟」的商品会进入虚拟发货链路，点击「配置」可修改发货方式。
            模板占位符：<b>${cardCode}</b> <b>${cardPassword}</b> <b>${link}</b> <b>${extractCode}</b> <b>${fileName}</b> <b>${itemTitle}</b> <b>${orderId}</b>
          </el-alert>
          <el-table :data="products" stripe v-loading="productLoading">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="title" label="商品标题" min-width="200" show-overflow-tooltip />
            <el-table-column label="商品类型" width="100">
              <template #default="{ row }">
                <el-tag :type="row.goodsType === 'VIRTUAL' ? 'warning' : 'info'">{{ row.goodsType === 'VIRTUAL' ? '虚拟' : '实物' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="发货类型" width="110">
              <template #default="{ row }">
                <el-tag :type="deliverTypeTag(row.deliverType)">{{ deliverTypeLabel(row.deliverType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="deliverContentTemplate" label="发货模板" min-width="200" show-overflow-tooltip />
            <el-table-column prop="stock" label="库存" width="80" />
            <el-table-column prop="status" label="状态" width="90" />
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="openProductConfig(row)">配置</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!productLoading && products.length === 0" description="暂无商品" />
        </el-card>
      </el-tab-pane>

      <!-- ===== Tab 3：卡密池 ===== -->
      <el-tab-pane label="卡密池" name="card">
        <el-card shadow="never" style="margin: 0;">
          <div class="card-head">
            <div class="card-head-left">
              <div class="card-chip chip-amber"><el-icon><Key /></el-icon></div>
              <div class="card-head-text">
                <div class="card-title">卡密池</div>
                <div class="card-sub">批量管理卡密/密码资源，用于虚拟商品自动发货</div>
              </div>
            </div>
            <div class="card-head-right">
              <el-button type="primary" size="small" @click="showAddCardDialog = true">
                <el-icon><Plus /></el-icon>批量添加卡密
              </el-button>
            </div>
          </div>

          <el-table :data="cards" stripe v-loading="cardLoading">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="cardContent" label="卡密内容" min-width="200" show-overflow-tooltip />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.used ? 'info' : 'success'">{{ row.used ? '已使用' : '可用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="添加时间" width="180">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="danger" @click="deleteCard(row.id)" :disabled="row.used">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="!cardLoading && cards.length === 0" description="暂无卡密" />
        </el-card>
      </el-tab-pane>

      <!-- ===== Tab 4：网盘文件 ===== -->
      <el-tab-pane label="网盘文件" name="file">
        <el-card shadow="never" style="margin: 0;">
          <div class="card-head">
            <div class="card-head-left">
              <div class="card-chip chip-green"><el-icon><FolderOpened /></el-icon></div>
              <div class="card-head-text">
                <div class="card-title">网盘文件（发货素材）</div>
                <div class="card-sub">上传本地素材，下单后自动创建分享链接发给买家</div>
              </div>
            </div>
            <div class="card-head-right">
              <el-button type="primary" size="small" @click="showUploadDialog = true">
                <el-icon><Upload /></el-icon>上传文件
              </el-button>
            </div>
          </div>

          <el-alert type="info" :closable="false" class="tip-alert">
            提示：买家下单后，系统自动从已上传文件中选择一个，创建网盘分享链接发给买家。
          </el-alert>

          <el-select v-model="fileFilterAccountId" placeholder="选择网盘账号" clearable style="width: 220px; margin-bottom: 12px;" @change="loadFiles">
            <el-option v-for="acc in storageAccounts" :key="acc.id" :label="`${providerLabel(acc.provider)} (${acc.uid || '-'})`" :value="acc.id" />
          </el-select>

          <el-table :data="files" stripe v-loading="fileLoading" style="margin-top: 12px;">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="fileName" label="文件名" min-width="200" />
            <el-table-column label="大小" width="100">
              <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag :type="statusType(row.uploadStatus)">{{ row.uploadStatus }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="上传时间" width="180">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="success" @click="testShare(row)" v-if="row.uploadStatus === 'COMPLETED'">测试分享</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="!fileLoading && files.length === 0" description="暂无文件" />
        </el-card>
      </el-tab-pane>

      <!-- ===== Tab 5：发货任务 ===== -->
      <el-tab-pane label="发货任务" name="task">
        <el-card shadow="never" style="margin: 0;">
          <div class="card-head">
            <div class="card-head-left">
              <div class="card-chip chip-slate"><el-icon><List /></el-icon></div>
              <div class="card-head-text">
                <div class="card-title">虚拟发货任务记录</div>
                <div class="card-sub">查看所有卡密 / 网盘发货任务的状态与执行明细</div>
              </div>
            </div>
          </div>

          <el-table :data="tasks" stripe v-loading="taskLoading">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="orderId" label="订单ID" width="120" />
            <el-table-column prop="accountId" label="账号ID" width="80" />
            <el-table-column label="发货类型" width="120">
              <template #default="{ row }">
                <el-tag :type="row.deliverType === 'FILE' ? 'warning' : 'primary'">
                  {{ row.deliverType === 'FILE' ? '网盘' : '卡密' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="taskStatusType(row.status)">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="deliverContent" label="发货内容" min-width="200" show-overflow-tooltip />
            <el-table-column prop="createdAt" label="创建时间" width="180">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="triggerShip(row)" v-if="row.status === 'PENDING'">手动发货</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="!taskLoading && tasks.length === 0" description="暂无发货任务" />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 商品虚拟发货配置弹窗 -->
    <el-dialog v-model="showProductConfigDialog" title="商品虚拟发货配置" width="640px">
      <el-form :model="productConfigForm" label-width="120px">
        <el-form-item label="商品">
          <span style="font-weight: 600;">{{ productConfigForm.title }}</span>
        </el-form-item>
        <el-form-item label="商品类型">
          <el-radio-group v-model="productConfigForm.goodsType">
            <el-radio-button value="VIRTUAL">虚拟商品</el-radio-button>
            <el-radio-button value="PHYSICAL">实物商品</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="发货类型" v-if="productConfigForm.goodsType === 'VIRTUAL'">
          <el-radio-group v-model="productConfigForm.deliverType" @change="onProductDeliverTypeChange">
            <el-radio-button value="CARD">卡密</el-radio-button>
            <el-radio-button value="ACCOUNT">账号</el-radio-button>
            <el-radio-button value="LINK">链接文本</el-radio-button>
            <el-radio-button value="FILE">网盘文件</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <!-- 动态发货内容表单：与本地商品新建弹窗同构，保存时组合成 JSON 存入 deliverContentTemplate -->
        <template v-if="productConfigForm.goodsType === 'VIRTUAL' && productConfigForm.deliverType">
          <el-form-item v-if="productConfigForm.deliverType === 'LINK'" label="发货链接">
            <el-input v-model="productDeliverForm.link" placeholder="https://pan.quark.cn/s/xxx（买家直接点击的下载链接）" />
          </el-form-item>
          <el-form-item v-if="productConfigForm.deliverType === 'CARD'" label="卡密列表">
            <el-input v-model="productDeliverForm.cardsText" type="textarea" :rows="5" placeholder="每行一条：卡号|密码（密码可省略）&#10;ABC123|pwd1&#10;DEF456" />
          </el-form-item>
          <el-form-item v-if="productConfigForm.deliverType === 'ACCOUNT'" label="账号列表">
            <el-input v-model="productDeliverForm.accountsText" type="textarea" :rows="5" placeholder="每行一条：账号|密码|服务器（服务器可省略）&#10;user1|pwd1|srv1" />
          </el-form-item>
          <el-form-item v-if="productConfigForm.deliverType === 'FILE'" label="文件路径">
            <el-input v-model="productDeliverForm.filePath" placeholder="/data/files/xxx.zip（本地文件路径，发布后自动上传网盘）" />
          </el-form-item>
          <el-form-item label="发货消息模板">
            <el-input v-model="productDeliverForm.message" type="textarea" :rows="3" :placeholder="productDeliverMessagePlaceholder" />
            <div style="color: var(--text-3); font-size: 12px; margin-top: 6px; line-height: 1.6;">{{ productDeliverMessageHint }}</div>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="showProductConfigDialog = false">取消</el-button>
        <el-button type="primary" @click="saveProductConfig" :loading="productConfigSaving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量添加卡密对话框 -->
    <el-dialog v-model="showAddCardDialog" title="批量添加卡密" width="500px">
      <el-alert type="info" :closable="false" style="margin-bottom: 12px;">每行一个卡密，如：XXXX-XXXX-XXXX-XXXX</el-alert>
      <el-input v-model="cardText" type="textarea" :rows="8" placeholder="粘贴卡密..." />
      <template #footer>
        <el-button @click="showAddCardDialog = false">取消</el-button>
        <el-button type="primary" @click="addCards">确定添加</el-button>
      </template>
    </el-dialog>

    <!-- 上传文件对话框 -->
    <el-dialog v-model="showUploadDialog" title="上传文件（网盘）" width="500px">
      <el-upload drag :auto-upload="false" :on-change="handleFileChange" :show-file-list="true" accept="*">
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处或点击上传</div>
      </el-upload>
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button type="primary" @click="uploadFile" :loading="uploading">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Upload, UploadFilled, Setting, Check, ShoppingBag, Key, FolderOpened, List, RefreshRight
} from '@element-plus/icons-vue'
import api from '@/api/request'
import {
  listVirtualShipTasks, getVirtualShipConfig, saveVirtualShipConfig,
  listVirtualCards, importVirtualCards, deleteVirtualCard,
  listStorageAccounts, listStorageFiles, shareStorageFile, uploadStorageFile,
  listForVirtualShip, saveProductVirtualShipConfig
} from '@/api/virtualShip'

const configForm = ref({
  accountId: null,
  enabled: true,
  delaySeconds: 30,
  autoConfirmDays: 7,
  notifyAfterShip: true
})
const activeTab = ref('config')
const configLoading = ref(false)

const accounts = ref([])
const loadAccounts = async () => {
  try { const r = await api.get('/accounts'); accounts.value = r.data || [] } catch {}
}
const loadConfig = async () => {
  if (!accounts.value.length) return
  try {
    const r = await getVirtualShipConfig(accounts.value[0].id)
    if (r.data) {
      configForm.value = {
        ...configForm.value,
        accountId: r.data.accountId,
        enabled: r.data.enabled,
        delaySeconds: r.data.delaySeconds,
        autoConfirmDays: r.data.autoConfirmDays,
        notifyAfterShip: r.data.notifyAfterShip
      }
    }
  } catch {}
}
const saveConfig = async () => {
  if (!accounts.value.length) return ElMessage.warning('请先添加账号')
  configLoading.value = true
  try {
    const payload = {
      accountId: accounts.value[0].id,
      enabled: configForm.value.enabled,
      delaySeconds: configForm.value.delaySeconds,
      autoConfirmDays: configForm.value.autoConfirmDays,
      notifyAfterShip: configForm.value.notifyAfterShip
    }
    await saveVirtualShipConfig(payload)
    ElMessage.success('配置已保存')
    await loadConfig()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '保存配置失败')
  } finally { configLoading.value = false }
}

const cards = ref([])
const cardLoading = ref(false)
const showAddCardDialog = ref(false)
const cardText = ref('')
const loadCards = async () => {
  cardLoading.value = true
  try { const r = await listVirtualCards(); cards.value = r.data || [] }
  finally { cardLoading.value = false }
}
const addCards = async () => {
  const list = cardText.value.split('\n').map(s => s.trim()).filter(Boolean)
  if (!list.length) return ElMessage.warning('请输入至少一个卡密')
  try {
    const res = await importVirtualCards({ productId: null, cards: list })
    if (res && res.success) {
      ElMessage.success(`已添加 ${list.length} 个卡密`)
      showAddCardDialog.value = false
      cardText.value = ''
      loadCards()
    } else {
      ElMessage.error((res && res.message) || '添加失败')
    }
  } catch (e) {
    ElMessage.error('添加失败：' + (e.message || ''))
  }
}
const deleteCard = async (id) => {
  try {
    await ElMessageBox.confirm('确认删除？', '提示', { type: 'warning' })
    const res = await deleteVirtualCard(id)
    if (res && res.success) {
      ElMessage.success('已删除')
      loadCards()
    } else {
      ElMessage.error((res && res.message) || '删除失败')
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败：' + (e.message || ''))
  }
}

const storageAccounts = ref([])
const files = ref([])
const fileLoading = ref(false)
const fileFilterAccountId = ref(null)
const showUploadDialog = ref(false)
const uploading = ref(false)
const selectedFile = ref(null)
const loadStorageAccounts = async () => {
  try { const r = await listStorageAccounts(); storageAccounts.value = r.data || [] } catch {}
}

const products = ref([])
const productLoading = ref(false)
const loadProductList = async () => {
  productLoading.value = true
  try {
    const r = await listForVirtualShip(accounts.value[0]?.id)
    products.value = r.data || []
  } catch (e) {
    ElMessage.error('加载商品列表失败')
  } finally { productLoading.value = false }
}

const showProductConfigDialog = ref(false)
const productConfigSaving = ref(false)
const productConfigForm = ref({
  id: null,
  title: '',
  goodsType: 'VIRTUAL',
  deliverType: 'CARD',
  deliverContentTemplate: ''
})

// 动态发货内容表单：与本地商品新建弹窗同构，保存时组合成 JSON 存入 deliverContentTemplate
const productDeliverForm = ref({
  link: '',
  cardsText: '',
  accountsText: '',
  filePath: '',
  message: ''
})

const onProductDeliverTypeChange = () => {
  // 切换发货类型时清空上次的字段，避免类型间串数据
  Object.assign(productDeliverForm.value, { link: '', cardsText: '', accountsText: '', filePath: '', message: '' })
}

const productDeliverMessagePlaceholder = computed(() => {
  const t = productConfigForm.value.deliverType
  if (t === 'CARD') return '卡号：${cardCode}\n密码：${cardPassword}（留空走默认格式）'
  if (t === 'ACCOUNT') return '账号：${account}\n密码：${password}\n服务器：${server}（留空走默认格式）'
  if (t === 'LINK') return '感谢购买【${itemTitle}】，下载链接：${link}\n订单号：${orderId}'
  if (t === 'FILE') return '下载链接：${link}\n提取码：${extractCode}\n有效期：7天'
  return ''
})

const productDeliverMessageHint = computed(() => {
  const t = productConfigForm.value.deliverType
  if (t === 'CARD') return '可用占位符：${cardCode} ${cardPassword}；每行一张卡密，格式 卡号|密码'
  if (t === 'ACCOUNT') return '可用占位符：${account} ${password} ${server}；每行一个账号，格式 账号|密码|服务器'
  if (t === 'LINK') return '可用占位符：${link} ${itemTitle} ${orderId}'
  if (t === 'FILE') return '可用占位符：${link} ${extractCode} ${fileName}；文件路径为服务器本地路径'
  return ''
})

// 把动态表单组合成 JSON 字符串（保存到 deliverContentTemplate）
function buildProductDeliverJson() {
  const t = productConfigForm.value.deliverType
  if (!t) return ''
  const f = productDeliverForm.value
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
function parseProductDeliverJson(str) {
  Object.assign(productDeliverForm.value, { link: '', cardsText: '', accountsText: '', filePath: '', message: '' })
  if (!str) return
  try {
    const obj = JSON.parse(str)
    if (obj && typeof obj === 'object' && !Array.isArray(obj) && obj.type) {
      productDeliverForm.value.link = obj.link || ''
      productDeliverForm.value.cardsText = Array.isArray(obj.cards) ? obj.cards.join('\n') : ''
      productDeliverForm.value.accountsText = Array.isArray(obj.accounts) ? obj.accounts.join('\n') : ''
      productDeliverForm.value.filePath = obj.filePath || ''
      productDeliverForm.value.message = obj.message || ''
      return
    }
  } catch { /* fallthrough */ }
  // 旧格式：整体当消息模板
  productDeliverForm.value.message = str
}

const deliverTypeLabel = t => ({ CARD: '卡密', ACCOUNT: '账号', LINK: '链接', FILE: '网盘' }[t] || '-')
const deliverTypeTag = t => ({ CARD: 'primary', ACCOUNT: 'primary', LINK: 'success', FILE: 'warning' }[t] || 'info')

const openProductConfig = (row) => {
  productConfigForm.value = {
    id: row.id,
    title: row.title,
    goodsType: row.goodsType || 'PHYSICAL',
    deliverType: row.deliverType || 'CARD',
    deliverContentTemplate: row.deliverContentTemplate || ''
  }
  parseProductDeliverJson(row.deliverContentTemplate || '')
  showProductConfigDialog.value = true
}

const saveProductConfig = async () => {
  productConfigSaving.value = true
  try {
    const isVirtual = productConfigForm.value.goodsType === 'VIRTUAL'
    const t = productConfigForm.value.deliverType
    if (isVirtual && t === 'CARD' && !productDeliverForm.value.cardsText.trim()) return ElMessage.warning('请填写卡密列表（每行一条）')
    if (isVirtual && t === 'ACCOUNT' && !productDeliverForm.value.accountsText.trim()) return ElMessage.warning('请填写账号列表（每行一个）')
    if (isVirtual && t === 'LINK' && !productDeliverForm.value.link.trim()) return ElMessage.warning('请填写发货链接')
    if (isVirtual && t === 'FILE' && !productDeliverForm.value.filePath.trim()) return ElMessage.warning('请填写文件路径')
    await saveProductVirtualShipConfig(productConfigForm.value.id, {
      goodsType: productConfigForm.value.goodsType,
      deliverType: isVirtual ? productConfigForm.value.deliverType : null,
      deliverContentTemplate: isVirtual ? buildProductDeliverJson() : null
    })
    ElMessage.success('配置已保存')
    showProductConfigDialog.value = false
    await loadProductList()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '保存配置失败')
  } finally { productConfigSaving.value = false }
}
const loadFiles = async () => {
  if (!fileFilterAccountId.value) { files.value = []; return }
  fileLoading.value = true
  try { const r = await listStorageFiles(fileFilterAccountId.value); files.value = r.data || [] }
  finally { fileLoading.value = false }
}
const handleFileChange = f => { selectedFile.value = f.raw }
const uploadFile = async () => {
  if (!selectedFile.value || !fileFilterAccountId.value) return ElMessage.warning('请选择账号和文件')
  uploading.value = true
  try {
    await uploadStorageFile(fileFilterAccountId.value, selectedFile.value)
    ElMessage.success('上传成功')
    showUploadDialog.value = false
    loadFiles()
  } catch { ElMessage.error('上传失败') }
  finally { uploading.value = false }
}
const testShare = async (row) => {
  const r = await shareStorageFile(row.id)
  ElMessage.success(`分享链接: ${r.data}`)
  loadFiles()
}

const tasks = ref([])
const taskLoading = ref(false)
const loadTasks = async () => {
  taskLoading.value = true
  try { const r = await listVirtualShipTasks({ size: 50 }); tasks.value = r.data?.records || r.data || [] }
  finally { taskLoading.value = false }
}
const triggerShip = async (row) => {
  try {
    await api.post(`/virtual-ship/tasks/${row.id}/trigger`)
    ElMessage.success('发货已触发')
    loadTasks()
  } catch {}
}

const formatTime = t => t ? new Date(t).toLocaleString('zh-CN') : '-'
const formatSize = b => {
  if (!b) return '0 B'
  const u = ['B','KB','MB','GB']; let i = 0
  while (b >= 1024 && i < u.length-1) { b /= 1024; i++ }
  return `${b.toFixed(1)} ${u[i]}`
}
const providerLabel = p => ({ BAIDU_NETDISK:'百度网盘', QUARK_NETDISK:'夸克网盘', ALIYUN_DRIVE:'阿里云盘' }[p] || p)
const statusType = s => ({ COMPLETED:'success', UPLOADING:'warning', FAILED:'danger', PENDING:'info' }[s] || 'info')
const taskStatusType = s => ({ SUCCESS:'success', SHIPPED:'success', FAILED:'error', PENDING:'warning' }[s] || 'info')

onMounted(() => {
  loadAccounts().then(loadConfig)
  loadCards()
  loadStorageAccounts()
  loadTasks()
  loadProductList()
})
</script>

<style scoped>
.ship-tabs {
  padding: 0;
}
.config-form :deep(.el-form-item) {
  margin-bottom: 20px;
}
.tip-alert {
  margin-bottom: 16px;
}
</style>
