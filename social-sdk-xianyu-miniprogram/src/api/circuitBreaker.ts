import { api } from './request'

// 后端真实端点：CircuitBreakerController /api/circuit-breaker + @GetMapping("/{accountId}/{serviceName}")
export function getStatus(accountId: number | string, serviceName: string) {
  return api.get(`/api/mini/circuit-breaker/${accountId}/${serviceName}`)
}

// 后端真实端点：CircuitBreakerController /api/circuit-breaker + @PostMapping("/{accountId}/{serviceName}/reset")
export function reset(accountId: number | string, serviceName: string) {
  return api.post(`/api/mini/circuit-breaker/${accountId}/${serviceName}/reset`)
}

// 后端真实端点：CircuitBreakerController /api/circuit-breaker + @PostMapping("/global/{serviceName}/reset")
export function globalReset(serviceName: string) {
  return api.post(`/api/mini/circuit-breaker/global/${serviceName}/reset`)
}
