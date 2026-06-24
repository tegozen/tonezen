import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const skillPath = path.join(root, ".agents/skills/bump/SKILL.md");

if (!existsSync(skillPath)) {
  throw new Error("Missing bump skill");
}

const skill = readFileSync(skillPath, "utf8");

function assertIncludes(expected) {
  if (!skill.includes(expected)) {
    throw new Error(`bump skill must include ${expected}`);
  }
}

assertIncludes("Russian release changelog");
assertIncludes("scripts/create-release-migration.mjs");
assertIncludes("backend/supabase/migrations");
assertIncludes("git tag -a $newTag -F");
assertIncludes("The same changelog text must be used for the migration and tag");

console.log("Bump skill checks passed");
