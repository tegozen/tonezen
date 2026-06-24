const SEMVER_RE = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/;
const CYRILLIC_RE = /[А-Яа-яЁё]/;

export function getNextReleaseMigrationFilename({ migrationNames, version }) {
  assertSemver(version);

  const nextNumber =
    Math.max(
      0,
      ...migrationNames
        .map((name) => /^(\d+)_/.exec(name)?.[1])
        .filter(Boolean)
        .map((number) => Number.parseInt(number, 10)),
    ) + 1;

  return `${String(nextNumber).padStart(3, "0")}_app_version_${version.replaceAll(".", "_")}.sql`;
}

export function buildReleaseMigration({ version, changelogRu, releasedAt }) {
  return [
    `-- Release metadata for Tonezen ${version}.`,
    "",
    buildAppVersionInsertSql({ version, changelogRu, releasedAt }),
    "",
  ].join("\n");
}

export function buildAppVersionInsertSql({
  version,
  changelogRu,
  releasedAt = new Date().toISOString(),
}) {
  validateReleaseInput({ version, changelogRu, releasedAt });

  const changelogSql = changelogRu
    .map((entry) => `    ${quoteSqlString(entry)}`)
    .join(",\n");

  return `INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  ${quoteSqlString(version)},
  ARRAY[
${changelogSql}
  ]::text[],
  ${quoteSqlString(releasedAt)}::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;`;
}

export function validateReleaseInput({ version, changelogRu, releasedAt }) {
  assertSemver(version);

  if (!Array.isArray(changelogRu) || changelogRu.length === 0) {
    throw new Error("Release changelog must contain at least one entry.");
  }

  for (const entry of changelogRu) {
    if (typeof entry !== "string" || entry.trim().length === 0) {
      throw new Error("Release changelog entries must be non-empty strings.");
    }
    if (!CYRILLIC_RE.test(entry)) {
      throw new Error("Release changelog entries must be written in Russian.");
    }
  }

  if (Number.isNaN(Date.parse(releasedAt))) {
    throw new Error("Release date must be a valid ISO timestamp.");
  }
}

function assertSemver(version) {
  if (typeof version !== "string" || !SEMVER_RE.test(version)) {
    throw new Error("Version must be a stable SemVer value like 1.2.3.");
  }
}

function quoteSqlString(value) {
  return `'${value.replaceAll("'", "''")}'`;
}
