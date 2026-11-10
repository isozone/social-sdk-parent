/**
 * 全局统一色板常量
 * 所有组件、ECharts、inline style 中涉及的颜色都应引用此文件，确保视觉一致。
 * 与 src/styles/theme.css 中的 CSS 变量保持同值（Linear 暗色体系：近黑画布 + lavender 强调）。
 */

// ===== 语义状态色 =====
export const COLORS = {
  success: '#27a644',
  warning: '#d97706',
  danger: '#e5484d',
  info: '#8a8f98',
}

// ===== 品牌色 =====
export const BRAND = {
  primary: '#5e6ad2',
  primary2: '#828fff',
  accent: '#828fff',
}

// ===== 文本层级 =====
export const TEXT = {
  primary: '#f7f8f8',
  regular: '#d0d6e0',
  secondary: '#8a8f98',
  disabled: '#6b7078',
  muted: '#8a8f98',
}

// ===== 背景 / 边框 =====
export const BG = {
  soft: '#1a1c20',
  subtle: '#141518',
  chat: '#1a1c20',
  border: '#26282e',
  borderSoft: '#202226',
}

// ===== Element Plus 派生色（与 theme.css --el-color-primary-light-* 对齐）=====
export const EL = {
  primaryLight9: '#17181d',
  primaryLight7: '#262833',
}

// ===== 图表色板（对应 --chart-1..6：品牌紫系 + 语义辅助）=====
export const CHART = [
  '#5e6ad2', // chart-1: 品牌紫
  '#828fff', // chart-2: 亮紫
  '#d97706', // chart-3: 琥珀（语义警示）
  '#27a644', // chart-4: 绿（语义成功）
  '#e5484d', // chart-5: 红（语义危险）
  '#6e7687', // chart-6: 中性灰
]

// ===== Element Plus 业务色（兼容旧代码）=====
export const EL_COLORS = {
  success: '#27a644',
  warning: '#d97706',
  danger: '#e5484d',
  info: '#8a8f98',
}
