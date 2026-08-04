import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],

  // The dev server proxies /api to the backend so the browser sees one origin.
  // This is why the app needs no CORS configuration: adding @CrossOrigin to the
  // controllers would ship a development concern into production, where the two
  // are served from the same origin anyway.
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },

  // Output goes straight into the Spring static directory, so `mvn package`
  // picks it up with no build plugin involved.
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
})
