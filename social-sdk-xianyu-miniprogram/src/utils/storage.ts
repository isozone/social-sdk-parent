/**
 * 安全的 getStorageSync
 */
export function safeGetStorageSync(key: string): string | undefined {
  try {
    return uni.getStorageSync(key)
  } catch {
    return undefined
  }
}

/**
 * 安全的 setStorageSync
 */
export function safeSetStorageSync(key: string, value: unknown): void {
  try {
    uni.setStorageSync(key, JSON.stringify(value))
  } catch {
    // ignore
  }
}

/**
 * 安全的 removeStorageSync
 */
export function safeRemoveStorageSync(key: string): void {
  try {
    uni.removeStorageSync(key)
  } catch {
    // ignore
  }
}

/**
 * 安全的 getItem
 */
export function safeGetItem<T = string>(key: string): T | null {
  try {
    const raw = typeof wx !== 'undefined' && wx.getStorageSync ? wx.getStorageSync(key) : null
    if (raw === null || raw === undefined) return null
    return JSON.parse(String(raw)) as T
  } catch {
    return null
  }
}

/**
 * 安全的 setItem
 */
export function safeSetItem(key: string, value: unknown): void {
  try {
    if (typeof wx !== 'undefined' && wx.setItem) {
      wx.setItem(key, JSON.stringify(value))
    } else {
      uni.setStorageSync(key, JSON.stringify(value))
    }
  } catch {
    // ignore
  }
}

/**
 * 安全的 removeItem
 */
export function safeRemoveItem(key: string): void {
  try {
    if (typeof wx !== 'undefined' && wx.removeItem) {
      wx.removeItem(key)
    } else {
      uni.removeStorageSync(key)
    }
  } catch {
    // ignore
  }
}
