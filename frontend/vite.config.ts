import path from "path"
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        // 手动拆分第三方库，优化缓存和加载性能
        // Vite 8 (Rolldown) 要求 manualChunks 为函数形式
        manualChunks(id: string) {
          if (id.includes('node_modules')) {
            if (id.includes('react-dom') || id.includes('react-router')) {
              return 'vendor-react'
            }
            if (id.includes('lucide-react') || id.includes('sonner')) {
              return 'vendor-ui'
            }
          }
        },
      },
    },
    // chunk 大小警告阈值（单位 KB）
    chunkSizeWarningLimit: 600,
  },
})
