<script setup lang="ts">
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app'
import { useAuthStore } from '@/store/modules/auth'

onLaunch(() => {
  console.log('[App] Launch')
})

onShow(() => {
  const auth = useAuthStore()
  if (auth.token && !auth.isRefreshing) {
    // Refresh profile cache silently
    auth.fetchProfile().catch(() => {})
  }
})

onHide(() => {
  console.log('[App] Hide')
})
</script>

<style lang="scss">
@import "@/uni.scss";

/* ========== Base reset ========== */
page {
  background-color: $bg-page;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  font-size: 28rpx;
  color: $text-primary;
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
a, .link {
  color: $brand-primary;
  text-decoration: none;
}

/* selection */
::selection {
  background-color: rgba($brand-primary, 0.3);
}
</style>
