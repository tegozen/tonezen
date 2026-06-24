import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { promisify } from "node:util";

import {
  buildAppVersionInsertSql,
  buildReleaseMigration,
  getNextReleaseMigrationFilename,
} from "./releaseMigration.mjs";

const execFileAsync = promisify(execFile);

test("builds app version insert SQL with Russian changelog entries", () => {
  const sql = buildAppVersionInsertSql({
    version: "0.2.1",
    changelogRu: [
      "Добавлена история релизов на лендинг",
      "Исправлено отображение версии под кнопками скачивания",
    ],
    releasedAt: "2026-06-24T01:02:03.000Z",
  });

  assert.match(sql, /INSERT INTO app_versions/);
  assert.match(sql, /version, changelog_ru, released_at/);
  assert.match(sql, /'0.2.1'/);
  assert.match(sql, /ARRAY\[/);
  assert.match(sql, /'Добавлена история релизов на лендинг'/);
  assert.match(sql, /'Исправлено отображение версии под кнопками скачивания'/);
  assert.match(sql, /'2026-06-24T01:02:03.000Z'::timestamptz/);
  assert.match(sql, /ON CONFLICT \(version\) DO UPDATE/);
});

test("selects the next release migration filename after existing numbered migrations", () => {
  assert.equal(
    getNextReleaseMigrationFilename({
      migrationNames: [
        "001_initial_schema.sql",
        "019_content_display_names.sql",
        "notes.txt",
      ],
      version: "0.2.1",
    }),
    "020_app_version_0_2_1.sql",
  );
});

test("rejects empty changelog entries, non-semver versions, and non-Russian changelog entries", () => {
  assert.throws(
    () => buildReleaseMigration({ version: "0.2", changelogRu: ["Добавлен релиз"] }),
    /SemVer/,
  );
  assert.throws(
    () => buildReleaseMigration({ version: "0.2.1", changelogRu: [] }),
    /changelog/,
  );
  assert.throws(
    () => buildReleaseMigration({ version: "0.2.1", changelogRu: ["Release history"] }),
    /Russian/,
  );
});

test("escapes quotes and backslashes in generated SQL", () => {
  const sql = buildAppVersionInsertSql({
    version: "0.2.1",
    changelogRu: ["Добавлено: пользовательский текст с 'кавычкой' и \\ слешем"],
    releasedAt: "2026-06-24T01:02:03.000Z",
  });

  assert.match(
    sql,
    /'Добавлено: пользовательский текст с ''кавычкой'' и \\ слешем'/,
  );
});

test("CLI writes the next migration and refuses an existing release version", async () => {
  const tempRoot = await mkdtemp(path.join(os.tmpdir(), "tonezen-release-"));
  const migrationsDir = path.join(tempRoot, "migrations");
  const changelogFile = path.join(tempRoot, "changelog.txt");

  try {
    await mkdir(migrationsDir);
    await writeFile(path.join(migrationsDir, "019_content_display_names.sql"), "-- prior\n");
    await writeFile(changelogFile, "Добавлен публичный changelog\nИсправлена строка версии\n");

    const cliPath = path.resolve("scripts/create-release-migration.mjs");
    await execFileAsync(process.execPath, [
      cliPath,
      "0.2.1",
      "--changelog-file",
      changelogFile,
      "--migrations-dir",
      migrationsDir,
      "--released-at",
      "2026-06-24T01:02:03.000Z",
    ]);

    const migrationPath = path.join(migrationsDir, "020_app_version_0_2_1.sql");
    const migration = await readFile(migrationPath, "utf8");
    assert.match(migration, /'0.2.1'/);
    assert.match(migration, /'Добавлен публичный changelog'/);
    assert.match(migration, /'Исправлена строка версии'/);

    await assert.rejects(
      execFileAsync(process.execPath, [
        cliPath,
        "0.2.1",
        "--changelog-file",
        changelogFile,
        "--migrations-dir",
        migrationsDir,
      ]),
      /already has a release migration/,
    );
  } finally {
    await rm(tempRoot, { recursive: true, force: true });
  }
});
