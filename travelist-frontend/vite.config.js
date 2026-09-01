import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    // 支持用 @/ 简写代替相对路径,如 import Home from '@/views/HomeView.vue'
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: '127.0.0.1',
    port: 5215,
    // 开发期把 /api 转发到后端 Spring Boot(5222),前端无需关心跨域
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:5222',
        changeOrigin: true,
      },
    },
  },
})
