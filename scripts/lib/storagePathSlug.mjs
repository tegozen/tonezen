/**
 * Sanitize Supabase Storage object keys: transliterate Cyrillic and normalize spaces.
 * Storage API accepts only ASCII (\w and a limited symbol set); S3 keys cannot contain Cyrillic.
 */

const RU_TO_LAT = {
  а: "a",
  б: "b",
  в: "v",
  г: "g",
  д: "d",
  е: "e",
  ё: "yo",
  ж: "zh",
  з: "z",
  и: "i",
  й: "y",
  к: "k",
  л: "l",
  м: "m",
  н: "n",
  о: "o",
  п: "p",
  р: "r",
  с: "s",
  т: "t",
  у: "u",
  ф: "f",
  х: "kh",
  ц: "ts",
  ч: "ch",
  ш: "sh",
  щ: "shch",
  ъ: "",
  ы: "y",
  ь: "",
  э: "e",
  ю: "yu",
  я: "ya",
};

/** @param {string} text */
export function transliterateRu(text) {
  let out = "";
  for (const char of text) {
    const lower = char.toLowerCase();
    const mapped = RU_TO_LAT[lower];
    if (mapped !== undefined) {
      if (char === lower) {
        out += mapped;
      } else {
        out += mapped.charAt(0).toUpperCase() + mapped.slice(1);
      }
      continue;
    }
    out += char;
  }
  return out;
}

const STORAGE_SEGMENT_SAFE = /[a-z0-9._\-+!*'()&$@=;:+,?]/;

/**
 * @typedef {{ storagePath: string, displayPath: string }} DisplayPathMapping
 */

/** @param {string} segment */
function sanitizeDirectorySegment(segment) {
  const transliterated = transliterateRu(segment).replace(/\s+/g, "-").toLowerCase();
  let out = "";
  for (const char of transliterated) {
    out += STORAGE_SEGMENT_SAFE.test(char) ? char : "-";
  }
  return out.replace(/-+/g, "-").replace(/^-+|-+$/g, "") || "untitled";
}

/** @param {string} segment */
function sanitizeFilenameSegment(segment) {
  const dot = segment.lastIndexOf(".");
  if (dot <= 0) {
    return sanitizeDirectorySegment(segment);
  }

  const base = segment.slice(0, dot);
  const ext = segment.slice(dot);
  let sanitizedBase = "";
  for (const char of transliterateRu(base).toLowerCase()) {
    sanitizedBase += STORAGE_SEGMENT_SAFE.test(char) ? char : "-";
  }
  sanitizedBase = sanitizedBase.replace(/-+/g, "-").replace(/^-+|-+$/g, "") || "file";

  let sanitizedExt = "";
  for (const char of ext.toLowerCase()) {
    sanitizedExt += STORAGE_SEGMENT_SAFE.test(char) ? char : "";
  }
  return `${sanitizedBase}${sanitizedExt || ""}`;
}

/**
 * @param {string} path Storage object path without leading slash.
 * @returns {string}
 */
export function sanitizeStoragePath(path) {
  if (!path) return path;

  const segments = path.split("/");
  const lastIndex = segments.length - 1;

  return segments
    .map((segment, index) => {
      if (!segment) return segment;
      const isFilename = index === lastIndex && segment.includes(".");
      return isFilename ? sanitizeFilenameSegment(segment) : sanitizeDirectorySegment(segment);
    })
    .join("/");
}

/**
 * @param {string} storagePath
 * @param {string} displayPath
 * @returns {DisplayPathMapping | null}
 */
function displayMapping(storagePath, displayPath) {
  if (!storagePath || !displayPath || storagePath === displayPath) {
    return null;
  }
  return { storagePath, displayPath };
}

/**
 * @param {string | undefined | null} header
 * @returns {string | undefined}
 */
export function rewriteUploadMetadataHeader(header) {
  return rewriteUploadMetadataHeaderWithMapping(header).header;
}

/**
 * @param {string | undefined | null} header
 * @returns {{ header: string | undefined, mapping: DisplayPathMapping | null }}
 */
export function rewriteUploadMetadataHeaderWithMapping(header) {
  if (!header) return { header: header ?? undefined, mapping: null };

  let mapping = null;

  const rewritten = header
    .split(",")
    .map((part) => {
      const trimmed = part.trim();
      const spaceIndex = trimmed.indexOf(" ");
      if (spaceIndex === -1) return trimmed;

      const key = trimmed.slice(0, spaceIndex);
      const value = trimmed.slice(spaceIndex + 1);
      if (key !== "objectName" || !value) {
        return `${key} ${value}`;
      }

      const decoded = Buffer.from(value, "base64").toString("utf8");
      const sanitized = sanitizeStoragePath(decoded);
      if (sanitized === decoded) {
        return `${key} ${value}`;
      }
      mapping ??= displayMapping(sanitized, decoded);
      return `${key} ${Buffer.from(sanitized, "utf8").toString("base64")}`;
    })
    .join(",");

  return { header: rewritten, mapping };
}

/**
 * @param {string} pathname Request pathname such as /object/content/cycles/foo/bar.mp3
 * @returns {string}
 */
export function rewriteObjectPathname(pathname) {
  return rewriteObjectPathnameWithMapping(pathname).pathname;
}

/**
 * @param {string} pathname Request pathname such as /object/content/cycles/foo/bar.mp3
 * @returns {{ pathname: string, mapping: DisplayPathMapping | null }}
 */
export function rewriteObjectPathnameWithMapping(pathname) {
  const match = pathname.match(/^(\/object\/(?:sign\/)?[^/]+\/)(.+)$/);
  if (!match) return { pathname, mapping: null };

  const [, prefix, rawPath] = match;
  const decodedPath = rawPath
    .split("/")
    .map((segment) => {
      try {
        return decodeURIComponent(segment);
      } catch {
        return segment;
      }
    })
    .join("/");

  const sanitizedPath = sanitizeStoragePath(decodedPath);
  if (sanitizedPath === decodedPath) {
    return { pathname, mapping: null };
  }

  const encodedPath = sanitizedPath
    .split("/")
    .map((segment) => encodeURIComponent(segment))
    .join("/");
  return {
    pathname: `${prefix}${encodedPath}`,
    mapping: displayMapping(sanitizedPath, decodedPath),
  };
}
