import fsd from "@feature-sliced/steiger-plugin";
import { defineConfig } from "steiger";

export default defineConfig([
  ...fsd.configs.recommended,
  {
    // Cross-slice coupling remaining from the flat layout; tighten in phase 2 splits.
    rules: {
      "fsd/forbidden-imports": "warn",
      "fsd/no-public-api-sidestep": "warn",
      "fsd/insignificant-slice": "off",
      "fsd/excessive-slicing": "off",
    },
  },
]);
