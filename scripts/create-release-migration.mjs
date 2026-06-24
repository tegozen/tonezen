#!/usr/bin/env node

import { existsSync } from "node:fs";
import { mkdir, readFile, readdir, writeFile } from "node:fs/promises";
import path from "node:path";

import {
  buildReleaseMigration,
  getNextReleaseMigrationFilename,
} from "./lib/releaseMigration.mjs";

async function main() {
  const { version, changelogFile, migrationsDir, releasedAt } = parseArgs(process.argv.slice(2));

  if (!existsSync(changelogFile)) {
    throw new Error(`Changelog file does not exist: ${changelogFile}`);
  }

  await mkdir(migrationsDir, { recursive: true });

  const migrationNames = await readdir(migrationsDir);
  await assertVersionNotRecorded(migrationsDir, migrationNames, version);

  const changelogRu = parseChangelog(await readFile(changelogFile, "utf8"));
  const filename = getNextReleaseMigrationFilename({ migrationNames, version });
  const migrationPath = path.join(migrationsDir, filename);
  const migration = buildReleaseMigration({ version, changelogRu, releasedAt });

  await writeFile(migrationPath, migration, "utf8");
  console.log(migrationPath);
}

function parseArgs(args) {
  const version = args[0];
  const options = new Map();

  for (let index = 1; index < args.length; index += 2) {
    const key = args[index];
    const value = args[index + 1];
    if (!key?.startsWith("--") || value === undefined) {
      usage();
    }
    options.set(key, value);
  }

  const changelogFile = options.get("--changelog-file");
  const migrationsDir =
    options.get("--migrations-dir") ?? path.resolve("backend/supabase/migrations");

  if (!version || !changelogFile) {
    usage();
  }

  return {
    version,
    changelogFile,
    migrationsDir,
    releasedAt: options.get("--released-at"),
  };
}

function usage() {
  throw new Error(
    "Usage: node scripts/create-release-migration.mjs <version> --changelog-file <path> [--migrations-dir <path>] [--released-at <iso>]",
  );
}

function parseChangelog(content) {
  return content
    .split(/\r?\n/)
    .map((line) => line.trim().replace(/^[-*]\s+/, ""))
    .filter(Boolean);
}

async function assertVersionNotRecorded(migrationsDir, migrationNames, version) {
  const versionSlug = version.replaceAll(".", "_");
  const versionSql = `'${version.replaceAll("'", "''")}'`;

  if (migrationNames.some((name) => name.endsWith(`app_version_${versionSlug}.sql`))) {
    throw new Error(`Version ${version} already has a release migration.`);
  }

  for (const name of migrationNames.filter((entry) => entry.endsWith(".sql"))) {
    const content = await readFile(path.join(migrationsDir, name), "utf8");
    if (content.includes("app_versions") && content.includes(versionSql)) {
      throw new Error(`Version ${version} already has a release migration.`);
    }
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
