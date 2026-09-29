import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  base: '/app/',
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/auths': { target: 'http://localhost:8708', changeOrigin: true },
      '/plant': { target: 'http://localhost:8708', changeOrigin: true },
      '/static': { target: 'http://localhost:8708', changeOrigin: true },
      '/ws': { target: 'ws://localhost:8708', ws: true, changeOrigin: true },
    },
  },
  build: {
    outDir: '../src/main/resources/static/app',
    emptyOutDir: true,
  },
})
