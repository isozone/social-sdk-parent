export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
  success?: boolean
  timestamp?: number
}

export interface PageResponse<T = any> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface PageInfo {
  page: number
  size: number
  keyword?: string
  [key: string]: any
}
