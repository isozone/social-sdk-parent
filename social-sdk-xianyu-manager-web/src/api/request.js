import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const api = axios.create({
  baseURL: import.meta.env.BASE_API_URL || '/api',
  timeout: 30000
})

// 请求拦截器 - 自动附加 JWT
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let isRedirecting = false

// 响应拦截器 - 统一处理错误
api.interceptors.response.use(
  response => response.data,
  error => {
    if (error.response) {
      const { status, data } = error.response
      if (status === 401 || status === 403) {
        localStorage.removeItem('token')
        if (!isRedirecting) {
          isRedirecting = true
          router.push('/login').finally(() => {
            isRedirecting = false
          })
          ElMessage.error(status === 401 ? '登录已过期，请重新登录' : '权限不足，请重新登录')
        }
      } else {
        ElMessage.error(data?.message || '请求失败')
      }
    } else {
      ElMessage.error('网络错误')
    }
    return Promise.reject(error)
  }
)

export default api
