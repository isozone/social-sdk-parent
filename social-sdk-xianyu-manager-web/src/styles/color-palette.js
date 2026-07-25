/**
 * 全局统一色板常量
 * 所有组件、ECharts、inline style 中涉及的颜色都应引用此文件，确保视觉一致。
 * 与 src/styles/theme.css 中的 CSS 变量保持同值。
 */

// ===== 语义状态色 =====
export const COLORS = {
  success: '#10b981',
  warning: '#f59e0b',
  danger: '#ef4444',
  info: '#64748b',
}

// ===== 品牌色 =====
export const BRAND = {
  primary: '#4f46e5',
  primary2: '#7c3aed',
  accent: '#22d3ee',
}

// ===== 文本层级 =====
export const TEXT = {
  primary: '#303133',
  regular: '#606266',
  secondary: '#909399',
  disabled: '#c0c4cc',
  muted: '#a8abb2',
}

// ===== 背景 / 边框 =====
export const BG = {
  soft: '#f5f7fa',
  subtle: '#fafbfc',
  chat: '#f7f7f7',
  border: '#ebeef5',
  borderSoft: '#e5e5e5',
}

// ===== Element Plus 派生色（与 theme.css --el-color-primary-light-* 对齐）=====
export const EL = {
  primaryLight9: '#ededfc',
  primaryLight7: '#cac8f7',
}

// ===== 图表色板（对应 --chart-1..6）=====
export const CHART = [
  '#7c3aed', // chart-1: violet
  '#22d3ee', // chart-2: cyan
  '#f59e0b', // chart-3: amber
  '#10b981', // chart-4: green
  '#ef4444', // chart-5: red
  '#6366f1', // chart-6: indigo
]

// ===== Element Plus 业务色（兼容旧代码）=====
export const EL_COLORS = {
  success: '#67C23A',
  warning: '#E6A23C',
  danger: '#F56C6C',
  info: '#909399',
}
