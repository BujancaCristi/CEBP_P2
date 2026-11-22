import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

export default defineConfig({
  plugins: [react()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: {
    port: 5173,
    proxy: {
      '/api/s1': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/s1/, ''),
      },
      '/api/s2': {
        target: 'http://localhost:8082',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/s2/, ''),
      },
      '/ws': {
        target: 'ws://localhost:8081',
        ws: true,
      },
    },
  },
});
