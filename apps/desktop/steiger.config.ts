import fsd from "@feature-sliced/steiger-plugin";
import { defineConfig } from "steiger";

export default defineConfig([
  ...fsd.configs.recommended,
  {
    rules: {
      "fsd/forbidden-imports": "error",
      "fsd/no-public-api-sidestep": "warn",
      "fsd/insignificant-slice": "off",
      "fsd/excessive-slicing": "off",
    },
  },
  {
    // App composition root: React Query / session providers are a purpose of this segment.
    files: ["./src/renderer/src/app/providers/**"],
    rules: {
      "fsd/segments-by-purpose": "off",
    },
  },
]);
