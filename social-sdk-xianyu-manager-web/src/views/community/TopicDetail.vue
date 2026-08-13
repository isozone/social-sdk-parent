<template>
  <div class="topic-detail-page">
    <div class="detail-nav">
      <el-button text @click="goBack">‹ 返回</el-button>
      <span class="nav-title">帖子详情</span>
    </div>

    <el-card v-if="topic.id" shadow="never" class="topic-card">
      <h2 class="t-title">{{ topic.title }}</h2>
      <div class="t-meta">
        <span class="t-author">{{ topic.user?.nickname || topic.author_name || topic.username || '社区用户' }}</span>
        <el-tag v-if="topic.price > 0" type="warning" size="small" effect="light">付费 {{ topic.price }} 币</el-tag>
        <span>浏览 {{ topic.view_count || 0 }}</span>
        <span>回复 {{ topic.reply_count || 0 }}</span>
        <span>{{ formatDateTime(topic.created_at) }}</span>
      </div>
      <div v-if="topic.category?.name" class="t-cat">分类：{{ topic.category.name }}</div>
      <div class="t-content" v-html="safeHtml(mdToHtml(topic.content || topic.summary || ''))"></div>
      <div v-if="topicTags.length" class="t-tags">
        <el-tag v-for="tag in topicTags" :key="tag" size="small" effect="plain" type="info">{{ tag }}</el-tag>
      </div>
      <div class="t-actions">
        <el-button :type="topic.liked ? 'danger' : 'default'" size="small" @click="react('like')">{{ topic.liked ? '已赞' : '点赞' }} {{ topic.like_count || 0 }}</el-button>
        <el-button :type="topic.favored ? 'warning' : 'default'" size="small" @click="toggleFavorite">{{ topic.favored ? '已收藏' : '收藏' }} {{ topic.favorite_count || 0 }}</el-button>
        <el-button v-if="topic.price > 0 && !topic.purchased" size="small" type="warning" @click="purchase">购买全文 {{ topic.price }} 币</el-button>
      </div>
    </el-card>
    <el-empty v-else-if="!loading" description="帖子不存在或已下架" />

    <el-card shadow="never" class="replies-card">
      <template #header>{{ topic.reply_count || replies.length || 0 }} 条回复</template>
      <div v-if="!replies.length" class="empty-box">暂无回复，来抢沙发</div>
      <div v-for="r in replies" :key="r.id" class="reply-item">
        <div class="r-head">
          <strong>{{ r.user?.nickname || r.author_name || r.username || '社区用户' }}</strong>
          <span class="r-time">{{ formatDateTime(r.created_at) }}</span>
        </div>
        <div class="r-body" v-html="safeHtml(mdToHtml(r.content || ''))"></div>
        <div class="r-like" @click="likeReply(r)">👍 {{ r.like_count || 0 }}</div>
      </div>
      <div class="reply-box">
        <el-input v-model="replyText" type="textarea" :rows="3" placeholder="写下你的回复…" />
        <div class="reply-send"><el-button type="primary" :loading="submitting" @click="sendReply">回复</el-button></div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { communityGet, communityPost } from '@/api/vip';
import { mdToHtml } from '@/utils/markdown';

const route = useRoute();
const router = useRouter();
const topicId = computed(() => Number(route.params.id || 0));
const topic = ref({});
const replies = ref([]);
const loading = ref(false);
const submitting = ref(false);
const replyText = ref('');

function safeHtml(html) {
  return String(html || '')
    .replace(/<script[\s\S]*?>[\s\S]*?<\/script>/gi, '')
    .replace(/<style[\s\S]*?>[\s\S]*?<\/style>/gi, '')
    .replace(/<iframe[\s\S]*?>[\s\S]*?<\/iframe>/gi, '')
    .replace(/<object[\s\S]*?>[\s\S]*?<\/object>/gi, '')
    .replace(/<embed[\s\S]*?>[\s\S]*?<\/embed>/gi, '')
    .replace(/<link[\s\S]*?>/gi, '')
    .replace(/<meta[\s\S]*?>/gi, '')
    .replace(/\son\w+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi, '')
    .replace(/(javascript|vbscript|data):/gi, '$1&#58;');
}
function formatDateTime(v) {
  if (!v) return '';
  const d = new Date(String(v).replace('Z', '').replace(/([+-]\d{2}:?\d{2})$/, ''));
  if (isNaN(d.getTime())) return '';
  const p = n => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}
