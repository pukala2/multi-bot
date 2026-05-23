import { defineConfig } from 'vite';
import preact from '@preact/preset-vite';
import cssInjectedByJsPlugin from 'vite-plugin-css-injected-by-js';

export default defineConfig({
  plugins: [preact(), cssInjectedByJsPlugin()],
  build: {
    lib: {
      entry: 'src/embed.tsx',
      name: 'MultibotWidget',
      formats: ['iife'],
      fileName: () => 'embed.js',
    },
    cssCodeSplit: false,
    sourcemap: false,
  },
  server: {
    port: 5174,
  },
});
