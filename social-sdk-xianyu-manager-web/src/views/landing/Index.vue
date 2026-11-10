<template>
  <div class="landing">
    <!-- 动态背景：漂移光斑 + 扫描线 -->
    <div class="bg-blobs" aria-hidden="true">
      <span class="blob blob-a"></span>
      <span class="blob blob-b"></span>
      <span class="blob blob-c"></span>
    </div>

    <!-- 顶部导航：左 logo + 右登录按钮 -->
    <header class="nav">
      <div class="nav-inner">
        <router-link to="/" class="brand">
          <span class="logo-mark"><svg width="22" height="22" viewBox="0 0 24 24" fill="none"><rect width="24" height="24" rx="6" fill="url(#brandGrad)"/><path d="M7 8l5 4-5 4V8z" fill="#fff"/></svg></span>
          <span>AI鱼多宝</span>
        </router-link>
        <button class="login-btn" @click="goLogin"><span class="btn-shine"></span>登录控制台 &rsaquo;</button>
      </div>
    </header>

    <!-- Hero 大 banner：深空网格 + 霓虹标题 -->
    <section class="hero">
      <div class="hero-content">
        <div class="hero-badge anim" style="--d:0"><span class="badge-dot"></span>XIANVU · 智能运营平台</div>
        <h1>
          <span class="hero-line anim" style="--d:1">闲鱼多账号</span>
          <span class="hero-line hero-glow anim" style="--d:2">自动化运营平台</span>
        </h1>
        <p class="hero-sub anim" style="--d:3">从消息同步到自动发货，从风控解题到 AI 客服，一站式托管你的闲鱼生意</p>
        <div class="hero-cta anim" style="--d:4">
          <button class="cta-primary" @click="goLogin"><span class="btn-shine"></span>立即登录</button>
          <a class="cta-link" href="#features">了解功能 &rsaquo;</a>
        </div>
      </div>
    </section>

    <!-- 核心特性 -->
    <section id="features" class="features">
      <h2 class="section-title"><span class="title-diamond"></span>核心能力<span class="title-diamond"></span></h2>
      <div class="feature-grid">
        <div class="feature-card reveal" v-for="(f, i) in features" :key="f.title" :style="{ '--d': i % 3 }">
          <div class="feature-icon-wrap" :class="'icon-' + f.key">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" v-html="getFeatureIcon(f.key)"></svg>
          </div>
          <h3>{{ f.title }}</h3>
          <p>{{ f.desc }}</p>
        </div>
      </div>
    </section>

    <!-- 使用场景 -->
    <section class="scenes">
      <h2 class="section-title"><span class="title-diamond"></span>使用场景<span class="title-diamond"></span></h2>
      <div class="scene-grid">
        <div class="scene-card reveal" v-for="(s, i) in scenes" :key="s.title" :style="{ '--d': i % 4 }">
          <div class="scene-icon" :class="'icon-' + s.key">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" v-html="getSceneIcon(s.key)"></svg>
          </div>
          <div>
            <h3>{{ s.title }}</h3>
            <p>{{ s.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- 数据指标 -->
    <section class="stats">
      <div class="stats-inner">
        <div class="stat-item reveal" v-for="(s, i) in stats" :key="s.label" :style="{ '--d': i }">
          <div class="stat-num">{{ displayNums[i] ?? s.num }}</div>
          <div class="stat-label">{{ s.label }}</div>
        </div>
      </div>
    </section>

    <!-- 底部 CTA -->
    <section class="bottom-cta">
      <div class="bottom-cta-inner reveal">
        <h2>开始托管你的闲鱼生意</h2>
        <button class="cta-primary" @click="goLogin"><span class="btn-shine"></span>登录控制台</button>
      </div>
    </section>

    <!-- 页脚 -->
    <footer class="footer">
      <div class="footer-inner">
        <span>&copy; 2026 AI鱼多宝 &middot; Xianyu Manager</span>
        <div class="footer-links">
          <a href="/service">服务协议</a>
          <a href="/privacy">隐私政策</a>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ref, onMounted, onUnmounted } from 'vue'

const router = useRouter()
// 已登录直接进控制台，未登录才去登录页（避免已登录用户被强制重新登录）
const goLogin = () => {
  if (localStorage.getItem('token')) router.push('/app/dashboard')
  else router.push('/login')
}

/* ==================== 滚动浮现 + 数字滚动 ==================== */
const displayNums = ref(Array(4).fill(''))
let observer = null

function countUp(el, target) {
  // 解析前导数字（如 "20+"、"2min"、"80%"），无数字前缀直接展示
  const m = String(target).match(/^(\d+)(.*)$/)
  if (!m) { el.textContent = target; return }
  const end = parseInt(m[1], 10)
  const suffix = m[2]
  const t0 = performance.now()
  const dur = 1100
  function tick(t) {
    const p = Math.min((t - t0) / dur, 1)
    const eased = 1 - Math.pow(1 - p, 3)
    el.textContent = Math.round(end * eased) + suffix
    if (p < 1) requestAnimationFrame(tick)
  }
  requestAnimationFrame(tick)
}

onMounted(() => {
  observer = new IntersectionObserver((entries) => {
    for (const e of entries) {
      if (!e.isIntersecting) continue
      e.target.classList.add('in-view')
      // 统计区进入视口：一次性触发数字滚动
      if (e.target.classList.contains('stat-item')) {
        const idx = Array.from(e.target.parentNode.children).indexOf(e.target)
        if (displayNums.value[idx] === '') {
          countUp(e.target.querySelector('.stat-num'), stats[idx].num)
        }
      }
      observer.unobserve(e.target)
    }
  }, { threshold: 0.18 })
  document.querySelectorAll('.reveal').forEach(el => observer.observe(el))
})
onUnmounted(() => observer && observer.disconnect())


/* ==================== Feature Icons ==================== */
const featureIcons = {
  sync: `<path d="M17 1v4"/><path d="M19 4H5a2 2 0 0 1-2-2V1"/><path d="m2 10 3 3-3 3"/>`,
  ai: `<rect x="3" y="8" width="18" height="12" rx="3"/><circle cx="9" cy="13" r="1.5"/><circle cx="15" cy="13" r="1.5"/><path d="M12 3v5"/>`,
  ship: `<path d="M16 3h5v5"/><path d="M4 20 21 3 21 16 4 16Z"/><path d="m21 16-4 4-5-5"/>`,
  shield: `<path d="M12 2 4 6v6c0 5 3.5 9.7 8 11 4.5-1.3 8-6 8-11V6Z"/><path d="m9 12 2 2 4-4"/>`,
  shop: `<path d="M3 9l1-5h16l1 5"/><path d="M2 9h20"/><path d="M4 9v10a1 1 0 0 0 1 1h4v-8h6v8h4a1 1 0 0 0 1-1V9"/><path d="M9 19v-8h6v8"/>`,
  buyer: `<path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>`,
  notify: `<path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/>`,
  cloud: `<path d="M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z"/>`,
  monitor: `<rect x="2" y="3" width="20" height="14" rx="2"/><path d="M8 21h8"/><path d="M12 17v4"/><path d="M7 8l2 2 3-3 2 2 3-3" opacity="0.5"/>`
}

const sceneIcons = {
  multi: `<rect x="2" y="7" width="4" height="14" rx="1"/><rect x="10" y="3" width="4" height="18" rx="1"/><rect x="18" y="7" width="4" height="14" rx="1"/>`,
  auto: `<path d="M12 2v4"/><path d="m10 4 2 2 2-2"/><path d="M5 12h14"/><path d="M12 12v8"/><circle cx="12" cy="12" r="10"/>`,
  cost: `<path d="M12 2v20"/><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/>`,
  data: `<path d="M3 3v18h18"/><path d="m19 9-5 5-4-4-3 3"/>`
}

function getFeatureIcon(key) { return featureIcons[key] ?? '' }
function getSceneIcon(key) { return sceneIcons[key] ?? '' }

const features = [
  { key: 'sync', title: '消息自动同步', desc: '定时拉取闲鱼 IM 消息，风控滑块自动解题，多账号会话统一管理' },
  { key: 'ai', title: 'AI 自动客服', desc: '接入大模型自动回复买家咨询，知识库 + 多轮会话，人工兜底无缝切换' },
  { key: 'ship', title: '虚拟发货', desc: '卡密/网盘类商品支付后自动发货，定时扫描重试，自动确认收货' },
  { key: 'shield', title: '风控熔断', desc: '自动识别风控 punish，滑块 CDP 解题，失败自动熔断保护账号' },
  { key: 'shop', title: '商品运营', desc: '批量上下架、擦亮、市场情报、关键词趋势分析，数据驱动选品' },
  { key: 'buyer', title: '买家画像', desc: '从消息行为自动构建买家画像，砍价/成单/售后偏好一目了然' },
  { key: 'notify', title: '消息通知', desc: '新订单/退款/评价/异常多渠道推送，重试队列保证送达' },
  { key: 'cloud', title: '网盘存储', desc: '内置 OpenList 网盘，自动安装启停，分享链接一键生成' },
  { key: 'monitor', title: '监控审计', desc: '定时监控任务、操作审计日志、实时大屏，运营全程可追溯' }
]

const scenes = [
  { key: 'multi', title: '多店托管', desc: '一个控制台托管数十个闲鱼账号，消息统一收发，订单统一处理' },
  { key: 'auto', title: '虚拟商品自动化', desc: '卡密/网盘商品支付即发货，无需人工值守，7×24 自动运营' },
  { key: 'cost', title: 'AI 客服降本', desc: '常见咨询 AI 自动回复，复杂问题人工接管，客服成本降 80%' },
  { key: 'data', title: '数据驱动运营', desc: '市场情报 + 买家画像 + 订单趋势，用数据指导选品和定价' }
]

const stats = [
  { num: '20+', label: '业务模块闭环' },
  { num: '7x24', label: '自动运营托管' },
  { num: '80%', label: '客服成本下降' },
  { num: '2min', label: '订单同步延迟' }
]
</script>

<style scoped>
/* ==================== 动效 keyframes ==================== */
@keyframes fadeUp {
  from { opacity: 0; transform: translateY(22px); }
  to { opacity: 1; transform: translateY(0); }
}
@keyframes floatBlob {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(60px, -40px) scale(1.12); }
  66% { transform: translate(-40px, 30px) scale(0.94); }
}
@keyframes pulseDot {
  0%, 100% { opacity: 1; box-shadow: 0 0 8px rgba(0, 229, 255, 0.9); }
  50% { opacity: 0.45; box-shadow: 0 0 2px rgba(0, 229, 255, 0.3); }
}
@keyframes shineSweep {
  0% { transform: translateX(-130%) skewX(-18deg); }
  60%, 100% { transform: translateX(230%) skewX(-18deg); }
}
@keyframes diamondBlink {
  0%, 100% { opacity: 1; box-shadow: 0 0 12px rgba(0, 229, 255, 0.7); }
  50% { opacity: 0.4; box-shadow: 0 0 4px rgba(0, 229, 255, 0.25); }
}
@keyframes scanGlow {
  0%, 100% { opacity: 0.55; }
  50% { opacity: 1; }
}

