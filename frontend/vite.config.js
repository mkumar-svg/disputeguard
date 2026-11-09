import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev: the dashboard runs on :5173 and forwards API calls to the Spring Boot app.
// Build: files go into Spring Boot's static folder, so one app serves both API and dashboard.
const apiTarget = process.env.API_TARGET || 'http://localhost:8081';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': apiTarget,
      '/actuator': apiTarget,
    },
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
});
