<template>
  <div class="community-page">
    <div class="hero-card">
      <div>
        <div class="eyebrow">I 社区工作区</div>
        <h2>{{ pageTitle }}</h2>
        <p>一比一接入 I 社区客户端主功能：圈子、发帖、回复、收藏、钱包、充值、订单、草稿、购买、兑换、通知、排行榜。</p>
      </div>
      <el-space>
        <el-tag :type="vipStatus.active ? 'success' : 'warning'" size="large">{{ vipStatus.active ? 'VIP 已解锁' : '待解锁/已到期' }}</el-tag>
        <el-button @click="reloadCurrent">刷新</el-button>
      </el-space>
    </div>

    <template v-if="section === 'home'">
      <el-row :gutter="16" class="section-row">
        <el-col :xs="24" :md="8"><metric-card title="我的身份" :value="vipStatus.communityUid || profile.community_uid || '未分配'" desc="按真实支付渠道分配：ALIX / WXX / UX / MANX" /></el-col>
        <el-col :xs="24" :md="8"><metric-card title="我的权益" :value="vipStatus.vipLevel || 'free'" :desc="`到期时间：${vipStatus.expiredAt || '暂无'}`" /></el-col>
        <el-col :xs="24" :md="8"><metric-card title="社区钱包" :value="wallet.balance ?? wallet.coins ?? 0" desc="可用于购买、打赏、兑换与资源消费" /></el-col>
      </el-row>
      <el-card shadow="never" class="feature-card">
        <template #header>客户端功能闭环</template>
        <el-row :gutter="12">
          <el-col v-for="item in features" :key="item.title" :xs="24" :sm="12" :md="6">
            <div class="feature-item" @click="$router.push(item.path)"><strong>{{ item.title }}</strong><span>{{ item.desc }}</span></div>
          </el-col>
        </el-row>
      </el-card>
    </template>

    <template v-else-if="section === 'topics'">
      <list-card title="帖子广场" :loading="loading" :empty="topics.length === 0">
        <template #actions>
          <el-input v-model="topicQuery.keyword" placeholder="搜索帖子" clearable style="width:220px" @keyup.enter="loadTopics" />
          <el-button type="primary" @click="$router.push('/app/community/composer')">发布帖子</el-button>
        </template>
        <div v-for="topic in topics" :key="topic.id" class="topic-item" @click="openTopic(topic)">
          <div class="topic-title">{{ topic.title }}</div>
          <div class="topic-meta"><span>{{ topic.author_name || topic.username || '社区用户' }}</span><span>浏览 {{ topic.view_count || 0 }}</span><span>回复 {{ topic.reply_count || 0 }}</span><span>点赞 {{ topic.like_count || 0 }}</span></div>
          <div class="topic-summary">{{ topic.summary || stripHtml(topic.content || '') }}</div>
          <el-space class="topic-actions" @click.stop>
            <el-button size="small" @click="toggleFavorite(topic)">收藏</el-button>
            <el-button size="small" @click="react(topic, 'like')">点赞</el-button>
            <el-button v-if="topic.price > 0" size="small" type="warning" @click="purchaseTopic(topic)">购买 {{ topic.price }}</el-button>
          </el-space>
        </div>
      </list-card>
    </template>

    <template v-else-if="section === 'composer'">
      <el-card shadow="never">
        <template #header>发布帖子</template>
        <el-form label-position="top">
          <el-form-item label="标题"><el-input v-model="composer.title" maxlength="120" show-word-limit placeholder="请输入帖子标题" /></el-form-item>
          <el-form-item label="内容"><el-input v-model="composer.content" type="textarea" :rows="10" placeholder="分享教程、经验、问题或资源" /></el-form-item>
          <el-row :gutter="12">
            <el-col :xs="24" :md="8"><el-form-item label="圈子 ID"><el-input-number v-model="composer.circle_id" :min="0" style="width:100%" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="分类 ID"><el-input-number v-model="composer.category_id" :min="0" style="width:100%" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="售价社区币"><el-input-number v-model="composer.price" :min="0" style="width:100%" /></el-form-item></el-col>
          </el-row>
          <el-form-item label="标签"><el-input v-model="composer.tagsText" placeholder="多个标签用逗号分隔" /></el-form-item>
          <el-space><el-button type="primary" :loading="submitting" @click="submitTopic">发布</el-button><el-button :loading="submitting" @click="saveDraft">保存草稿</el-button></el-space>
        </el-form>
      </el-card>
    </template>

    <template v-else-if="section === 'wallet'">
      <el-row :gutter="16">
        <el-col :xs="24" :md="8"><metric-card title="社区钱包" :value="wallet.balance ?? wallet.coins ?? 0" :desc="`积分：${wallet.points ?? 0}`" /></el-col>
        <el-col :xs="24" :md="16">
          <el-card shadow="never"><template #header>充值套餐</template>
            <el-space wrap><el-card v-for="plan in rechargePlans" :key="plan.id" shadow="hover" class="recharge-plan"><div class="vip-plan-title">{{ plan.name }}</div><div class="vip-plan-price">¥{{ formatCents(plan.price_cents) }}</div><div class="muted">{{ plan.coins }} 社区币</div><el-button size="small" type="primary" @click="createCreditOrder(plan)">创建订单</el-button></el-card></el-space>
          </el-card>
        </el-col>
      </el-row>
      <data-table title="钱包流水" :data="transactions" :columns="txColumns" />
    </template>

    <template v-else-if="section === 'orders'"><data-table title="支付订单" :data="orders" :columns="orderColumns" /></template>
    <template v-else-if="section === 'circles'"><simple-list title="社区圈子" :items="circles" name-key="name" desc-key="description" @reload="loadCircles" /></template>
    <template v-else-if="section === 'my-topics'"><simple-list title="我的帖子" :items="myTopics" name-key="title" desc-key="summary" @reload="loadMyTopics" /></template>
    <template v-else-if="section === 'favorites'"><simple-list title="我的收藏" :items="favorites" name-key="title" desc-key="summary" @reload="loadFavorites" /></template>
    <template v-else-if="section === 'drafts'"><simple-list title="草稿箱" :items="drafts" name-key="title" desc-key="content" @reload="loadDrafts" /></template>
    <template v-else-if="section === 'purchases'"><simple-list title="我的购买" :items="purchases" name-key="title" desc-key="summary" @reload="loadPurchases" /></template>
    <template v-else-if="section === 'exchange'">
      <simple-list title="兑换商城" :items="exchangeItems" name-key="name" desc-key="description" @reload="loadExchangeItems">
        <template #item-actions="{ item }"><el-button size="small" type="primary" @click="exchange(item)">兑换</el-button></template>
      </simple-list>
    </template>
    <template v-else-if="section === 'notifications'"><simple-list title="社区通知" :items="notifications" name-key="title" desc-key="content" @reload="loadNotifications" /></template>
    <template v-else-if="section === 'leaderboard'"><simple-list title="排行榜" :items="leaderboard" name-key="username" desc-key="score" @reload="loadLeaderboard" /></template>
    <template v-else-if="section === 'announcements'"><simple-list title="社区公告" :items="announcements" name-key="title" desc-key="content" @reload="loadAnnouncements" /></template>
    <template v-else-if="section === 'resources'"><simple-list title="资源中心" :items="resources" name-key="title" desc-key="description" @reload="loadResources" /></template>

    <template v-else-if="section === 'profile' || section === 'bindings' || section === 'benefits'">
      <el-row :gutter="16">
        <el-col :xs="24" :md="12"><el-card shadow="never"><template #header>社区资料</template><el-descriptions :column="1" border><el-descriptions-item label="社区 ID">{{ vipStatus.communityUid || profile.community_uid || '-' }}</el-descriptions-item><el-descriptions-item label="昵称">{{ profile.display_name || profile.nickname || '-' }}</el-descriptions-item><el-descriptions-item label="邮箱">{{ profile.email || '未绑定' }}</el-descriptions-item><el-descriptions-item label="微信">{{ profile.wechat_bound ? '已绑定' : '未绑定' }}</el-descriptions-item></el-descriptions></el-card></el-col>
        <el-col :xs="24" :md="12"><el-card shadow="never"><template #header>VIP 权益</template><el-descriptions :column="1" border><el-descriptions-item label="等级">{{ vipStatus.vipLevel || 'free' }}</el-descriptions-item><el-descriptions-item label="到期">{{ vipStatus.expiredAt || '-' }}</el-descriptions-item><el-descriptions-item label="功能">{{ JSON.stringify(vipStatus.features || []) }}</el-descriptions-item><el-descriptions-item label="限制">{{ JSON.stringify(vipStatus.limits || {}) }}</el-descriptions-item></el-descriptions></el-card></el-col>
      </el-row>
    </template>

    <template v-else><el-card shadow="never"><template #header>{{ pageTitle }}</template><div class="muted">该功能已纳入 I 社区客户端闭环。</div></el-card></template>

    <el-dialog v-model="topicDialogVisible" title="帖子详情" width="760px">
      <div v-if="currentTopic"><h3>{{ currentTopic.title }}</h3><div class="topic-meta"><span>{{ currentTopic.author_name || currentTopic.username }}</span><span>回复 {{ replies.length }}</span></div><div class="topic-content">{{ stripHtml(currentTopic.content || currentTopic.summary || '') }}</div><el-divider content-position="left">回复</el-divider><div v-for="reply in replies" :key="reply.id" class="reply-item">{{ reply.content }}</div><el-input v-model="replyText" type="textarea" :rows="3" placeholder="写下你的回复" /></div>
      <template #footer><el-button @click="topicDialogVisible = false">关闭</el-button><el-button type="primary" :loading="submitting" @click="submitReply">回复</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElButton, ElCard, ElEmpty, ElSkeleton, ElTable, ElTableColumn, ElMessage } from 'element-plus'
