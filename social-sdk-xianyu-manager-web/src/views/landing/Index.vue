<template>
  <div class="landing">
    <!-- 顶部导航：左 logo + 右登录按钮 -->
    <header class="nav">
      <div class="nav-inner">
        <router-link to="/" class="brand">
          <span class="logo-mark"><svg width="22" height="22" viewBox="0 0 24 24" fill="none"><rect width="24" height="24" rx="6" fill="url(#brandGrad)"/><path d="M7 8l5 4-5 4V8z" fill="#fff"/></svg></span>
          <span>闲鱼管家</span>
        </router-link>
        <button class="login-btn" @click="goLogin">登录控制台 &rsaquo;</button>
      </div>
    </header>

    <!-- Hero 大 banner -->
    <section class="hero">
      <div class="hero-content">
        <h1>闲鱼多账号自动化运营平台</h1>
        <p class="hero-sub">从消息同步到自动发货，从风控解题到 AI 客服，一站式托管你的闲鱼生意</p>
        <div class="hero-cta">
          <button class="cta-primary" @click="goLogin">立即登录</button>
          <a class="cta-link" href="#features">了解功能</a>
        </div>
      </div>
    </section>

    <!-- 核心特性 -->
    <section id="features" class="features">
      <h2 class="section-title">核心能力</h2>
      <div class="feature-grid">
        <div class="feature-card" v-for="f in features" :key="f.title">
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
      <h2 class="section-title">使用场景</h2>
      <div class="scene-grid">
        <div class="scene-card" v-for="s in scenes" :key="s.title">
          <div class="scene-icon" :class="'icon-' + s.key">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" v-html="getSceneIcon(s.key)"></svg>
          </div>
          <h3>{{ s.title }}</h3>
          <p>{{ s.desc }}</p>
        </div>
      </div>
    </section>

    <!-- 数据指标 -->
    <section class="stats">
      <div class="stats-inner">
        <div class="stat-item" v-for="s in stats" :key="s.label">
          <div class="stat-num">{{ s.num }}</div>
          <div class="stat-label">{{ s.label }}</div>
        </div>
      </div>
    </section>

    <!-- 底部 CTA -->
    <section class="bottom-cta">
      <div class="bottom-cta-inner">
        <h2>开始托管你的闲鱼生意</h2>
        <button class="cta-primary" @click="goLogin">登录控制台</button>
      </div>
    </section>

    <!-- 页脚 -->
    <footer class="footer">
      <div class="footer-inner">
        <span>&copy; 2026 闲鱼管家 &middot; Xianyu Manager</span>
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

const router = useRouter()
const goLogin = () => router.push('/login')

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
.landing {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', sans-serif;
  color: #1a1a1a;
  background: #fff;
  min-height: 100vh;
  scroll-behavior: smooth;
}

/* ===== 顶部导航 ===== */
.nav {
  position: sticky; top: 0; z-index: 100;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}
