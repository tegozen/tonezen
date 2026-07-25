import tailwindcss from "@tailwindcss/vite";
import { defineConfig } from "electron-vite";
import react from "@vitejs/plugin-react";
import { resolve } from "node:path";

const coreAlias = { "@core": resolve("src/core") };

export default defineConfig({
  main: {
    resolve: {
      alias: coreAlias,
    },
    build: {
      rollupOptions: {
        external: ["better-sqlite3", "ws"],
      },
    },
  },
  preload: {
    resolve: {
      alias: coreAlias,
    },
  },
  renderer: {
    resolve: {
      alias: {
        ...coreAlias,
        "@": resolve("src/renderer/src"),
      },
    },
    plugins: [react(), tailwindcss()],
  },
});