import { communityGet, communityPost, getVipStatus } from '@/api/vip'

const MetricCard = defineComponent({ props: ['title','value','desc'], setup: p => () => h(ElCard, { shadow:'never' }, { header: () => p.title, default: () => [h('div',{class:'identity-id'}, p.value), h('div',{class:'muted'}, p.desc)] }) })
const ListCard = defineComponent({ props: ['title','loading','empty'], setup: (p,{slots}) => () => h(ElCard,{shadow:'never'},{header:()=>h('div',{class:'card-header'},[h('span',p.title), h('div', slots.actions?.())]), default:()=>h(ElSkeleton,{loading:p.loading,animated:true,rows:5},{default:()=>p.empty?h('div',{class:'empty-box'},'暂无数据'):slots.default?.()})}) })
const DataTable = defineComponent({ props: ['title','data','columns'], setup: p => () => h(ElCard,{shadow:'never',class:'mt16'},{header:()=>p.title, default:()=>h(ElTable,{data:p.data||[],stripe:true},()=> (p.columns||[]).map(c=>h(ElTableColumn,c)))}) })
const SimpleList = defineComponent({ props: ['title','items','nameKey','descKey'], emits:['reload'], setup: (p,{emit,slots}) => () => h(ElCard,{shadow:'never'},{header:()=>h('div',{class:'card-header'},[h('span',p.title),h(ElButton,{onClick:()=>emit('reload')},()=> '刷新')]), default:()=> (p.items||[]).length===0?h(ElEmpty,{description:'暂无数据'}):h('div', (p.items||[]).map(item=>h('div',{class:'topic-item'},[h('div',{class:'topic-title'}, item[p.nameKey] || item.title || item.name || `#${item.id}`), h('div',{class:'topic-summary'}, String(item[p.descKey] || item.content || item.description || item.score || '')), h('div',{class:'topic-actions'}, slots['item-actions']?.({item}))])))}) })

