/**
 * 判断当前是否应该跳转登录页
 *
 * @param token - 当前用户 token，为空时表示未登录
 */
const BASE64_CHARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'

function decodeBase64Url(input: string): string {
  const normalized = input.replace(/-/g, '+').replace(/_/g, '/')
  let buffer = 0
  let bits = 0
  let output = ''

  for (let i = 0; i < normalized.length; i++) {
    const ch = normalized[i]
    if (ch === '=') break
    const value = BASE64_CHARS.indexOf(ch)
    if (value < 0) continue
    buffer = (buffer << 6) | value
    bits += 6
    if (bits >= 8) {
      bits -= 8
      output += String.fromCharCode((buffer >> bits) & 0xff)
    }
  }
  try {
    return decodeURIComponent(output.split('').map(ch => `%${ch.charCodeAt(0).toString(16).padStart(2, '0')}`).join(''))
  } catch {
    return output
  }
}

export function shouldNavigateToLogin(token?: string | null): boolean {
  if (!token) {
    return true
  }
  try {
    // 简单校验 token 是否看似过期：尝试解析并检查 exp
    const payload = JSON.parse(decodeBase64Url(token.split('.')[1] || ''))
    if (payload.exp && payload.exp * 1000 < Date.now()) {
      return true
    }
  } catch {
    // token 无法解析，视为无效
    return true
  }
  return false
}

/**
 * 执行导航前拦截
 *
 * 若 token 不存在或已过期，则跳转到登录页；否则通过回调放行。
 *
 * @param options 配置项
 * @param options.token 当前 token
 * @param options.loginPath 登录页路径，默认 /pages/login/login
 * @param options.callback 登录状态正常时执行的回调
 * @returns 是否已触发拦截（true 表示需要阻止后续导航）
 */
export async function guardRoute(options: {
  token?: string | null
  loginPath?: string
  callback: () => void | Promise<void>
}): Promise<boolean> {
  const { token, callback, loginPath = '/pages/login/login' } = options

  if (shouldNavigateToLogin(token)) {
    try {
      await uni.navigateTo({ url: loginPath })
    } catch {
      uni.reLaunch({ url: loginPath })
    }
    return true
  }

  await callback()
  return false
}