/* Hero 逐层入场：--d 为交错序号 */
.anim {
  opacity: 0;
  animation: fadeUp 0.9s cubic-bezier(0.22, 0.8, 0.3, 1) forwards;
  animation-delay: calc(var(--d) * 0.16s);
}
/* 滚动浮现：进视口后交错淡入 */
.reveal {
  opacity: 0;
  transform: translateY(26px);
  transition: opacity 0.7s cubic-bezier(0.22, 0.8, 0.3, 1), transform 0.7s cubic-bezier(0.22, 0.8, 0.3, 1);
  transition-delay: calc(var(--d, 0) * 0.09s);
}
.reveal.in-view { opacity: 1; transform: translateY(0); }

/* 按钮：hover 流光扫过 */
.btn-shine {
  position: relative;
  display: inline-block;
  width: 40%;
  height: 100%;
  position: absolute;
  left: 0; top: 0;
  overflow: hidden;
  pointer-events: none;
}
.cta-primary, .login-btn { position: relative; overflow: hidden; }
.cta-primary .btn-shine::after, .login-btn .btn-shine::after {
  content: "";
  position: absolute;
  left: 0; top: 0;
  width: 46%; height: 100%;
  background: linear-gradient(100deg, transparent, rgba(255, 255, 255, 0.45), transparent);
  transform: translateX(-130%) skewX(-18deg);
  animation: shineSweep 3.4s ease-in-out infinite;
}