const route = useRoute(); const router = useRouter(); const loading = ref(false); const submitting = ref(false)
const vipStatus = ref({ active:false, communityUid:'', vipLevel:'free' }); const profile = ref({}); const wallet = ref({})
const topics = ref([]); const replies = ref([]); const orders = ref([]); const transactions = ref([]); const rechargePlans = ref([])
const circles = ref([]); const myTopics = ref([]); const favorites = ref([]); const drafts = ref([]); const purchases = ref([]); const exchangeItems = ref([]); const notifications = ref([]); const leaderboard = ref([]); const announcements = ref([]); const resources = ref([])
const topicDialogVisible = ref(false); const currentTopic = ref(null); const replyText = ref(''); const topicQuery = ref({ keyword:'' })
const composer = ref({ title:'', content:'', circle_id:0, category_id:0, price:0, tagsText:'' })
const titleMap = { home:'社区首页', profile:'我的身份', benefits:'我的权益', bindings:'账户绑定', topics:'帖子广场', composer:'发布帖子', wallet:'社区钱包', orders:'支付订单', circles:'社区圈子', 'my-topics':'我的帖子', favorites:'我的收藏', drafts:'草稿箱', purchases:'我的购买', exchange:'兑换商城', notifications:'社区通知', leaderboard:'排行榜', announcements:'社区公告', resources:'资源中心', support:'工单支持' }
const section = computed(() => route.params.section || 'home'); const pageTitle = computed(() => titleMap[section.value] || '社区首页')
const features = [ { title:'帖子广场', desc:'浏览、搜索、查看、回复、收藏、点赞社区帖子', path:'/app/community/topics' }, { title:'发布帖子', desc:'发布教程、问题、资源，可设置售价和草稿', path:'/app/community/composer' }, { title:'社区钱包', desc:'查看余额、流水、充值套餐和订单', path:'/app/community/wallet' }, { title:'兑换/通知', desc:'兑换商城、排行榜、通知中心、我的内容', path:'/app/community/exchange' } ]
const txColumns = [{prop:'created_at',label:'时间',width:180},{prop:'currency',label:'币种',width:100},{prop:'direction',label:'方向',width:100},{prop:'amount',label:'数量',width:120},{prop:'remark',label:'备注'}]
const orderColumns = [{prop:'order_no',label:'订单号',minWidth:180},{prop:'plan_name',label:'套餐',minWidth:120},{prop:'pay_channel',label:'渠道',width:120},{prop:'pay_amount',label:'金额',width:120},{prop:'status',label:'状态',width:120},{prop:'created_at',label:'创建时间',minWidth:160}]

