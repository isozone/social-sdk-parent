<script setup lang="ts">
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app'
import { useAuthStore } from '@/store/modules/auth'

onLaunch(() => {})

onShow(() => {
  const auth = useAuthStore()
  if (auth.token && !auth.isRefreshing) {
    // Refresh profile cache silently
    auth.fetchProfile().catch(() => {})
  }
})

onHide(() => {})
</script>

<style lang="scss">
/* ========== Base reset ========== */
page {
  background-color: #f5f5f7;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  font-size: 28rpx;
  color: #111827;
  box-sizing: border-box;
}

*, *::before, *::after {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

/* safe areas */
.safe-bottom {
  padding-bottom: env(safe-area-inset-bottom);
}

/* scrollbar hidden */
::-webkit-scrollbar {
  display: none;
  width: 0;
  height: 0;
}

/* global button reset */
button::after {
  border: none;
}

button {
  background: transparent;
  border-radius: 0;
}

/* link */
.link {
  color: #4f46e5;
  text-decoration: none;
}

/* selection */
::selection {
  background-color: rgba(79, 70, 229, 0.3);
}
</style>