/* 背景漂移光斑 */
.bg-blobs { position: fixed; inset: 0; pointer-events: none; z-index: 0; overflow: hidden; }
.blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  opacity: 0.5;
  animation: floatBlob 14s ease-in-out infinite;
}
.blob-a { width: 420px; height: 420px; left: -120px; top: 8%; background: rgba(0, 120, 255, 0.20); }
.blob-b { width: 380px; height: 380px; right: -100px; top: 30%; background: rgba(0, 229, 255, 0.14); animation-delay: -5s; }
.blob-c { width: 340px; height: 340px; left: 34%; bottom: -140px; background: rgba(124, 108, 255, 0.13); animation-delay: -9s; }
.landing > header, .landing > section, .landing > footer { position: relative; z-index: 1; }

.landing {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', sans-serif;
  color: var(--text-2);
  background:
    radial-gradient(ellipse 1100px 560px at 50% -8%, rgba(0, 120, 255, 0.10), transparent),
    linear-gradient(rgba(0, 229, 255, 0.025) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 229, 255, 0.025) 1px, transparent 1px),
    var(--bg-canvas);
  background-size: auto, 42px 42px, 42px 42px, auto;
  min-height: 100vh;
  scroll-behavior: smooth;
}

/* ===== 顶部导航：深色玻璃 ===== */
.nav {
  position: sticky; top: 0; z-index: 100;
  background: rgba(6, 10, 19, 0.82);
  backdrop-filter: blur(14px);
  border-bottom: 1px solid var(--border-hairline);
}
.features, .scenes, .stats, .bottom-cta {
  scroll-margin-top: 80px;
}
.nav-inner {
  width: 100%; box-sizing: border-box;
  padding: 0 32px; height: 64px;
  display: flex; align-items: center; justify-content: space-between;
}
.brand {
  display: flex; align-items: center; gap: 10px;
  text-decoration: none; font-size: 19px; font-weight: 700;
  letter-spacing: 2px;
  background: linear-gradient(180deg, #eaffff 0%, #7df3ff 60%, var(--brand) 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  filter: drop-shadow(0 0 10px rgba(0, 229, 255, 0.35));
}
.logo-mark svg { width: 24px; height: 24px; filter: drop-shadow(0 0 8px rgba(0,229,255,.5)); }
.login-btn {
  padding: 9px 22px; font-size: 14px; font-weight: 600;
  color: var(--brand);
  background: rgba(0, 229, 255, 0.10);
  border: 1px solid rgba(0, 229, 255, 0.55);
  border-radius: var(--radius-md, 8px);
  cursor: pointer; transition: all 0.2s;
  box-shadow: 0 0 12px rgba(0, 229, 255, 0.25);
}
.login-btn:hover {
  background: rgba(0, 229, 255, 0.2);
  border-color: var(--brand);
  box-shadow: 0 0 20px rgba(0, 229, 255, 0.45);
}

/* ===== Hero：深空网格 + 霓虹大标题 ===== */
.hero {
  padding: 130px 24px 110px;
  text-align: center;
  position: relative;
  overflow: hidden;
  border-bottom: 1px solid var(--border-hairline);
}
.hero::after {
  content: "";
  position: absolute;
  left: 15%; right: 15%; bottom: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(0, 229, 255, 0.8), transparent);
  animation: scanGlow 3.2s ease-in-out infinite;
}
.hero-content { max-width: 860px; margin: 0 auto; position: relative; z-index: 1; }
.hero-badge {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 7px 20px;
  margin-bottom: 28px;
  font-size: 12px;
  letter-spacing: 4px;
  color: var(--brand);
  border: 1px solid rgba(0, 229, 255, 0.4);
  border-radius: 9999px;
  background: rgba(0, 229, 255, 0.06);
  box-shadow: 0 0 16px rgba(0, 229, 255, 0.15);
}
.badge-dot {
  width: 7px; height: 7px;
  border-radius: 50%;
  background: var(--brand);
  animation: pulseDot 1.8s ease-in-out infinite;
}
.hero h1 {
  font-size: clamp(34px, 5.4vw, 58px);
  font-weight: 800;
  line-height: 1.22;
  margin: 0 0 24px;
  letter-spacing: 3px;
}
.hero-line { display: block; }
.hero-glow {
  background: linear-gradient(180deg, #eaffff 0%, #7df3ff 55%, var(--brand) 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  filter: drop-shadow(0 0 22px rgba(0, 229, 255, 0.5));
}
.hero-sub { font-size: clamp(14px, 1.6vw, 17px); line-height: 1.75; color: var(--text-3); margin: 0 0 42px; letter-spacing: 1.5px; }
.hero-cta { display: flex; gap: 18px; justify-content: center; align-items: center; }
.cta-primary {
  padding: 14px 40px; font-size: 16px; font-weight: 700; letter-spacing: 2px;
  color: #06121f;
  background: var(--brand-gradient);
  border: none; border-radius: 10px;
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
  box-shadow: 0 0 24px rgba(0, 229, 255, 0.4);
}
.cta-primary:hover { transform: translateY(-2px); box-shadow: 0 0 36px rgba(0, 229, 255, 0.6); }
.cta-link { color: var(--text-2); font-size: 15px; text-decoration: none; letter-spacing: 1px; }
.cta-link:hover { color: var(--brand); }

/* ===== Section Title：发光菱形装饰 ===== */
.section-title {
  display: flex; align-items: center; justify-content: center; gap: 18px;
  font-size: 30px; font-weight: 700; text-align: center;
  margin: 0 0 52px;
  color: var(--text-1);
  letter-spacing: 4px;
}
.title-diamond {
  width: 9px; height: 9px;
  background: var(--brand);
  box-shadow: 0 0 12px rgba(0, 229, 255, 0.6);
  transform: rotate(45deg);
  animation: diamondBlink 2.6s ease-in-out infinite;
}

/* ===== Feature Grid：霓虹角标卡片 ===== */
.features { padding: 90px 32px; }
.feature-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 22px; max-width: 1280px; margin: 0 auto; }
.feature-card {
  position: relative;
  padding: 30px 24px;
  background: var(--bg-surface-1);
  border: 1px solid var(--border-hairline);
  border-radius: 14px;
  transition: transform 0.2s, box-shadow 0.2s, border-color 0.2s;
}
.feature-card::before, .feature-card::after {
  content: ""; position: absolute; width: 12px; height: 12px; pointer-events: none;
}
.feature-card::before {
  top: -1px; left: -1px;
  border-top: 1px solid rgba(0, 229, 255, 0.6);
  border-left: 1px solid rgba(0, 229, 255, 0.6);
  border-top-left-radius: 14px;
}
.feature-card::after {
  bottom: -1px; right: -1px;
  border-bottom: 1px solid rgba(0, 229, 255, 0.4);
  border-right: 1px solid rgba(0, 229, 255, 0.4);
  border-bottom-right-radius: 14px;
}
.feature-card:hover {
  transform: translateY(-3px);
  border-color: var(--border-hairline-strong);
  box-shadow: 0 0 24px rgba(0, 229, 255, 0.12);
}
.feature-icon-wrap {
  width: 48px; height: 48px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  margin-bottom: 18px;
  border: 1px solid var(--border-hairline);
}
.feature-icon-wrap svg { width: 24px; height: 24px; }
.icon-sync { background: rgba(0,229,255,.10); color: var(--brand); }
.icon-ai     { background: rgba(124,108,255,.12); color: #a89bff; }
.icon-ship   { background: rgba(0,229,255,.10); color: var(--brand); }
.icon-shield { background: rgba(255,179,0,.12); color: #ffc94d; }
.icon-shop   { background: rgba(0,230,118,.10); color: #4dff9e; }
.icon-buyer  { background: rgba(255,77,106,.12); color: #ff7a92; }
.icon-notify { background: rgba(124,108,255,.12); color: #a89bff; }
.icon-cloud  { background: rgba(0,229,255,.10); color: var(--brand); }
.icon-monitor{ background: rgba(0,176,255,.12); color: #4dc3ff; }
.feature-card h3 { font-size: 17px; font-weight: 600; margin: 0 0 10px; color: var(--text-1); letter-spacing: 1px; }
.feature-card p  { font-size: 14px; line-height: 1.65; color: var(--text-3); margin: 0; }

/* ===== Scenes：横向霓虹卡 ===== */
.scenes { background: var(--bg-surface-1); padding: 90px 32px; border-top: 1px solid var(--border-hairline); border-bottom: 1px solid var(--border-hairline); }
.scene-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 22px;
  max-width: 1280px; margin: 0 auto;
}
.scene-card {
  padding: 28px 24px;
  background: var(--bg-surface-2);
  border-radius: 14px;
  border: 1px solid var(--border-hairline);
  display: flex; gap: 16px; align-items: flex-start;
  transition: transform 0.2s, box-shadow 0.2s, border-color 0.2s;
}
.scene-card:hover {
  transform: translateY(-2px);
  border-color: rgba(0, 229, 255, 0.45);
  box-shadow: 0 0 24px rgba(0, 229, 255, 0.12);
}
.scene-icon {
  width: 48px; height: 48px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  border: 1px solid var(--border-hairline);
}
.scene-icon svg { width: 22px; height: 22px; }
.scene-icon.icon-multi { background: rgba(0,229,255,.10); color: var(--brand); }
.scene-icon.icon-auto  { background: rgba(0,230,118,.10); color: #4dff9e; }
.scene-icon.icon-cost  { background: rgba(255,179,0,.12); color: #ffc94d; }
.scene-icon.icon-data  { background: rgba(124,108,255,.12); color: #a89bff; }
.scene-card h3 { font-size: 16px; font-weight: 600; margin: 0 0 8px; color: var(--text-1); letter-spacing: 1px; }
.scene-card p { font-size: 14px; line-height: 1.65; color: var(--text-3); margin: 0; }

/* ===== Stats：发光大数字 ===== */
.stats { padding: 80px 32px; }
.stats-inner {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 24px;
  text-align: center;
  max-width: 1100px; margin: 0 auto;
}
.stat-num {
  font-size: 44px; font-weight: 800;
  font-family: var(--font-mono);
  color: var(--text-1);
  margin-bottom: 10px;
  text-shadow: 0 0 22px rgba(0, 229, 255, 0.45);
}
.stat-label { font-size: 14px; color: var(--text-3); letter-spacing: 2px; }

/* ===== Bottom CTA：霓虹边框条 ===== */
.bottom-cta { padding: 70px 32px; }
.bottom-cta-inner {
  max-width: 1100px; margin: 0 auto;
  display: flex; align-items: center; justify-content: space-between;
  gap: 24px; flex-wrap: wrap;
  padding: 44px 48px;
  border: 1px solid rgba(0, 229, 255, 0.35);
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(0, 229, 255, 0.06), rgba(46, 124, 246, 0.06));
  box-shadow: 0 0 32px rgba(0, 229, 255, 0.08);
}
.bottom-cta h2 {
  font-size: 26px; font-weight: 700; color: var(--text-1); margin: 0; letter-spacing: 2px;
}

/* ===== Footer ===== */
.footer { background: rgba(6, 10, 19, 0.9); padding: 24px 32px; border-top: 1px solid var(--border-hairline); }
.footer-inner {
  display: flex; justify-content: space-between; align-items: center;
  font-size: 13px; color: var(--text-3);
}
.footer-links { display: flex; gap: 20px; }
.footer-links a { color: var(--text-3); text-decoration: none; }
.footer-links a:hover { color: var(--brand); }

/* ===== Responsive ===== */
@media (max-width: 768px) {
  .hero h1 { font-size: 30px; }
  .hero-sub { font-size: 14px; }
  .section-title { font-size: 22px; letter-spacing: 2px; }
  .stats-inner { grid-template-columns: repeat(2, 1fr); }
  .footer-inner { flex-direction: column; gap: 10px; }
  .bottom-cta-inner { flex-direction: column; text-align: center; }
  .sidebar-menu :deep(.el-menu-item) { width: calc(50% - 12px); }
}
</style>