onMounted(reloadCurrent); watch(() => route.params.section, reloadCurrent)
async function reloadCurrent(){ await loadVipStatus(); const s=section.value; if(s==='home') await Promise.allSettled([loadProfile(),loadWallet()]); else if(s==='topics') await loadTopics(); else if(s==='wallet') await Promise.allSettled([loadWallet(),loadTransactions(),loadRechargePlans()]); else if(s==='orders') await loadOrders(); else if(['profile','bindings','benefits'].includes(s)) await loadProfile(); else if(s==='circles') await loadCircles(); else if(s==='my-topics') await loadMyTopics(); else if(s==='favorites') await loadFavorites(); else if(s==='drafts') await loadDrafts(); else if(s==='purchases') await loadPurchases(); else if(s==='exchange') await loadExchangeItems(); else if(s==='notifications') await loadNotifications(); else if(s==='leaderboard') await loadLeaderboard(); else if(s==='announcements') await loadAnnouncements(); else if(s==='resources') await loadResources() }
async function loadVipStatus(){ try{const r=await getVipStatus(); if(r.success) vipStatus.value=r.data||{}}catch(e){} }
async function loadProfile(){ try{profile.value=normalize(await communityGet('/profile'))}catch(e){} }
async function loadWallet(){ try{wallet.value=normalize(await communityGet('/wallet'))}catch(e){} }
async function loadTopics(){ loading.value=true; try{topics.value=pickList(normalize(await communityGet('/topics',{keyword:topicQuery.value.keyword})))}catch(e){ElMessage.error(e.message||'加载帖子失败')}finally{loading.value=false} }
async function loadOrders(){ try{orders.value=pickList(normalize(await communityGet('/credit-orders')))}catch(e){} }
async function loadTransactions(){ try{transactions.value=pickList(normalize(await communityGet('/wallet/transactions')))}catch(e){} }
async function loadRechargePlans(){ try{rechargePlans.value=pickList(normalize(await communityGet('/recharge/plans')))}catch(e){} }
async function loadCircles(){ try{circles.value=pickList(normalize(await communityGet('/circles')))}catch(e){} }
async function loadMyTopics(){ try{myTopics.value=pickList(normalize(await communityGet('/my/topics')))}catch(e){} }
async function loadFavorites(){ try{favorites.value=pickList(normalize(await communityGet('/my/favorites')))}catch(e){} }
async function loadDrafts(){ try{drafts.value=pickList(normalize(await communityGet('/my/drafts')))}catch(e){} }
async function loadPurchases(){ try{purchases.value=pickList(normalize(await communityGet('/my/purchases')))}catch(e){} }
async function loadExchangeItems(){ try{exchangeItems.value=pickList(normalize(await communityGet('/exchange/items')))}catch(e){} }
async function loadNotifications(){ try{notifications.value=pickList(normalize(await communityGet('/notifications')))}catch(e){} }
async function loadLeaderboard(){ try{leaderboard.value=pickList(normalize(await communityGet('/leaderboard')))}catch(e){} }
async function loadAnnouncements(){ try{announcements.value=pickList(normalize(await communityGet('/announcements')))}catch(e){} }
async function loadResources(){ resources.value=[{title:'Prompt 模板',description:'从 I 社区资源中心扩展同步'}, {title:'规则模板',description:'自动回复和运营规则模板'}, {title:'运营教程',description:'社区教程、玩法和案例'}] }
async function submitTopic(){ if(!composer.value.title||!composer.value.content){ElMessage.warning('标题和内容不能为空');return} submitting.value=true; try{await communityPost('/topics',{title:composer.value.title,content:composer.value.content,circle_id:composer.value.circle_id||0,category_id:composer.value.category_id||0,price:composer.value.price||0,tags:composer.value.tagsText?composer.value.tagsText.split(',').map(s=>s.trim()).filter(Boolean):[]}); ElMessage.success('发布成功'); router.push('/app/community/topics')}catch(e){ElMessage.error(e.message||'发布失败')}finally{submitting.value=false} }
async function saveDraft(){ submitting.value=true; try{await communityPost('/my/drafts',composer.value); ElMessage.success('草稿已保存')}catch(e){ElMessage.error(e.message||'保存失败')}finally{submitting.value=false} }
async function openTopic(topic){ currentTopic.value=topic; topicDialogVisible.value=true; try{currentTopic.value=normalize(await communityGet(`/topics/${topic.id}`))||topic}catch(e){} try{replies.value=pickList(normalize(await communityGet(`/topics/${topic.id}/replies`)))}catch(e){replies.value=[]} }
async function submitReply(){ if(!replyText.value||!currentTopic.value)return; submitting.value=true; try{await communityPost(`/topics/${currentTopic.value.id}/replies`,{content:replyText.value}); replyText.value=''; await openTopic(currentTopic.value); ElMessage.success('回复成功')}catch(e){ElMessage.error(e.message||'回复失败')}finally{submitting.value=false} }
async function toggleFavorite(t){ try{await communityPost(`/topics/${t.id}/favorite`,{}); ElMessage.success('收藏状态已更新')}catch(e){ElMessage.error(e.message||'操作失败')} }
async function react(t,type){ try{await communityPost(`/reactions/topic/${t.id}`,{reaction:type}); ElMessage.success('已互动')}catch(e){ElMessage.error(e.message||'操作失败')} }
async function purchaseTopic(t){ try{await communityPost(`/topics/${t.id}/purchase`,{}); ElMessage.success('购买成功')}catch(e){ElMessage.error(e.message||'购买失败')} }
async function createCreditOrder(plan){ try{const r=await communityPost('/credit-orders',{plan_id:plan.id,channel:'wechat'}); ElMessage.success('充值订单已创建'); router.push('/app/community/orders'); orders.value.unshift(normalize(r))}catch(e){ElMessage.error(e.message||'创建充值订单失败')} }
async function exchange(item){ try{await communityPost('/exchange',{item_id:item.id}); ElMessage.success('兑换成功')}catch(e){ElMessage.error(e.message||'兑换失败')} }
function normalize(res){ return res?.data || res || {} } function pickList(data){ return Array.isArray(data)?data:(data.items||data.list||data.records||data.topics||data.orders||data.transactions||data.plans||data.circles||data.notifications||[]) } function stripHtml(text){ return String(text||'').replace(/<[^>]+>/g,'').slice(0,180) } function formatCents(cents){ return (Number(cents||0)/100).toFixed(2) }
</script>

