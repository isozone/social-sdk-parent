/**
 * 格式化日期为 ISO 风格的短日期字符串：YYYY-MM-DD
 */
export function formatDate(date: Date | string): string {
  const d = date instanceof Date ? date : new Date(date)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/**
 * 格式化日期时间为 YYYY-MM-DD HH:mm:ss
 */
export function formatDateTime(date: Date | string): string {
  const d = date instanceof Date ? date : new Date(date)
  return `${formatDate(d)} ${padHour(d.getHours())}:${padMinute(d.getMinutes())}:${padMinute(d.getSeconds())}`
}

function padHour(h: number): string {
  return String(h).padStart(2, '0')
}

function padMinute(m: number): string {
  return String(m).padStart(2, '0')
}

/**
 * 返回相对时间描述，如"刚刚"、"5分钟前"、"2小时前"、"昨天"等
 */
export function timeAgo(date: Date | string): string {
  const target = date instanceof Date ? date.getTime() : new Date(date).getTime()
  const now = Date.now()
  const diffMs = now - target

  if (diffMs < 0) {
    return '刚刚'
  }

  const seconds = Math.floor(diffMs / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)

  if (seconds < 60) {
    return '刚刚'
  }
  if (minutes < 60) {
    return `${minutes}分钟前`
  }
  if (hours < 24) {
    return `${hours}小时前`
  }
  if (days === 1) {
    return '昨天'
  }
  if (days < 7) {
    return `${days}天前`
  }

  return formatDate(target)
}

/**
 * formatTime 的别名，与 timeAgo 功能一致
 */
export function formatTime(date: Date | string): string {
  return timeAgo(date)
}
