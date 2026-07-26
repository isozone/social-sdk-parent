import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
  plugins: [
    uni({
      vue: {
        // uni-mp-vite 期望 vueOptions 存在，给空对象避免 undefined
        compilerOptions: {},
      },
    }),
  ],
  css: {
    preprocessorOptions: {
      scss: {
        // 不全局注入 uni.scss，避免与 .vue 内显式 @import 重复
      },
    },
  },
  server: {
    port: 5170,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
      },
    },
  },
})