<style scoped>
.community-page{padding:2px}.hero-card{border-radius:18px;padding:24px;background:linear-gradient(135deg,#eef2ff,#fff7ed);display:flex;justify-content:space-between;gap:16px;align-items:center;margin-bottom:16px}.eyebrow{color:#7c3aed;font-weight:700;font-size:13px;margin-bottom:6px}h2{margin:0 0 8px;font-size:26px;color:#111827}p{margin:0;color:#6b7280}.section-row,.mt16{margin-top:16px;margin-bottom:16px}.identity-id{font-size:24px;font-weight:800;color:#111827;margin-bottom:8px}.muted{color:#6b7280;font-size:13px;line-height:1.6}.feature-card{border-radius:16px}.feature-item{border:1px solid #eef2ff;background:#fafafa;border-radius:12px;padding:14px;min-height:84px;display:flex;flex-direction:column;gap:8px;cursor:pointer;transition:all .2s}.feature-item:hover{border-color:#6366f1;transform:translateY(-1px)}.feature-item span{color:#6b7280;font-size:13px;line-height:1.5}.card-header{display:flex;align-items:center;justify-content:space-between;gap:12px}.empty-box{text-align:center;color:#9ca3af;padding:36px}.topic-item{padding:16px 0;border-bottom:1px solid #eef2f7;cursor:pointer}.topic-title{font-size:17px;font-weight:700;color:#111827;margin-bottom:8px}.topic-meta{display:flex;gap:12px;color:#9ca3af;font-size:12px;margin-bottom:8px}.topic-summary,.topic-content{color:#4b5563;line-height:1.7;white-space:pre-wrap}.topic-actions{margin-top:10px}.reply-item{padding:10px 0;border-bottom:1px dashed #e5e7eb;color:#374151}.recharge-plan{width:160px}.vip-plan-title{font-weight:700;color:#111827}.vip-plan-price{font-size:22px;font-weight:800;color:#ef4444;margin:8px 0 4px}
</style>
