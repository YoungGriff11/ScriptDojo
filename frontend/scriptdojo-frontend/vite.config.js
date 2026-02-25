import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        credentials: true,
      },
      '/ws': {
        target: 'http://localhost:8080',
        ws: true,           // ← tells Vite this is a WebSocket endpoint
        changeOrigin: true,
      },
      '/perform_login': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/logout': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      }
    }
  }
})