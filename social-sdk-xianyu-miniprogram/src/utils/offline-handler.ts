import { ref, onMounted, onUnmounted } from 'vue'

/**
 * 网络状态检测 composable
 *
 * 使用 uni.getNetworkType 获取初始网络状态，并通过 uni.onNetworkStatusChange
 * 监听网络变化事件。
 */
export function useOfflineHandler() {
  const isOffline = ref(false)
  const networkType = ref('unknown')

  async function checkNetwork() {
    try {
      const res = await uni.getNetworkType({ success: true }) as any
      const type = res?.networkType || res?.type || 'unknown'
      networkType.value = type
      isOffline.value = type === 'none'
    } catch {
      networkType.value = 'unknown'
      isOffline.value = false
    }
  }

  function handleNetworkStatusChange(isConnected: boolean) {
    isOffline.value = !isConnected
  }

  onMounted(() => {
    checkNetwork()
    uni.onNetworkStatusChange(handleNetworkStatusChange)
  })

  onUnmounted(() => {
    uni.offNetworkStatusChange(handleNetworkStatusChange)
  })

  return {
    isOffline,
    networkType,
    checkNetwork,
  }
}
