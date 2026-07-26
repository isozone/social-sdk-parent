import { encryptPayload, decryptResponse } from './encrypt'
import { useAuthStore } from '@/store/modules/auth'

// 服务器地址闭环：登录页「服务器」切换写入 aiyudb_server_base，启动时读取拼到所有请求前
// 默认空串 = 同源（小程序与后端同域），自定义地址 = 跨域部署
function readServerBase(): string {
  try { return uni.getStorageSync('aiyudb_server_base') || '' } catch { return '' }
}
const BASE = readServerBase()

function getToken(): string {
  const auth = useAuthStore()
  return auth.token || ''
}

interface ReqConfig {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: any
  loading?: boolean
  header?: Record<string, string>
}

export async function request<T = any>(cfg: ReqConfig): Promise<T> {
  if (cfg.loading !== false) {
    uni.showLoading({ title: '加载中...' })
  }
  try {
    const isGet = cfg.method === 'GET' || !cfg.method
    let url = cfg.url
    let body: any = cfg.data

    if (isGet && body) {
      const params = new URLSearchParams()
      Object.entries(body).forEach(([k, v]) => {
        if (v !== undefined && v !== null) {
          params.append(k, String(v))
        }
      })
      const q = params.toString()
      url += q ? `${url.includes('?') ? '&' : '?'}${q}` : ''
      body = undefined
    } else if (!isGet && body) {
      body = await encryptPayload(body)
    }

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...cfg.header,
    }
    const token = getToken()
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }
    headers['X-App-Type'] = 'mini-program'

    const res: any = await new Promise((resolve, reject) => {
      uni.request({
        url: BASE + url,
        method: isGet ? 'GET' : (cfg.method || 'POST'),
        data: body,
        header: headers,
        timeout: 30000,
        success: r => resolve(r),
        fail: e => reject(e),
      })
    })

    const data = res.data as any
    if (data?.code === 401 || data?.code === '401' || res.statusCode === 401) {
      const auth = useAuthStore()
      auth.logout()
      uni.redirectTo({ url: '/pages/login/index' })
      throw new Error('登录已过期，请重新登录')
    }
    // 后端 ApiResponse: success=true + code="OK"；兼容 code=0 / 数字 0
    const ok = data?.success === true
      || data?.code === 0
      || data?.code === '0'
      || data?.code === 'OK'
      || data?.code === 'ok'
    if (!ok) {
      throw new Error(data?.message || '请求失败')
    }

    let result = data?.data
    if (result?.d) {
      result = await decryptResponse(result)
    }
    return result as T
  } finally {
    if (cfg.loading !== false) {
      uni.hideLoading()
    }
  }
}

export const api = {
  get: <T = any>(url: string, data?: any, loading = true) => request<T>({ url, method: 'GET', data, loading }),
  post: <T = any>(url: string, data?: any, loading = true) => request<T>({ url, method: 'POST', data, loading }),
  put: <T = any>(url: string, data?: any, loading = true) => request<T>({ url, method: 'PUT', data, loading }),
  delete: <T = any>(url: string, data?: any, loading = true) => request<T>({ url, method: 'DELETE', data, loading }),
}
