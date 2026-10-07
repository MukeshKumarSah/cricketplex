import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const GATEWAY = 'http://localhost:8080'
const AUTH = process.env.VITE_AUTH_URL || GATEWAY
const GAME = process.env.VITE_GAME_URL || GATEWAY
const SOCIAL = process.env.VITE_SOCIAL_URL || GATEWAY

export default defineConfig({
  plugins: [react()],
  server: {
    allowedHosts: ['.monkeycode-ai.live'],
    proxy: {
      '/api/auth': { target: AUTH, changeOrigin: true },
      '/api/settings': { target: AUTH, changeOrigin: true },
      '/api/files': { target: AUTH, changeOrigin: true },
      '/api/supporter': { target: AUTH, changeOrigin: true },
      '/api/notifications': { target: SOCIAL, changeOrigin: true },
      '/api/chat': { target: SOCIAL, changeOrigin: true },
      '/api/blogs': { target: SOCIAL, changeOrigin: true },
      '/api/commentary': { target: SOCIAL, changeOrigin: true },
      '/api/admin/commentary': { target: SOCIAL, changeOrigin: true },
      '/api/forum': { target: SOCIAL, changeOrigin: true },
      '/ws': { target: SOCIAL, changeOrigin: false, ws: true },
      '/api': { target: GAME, changeOrigin: true },
    },
  },
})