.nav-inner {
  width: 100%; box-sizing: border-box;
  padding: 0 32px; height: 64px;
  display: flex; align-items: center; justify-content: space-between;
}
.brand {
  display: flex; align-items: center; gap: 10px;
  text-decoration: none; color: #1a1a1a; font-size: 20px; font-weight: 700;
}
.logo-mark svg { width: 24px; height: 24px; }
.login-btn {
  padding: 8px 20px; font-size: 14px; font-weight: 500;
  color: #fff; background: var(--brand, #4f46e5); border: none;
  border-radius: var(--radius-md, 8px); cursor: pointer; transition: background 0.2s;
}
.login-btn:hover { background: #4338ca; }

/* ===== Hero ===== */
.hero {
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 50%, #2563eb 100%);
  padding: 120px 24px 100px; text-align: center; color: #fff;
}
.hero-content { max-width: 800px; margin: 0 auto; }
.hero h1 { font-size: 44px; font-weight: 800; margin: 0 0 20px; letter-spacing: -0.5px; }
.hero-sub { font-size: 18px; line-height: 1.6; color: rgba(255, 255, 255, 0.88); margin: 0 0 36px; }
.hero-cta { display: flex; gap: 16px; justify-content: center; align-items: center; }
.cta-primary {
  padding: 14px 36px; font-size: 16px; font-weight: 600;
  color: #4f46e5; background: #fff; border: none; border-radius: 10px;
  cursor: pointer; transition: transform 0.15s, box-shadow 0.15s;
}
.cta-primary:hover { transform: translateY(-1px); box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15); }
.cta-link { color: #fff; font-size: 15px; text-decoration: none; opacity: 0.9; }
.cta-link:hover { opacity: 1; }

/* ===== Section Title ===== */
.section-title {
  font-size: 32px; font-weight: 700; text-align: center;
  margin: 0 0 48px; color: #1a1a1a;
}

/* ===== Feature Grid ===== */
.features { padding: 80px 32px; }
.feature-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 24px; }
.feature-card {
  padding: 32px 24px; background: #fafafa;
  border: 1px solid #f0f0f0; border-radius: 16px;
  transition: transform 0.2s, box-shadow 0.2s, border-color 0.2s;
}
.feature-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 12px 32px rgba(79, 70, 229, 0.08);
  border-color: rgba(79, 70, 229, 0.18);
}
.feature-icon-wrap {
  width: 48px; height: 48px; border-radius: 14px;
  display: flex; align-items: center; justify-content: center;
  margin-bottom: 18px; color: #fff; position: relative; overflow: hidden;
}
.feature-icon-wrap::after {
  content: ''; position: absolute; inset: 0;
  border: 1px solid rgba(255,255,255,0.18); border-radius: inherit;
}
.feature-icon-wrap svg { width: 24px; height: 24px; position: relative; z-index: 1; }
.icon-sync { background: linear-gradient(135deg, #4f46e5, #6366f1); }
.icon-ai     { background: linear-gradient(135deg, #7c3aed, #a855f7); }
.icon-ship   { background: linear-gradient(135deg, #06b6d4, #22d3ee); }
.icon-shield { background: linear-gradient(135deg, #f59e0b, #fbbf24); }
.icon-shop   { background: linear-gradient(135deg, #10b981, #34d399); }
.icon-buyer  { background: linear-gradient(135deg, #ef4444, #f87171); }
.icon-notify { background: linear-gradient(135deg, #6366f1, #818cf8); }
.icon-cloud  { background: linear-gradient(135deg, #22d3ee, #67e8f9); }
.icon-monitor{ background: linear-gradient(135deg, #06b6d4, #0891b2); }
.feature-card h3 { font-size: 18px; font-weight: 600; margin: 0 0 10px; color: #1a1a1a; }
.feature-card p  { font-size: 14px; line-height: 1.6; color: #666; margin: 0; }

/* ===== Scenes ===== */
.scenes { background: #f9fafb; padding: 80px 32px; }
.scene-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 24px;
}
.scene-card {
  padding: 32px 24px; background: #fff;
  border-radius: 16px; border: 1px solid #f0f0f0;
  display: flex; flex-direction: column; gap: 14px;
  transition: transform 0.2s, box-shadow 0.2s;
}
.scene-card:hover { transform: translateY(-2px); box-shadow: 0 8px 24px rgba(0,0,0,0.06); }
.scene-icon {
  width: 48px; height: 48px; border-radius: 14px;
  display: flex; align-items: center; justify-content: center;
  color: #fff;
}
.scene-icon svg { width: 22px; height: 22px; }
.scene-icon.icon-multi { background: linear-gradient(135deg, #4f46e5, #7c3aed); }
.scene-icon.icon-auto  { background: linear-gradient(135deg, #06b6d4, #22d3ee); }
.scene-icon.icon-cost  { background: linear-gradient(135deg, #f59e0b, #fbbf24); }
.scene-icon.icon-data  { background: linear-gradient(135deg, #10b981, #34d399); }
.scene-card h3 { font-size: 17px; font-weight: 600; margin: 0; color: #1a1a1a; }
.scene-card p { font-size: 14px; line-height: 1.6; color: #666; margin: 0; }

/* ===== Stats ===== */
.stats { background: #1a1a2e; padding: 72px 32px; }
.stats-inner {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 24px;
  text-align: center;
}
.stat-num { font-size: 40px; font-weight: 800; color: #fff; margin-bottom: 8px; }
.stat-label { font-size: 14px; color: rgba(255, 255, 255, 0.65); }

/* ===== Bottom CTA ===== */
.bottom-cta {
  padding: 56px 32px;
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
}
.bottom-cta-inner {
  display: flex; align-items: center; justify-content: space-between;
  gap: 24px; flex-wrap: wrap;
}
.bottom-cta h2 { font-size: 28px; font-weight: 700; color: #fff; margin: 0; }

/* ===== Footer ===== */
.footer { background: #111; padding: 24px 32px; border-top: 1px solid #222; }
.footer-inner {
  display: flex; justify-content: space-between; align-items: center;
  font-size: 13px; color: #888;
}
.footer-links { display: flex; gap: 20px; }
.footer-links a { color: #888; text-decoration: none; }
.footer-links a:hover { color: #fff; }

/* ===== Responsive ===== */
@media (max-width: 768px) {
  .hero h1 { font-size: 28px; }
  .hero-sub { font-size: 15px; }
  .section-title { font-size: 24px; }
  .stats-inner { grid-template-columns: repeat(2, 1fr); }
  .footer-inner { flex-direction: column; gap: 10px; }
  .bottom-cta-inner { flex-direction: column; text-align: center; }
}
</style>
