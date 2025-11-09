// vite.config.ts
import { defineConfig } from 'vite';

export default defineConfig({
  // Évite le pré-bundle cassant pour sockjs
  optimizeDeps: {
    exclude: ['sockjs-client'],
  },
  // Aide Vite à résoudre le fichier UMD de sockjs
  resolve: {
    alias: {
      'sockjs-client': 'sockjs-client/dist/sockjs.js',
    },
  },
  define: {
    global: 'window', // ⬅️ corrige "global is not defined"
  },
});