function normalize(res) { return res?.data || res || {}; }
function pickList(data) { return Array.isArray(data) ? data : (data?.items || data?.list || data?.records || []); }
const topicTags = computed(() => {
  const val = topic.value.tags;
  if (!val) return [];
  return Array.isArray(val) ? val : String(val).split(',').map(s => s.trim()).filter(Boolean);
});

async function load() {
  loading.value = true;
  try {
    topic.value = normalize(await communityGet(`/topics/${topicId.value}`)) || {};
  } catch (e) { ElMessage.error(e.message || '加载失败'); }
  try {
    replies.value = pickList(normalize(await communityGet(`/topics/${topicId.value}/replies`)));
  } catch (e) { /* ignore */ }
  loading.value = false;
}
async function react(type) {
  try {
    const res = normalize(await communityPost(`/reactions/topic/${topicId.value}`, { reaction_type: type }));
    topic.value.liked = !!res.liked;
    topic.value.like_count = Math.max(0, (topic.value.like_count || 0) + (res.liked ? 1 : -1));
  } catch (e) { ElMessage.error(e.message || '操作失败'); }
}
async function toggleFavorite() {
  try {
    await communityPost(`/topics/${topicId.value}/favorite`, {});
    topic.value.favored = !topic.value.favored;
    ElMessage.success('已更新收藏');
  } catch (e) { ElMessage.error(e.message || '操作失败'); }
}
async function purchase() {
  try { await communityPost(`/topics/${topicId.value}/purchase`, {}); topic.value.purchased = true; ElMessage.success('购买成功'); }
  catch (e) { ElMessage.error(e.message || '购买失败'); }
}
async function likeReply(r) {
  try { await communityPost(`/reactions/reply/${r.id}`, { reaction_type: 'like' }); r.like_count = (r.like_count || 0) + 1; }
  catch (e) { /* ignore */ }
}
async function sendReply() {
  const text = (replyText.value || '').trim();
  if (!text) { ElMessage.warning('请输入回复内容'); return; }
  submitting.value = true;
  try {
    await communityPost(`/topics/${topicId.value}/replies`, { content: text, content_format: 'markdown' });
    replyText.value = '';
    ElMessage.success('回复成功');
    await load();
  } catch (e) { ElMessage.error(e.message || '回复失败'); }
  finally { submitting.value = false; }
}
function goBack() { router.back(); }

load();
</script>

<style scoped>
.topic-detail-page { max-width: 860px; margin: 0 auto; padding: 16px; }
.detail-nav { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.nav-title { font-weight: 700; }
.t-title { margin: 0 0 8px; }
.t-meta { display: flex; align-items: center; gap: 12px; color: #888; font-size: 13px; margin-bottom: 6px; flex-wrap: wrap; }
.t-author { font-weight: 600; color: #333; }
.t-cat { color: #999; font-size: 12px; margin-bottom: 8px; }
.t-content { line-height: 1.75; word-break: break-word; }
.t-content :deep(img) { max-width: 100%; border-radius: 6px; }
.t-content :deep(pre) { background: #f6f8fa; padding: 12px; border-radius: 6px; overflow-x: auto; }
.t-content :deep(code) { background: #f0f0f0; padding: 1px 5px; border-radius: 3px; font-size: 13px; }
.t-tags { margin-top: 12px; display: flex; gap: 6px; flex-wrap: wrap; }
.t-actions { margin-top: 14px; display: flex; gap: 8px; flex-wrap: wrap; }
.replies-card { margin-top: 16px; }
.empty-box { color: #999; text-align: center; padding: 20px 0; }
.reply-item { border-bottom: 1px solid #f0f0f0; padding: 10px 0; }
.r-head { display: flex; align-items: center; gap: 10px; margin-bottom: 4px; }
.r-time { color: #aaa; font-size: 12px; }
.r-body { line-height: 1.7; word-break: break-word; }
.r-body :deep(img) { max-width: 100%; border-radius: 6px; }
.r-like { color: #999; font-size: 12px; margin-top: 6px; cursor: pointer; }
.reply-box { margin-top: 14px; }
.reply-send { display: flex; justify-content: flex-end; margin-top: 8px; }
</style>
