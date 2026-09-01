import {
  audiobookBookSlug,
  buildAudiobookTracks,
  buildMusicLibrary,
  isAudioFilename,
  naturalCompare,
  pickAudiobookAuthor,
  titleFromSlug,
  trackTitleFromFilename,
  type AudiobookFileScan,
  type MusicFileScan,
  type ParsedBook,
  type ParsedCycle,
} from "./parsers.js";
import type { AudioTags } from "./mediaProbe.js";
import { repairMojibake } from "./textEncoding.js";

export interface StorageObjectInput {
  name: string;
  displayPath?: string | null;
}

export interface ScanStorageOptions {
  probeTags?: (storagePath: string) => Promise<AudioTags | null>;
}

export function isIndexableAudioPath(name: string): boolean {
  if (name.endsWith("/")) return false;
  const basename = name.split("/").pop() ?? "";
  if (!basename || basename === ".gitkeep") return false;
  return isAudioFilename(basename);
}

function displayPathParts(name: string, displayPath: string | null | undefined): string[] | null {
  if (!displayPath || displayPath === name) return null;
  const nameParts = name.split("/");
  const parts = displayPath.split("/");
  if (parts.length !== nameParts.length || parts[0] !== nameParts[0]) return null;
  return parts;
}

function displaySegment(parts: string[] | null, index: number): string | null {
  const value = parts?.[index]?.trim();
  return value ? repairMojibake(value) : null;
}

function audiobookBookPrefix(name: string): string | null {
  if (!isIndexableAudioPath(name) || !name.startsWith("cycles/")) return null;
  const parts = name.split("/");
  if (parts.length !== 4 || !parts[1] || !parts[2] || !parts[3]) return null;
  return `cycles/${parts[1]}/${parts[2]}/`;
}

export function expandChangedAudiobookBookObjects<T extends StorageObjectInput>(
  changedObjects: T[],
  allObjects: T[],
): T[] {
  const audiobookPrefixes = new Set<string>();
  for (const object of changedObjects) {
    const prefix = audiobookBookPrefix(object.name);
    if (prefix) audiobookPrefixes.add(prefix);
  }

  if (audiobookPrefixes.size === 0) return changedObjects;

  const scopedByName = new Map<string, T>();
  for (const object of changedObjects) {
    scopedByName.set(object.name, object);
  }
  for (const object of allObjects) {
    const prefix = audiobookBookPrefix(object.name);
    if (prefix && audiobookPrefixes.has(prefix) && !scopedByName.has(object.name)) {
      scopedByName.set(object.name, object);
    }
  }

  return [...scopedByName.values()];
}

export async function scanStorageObjects(
  objects: StorageObjectInput[],
  options: ScanStorageOptions = {},
): Promise<{ cycles: ParsedCycle[]; musicAlbums: ParsedBook[] }> {
  const cycleBooks = new Map<
    string,
    {
      title: string | null;
      books: Map<string, { title: string | null; files: AudiobookFileScan[] }>;
    }
  >();
  const musicFiles: MusicFileScan[] = [];

  for (const object of objects) {
    const name = object.name;
    if (!isIndexableAudioPath(name)) continue;

    if (name.startsWith("music/")) {
      const filename = name.slice("music/".length);
      if (!filename || filename.includes("/")) continue;
      const tags = options.probeTags ? await options.probeTags(name) : null;
      const parts = displayPathParts(name, object.displayPath);
      const displayFilename = displaySegment(parts, 1);
      musicFiles.push({
        filename,
        title: tags?.title ?? (displayFilename ? trackTitleFromFilename(displayFilename) : null),
        artist: tags?.artist ?? null,
        album: tags?.album ?? null,
        trackNumber: tags?.trackNumber ?? null,
        durationMs: tags?.durationMs ?? null,
      });
      continue;
    }

    if (!name.startsWith("cycles/")) continue;
    const parts = name.split("/");
    if (parts.length !== 4) continue;

    const [, cycleSlug, bookSlug, filename] = parts;
    if (!cycleSlug || !bookSlug || !filename) continue;

    const tags = options.probeTags ? await options.probeTags(name) : null;
    const displayParts = displayPathParts(name, object.displayPath);
    const cycle = cycleBooks.get(cycleSlug) ?? {
      title: displaySegment(displayParts, 1),
      books: new Map<string, { title: string | null; files: AudiobookFileScan[] }>(),
    };
    cycle.title ??= displaySegment(displayParts, 1);
    const book = cycle.books.get(bookSlug) ?? {
      title: displaySegment(displayParts, 2),
      files: [],
    };
    book.title ??= displaySegment(displayParts, 2);
    const displayFilename = displaySegment(displayParts, 3);
    const displayTitle = displayFilename ? trackTitleFromFilename(displayFilename) : null;
    const bookFiles = book.files;
    bookFiles.push({
      filename,
      orderName: displayFilename ?? filename,
      title: tags?.title ?? displayTitle,
      artist: tags?.artist ?? null,
      durationMs: tags?.durationMs ?? null,
    });
    cycle.books.set(bookSlug, book);
    cycleBooks.set(cycleSlug, cycle);
  }

  const cycles: ParsedCycle[] = [];
  for (const [cycleSlug, cycleScan] of cycleBooks) {
    const bookSlugs = [...cycleScan.books.keys()].sort(naturalCompare);
    const books: ParsedBook[] = [];

    for (const bookSlug of bookSlugs) {
      const bookScan = cycleScan.books.get(bookSlug);
      const scannedFiles = [...(bookScan?.files ?? [])].sort((a, b) => {
        const order = naturalCompare(a.orderName ?? a.filename, b.orderName ?? b.filename);
        return order || naturalCompare(a.filename, b.filename);
      });
      if (scannedFiles.length === 0) continue;

      books.push({
        slug: audiobookBookSlug(cycleSlug, bookSlug),
        storageSlug: bookSlug,
        contentType: "audiobook",
        title: bookScan?.title ?? titleFromSlug(bookSlug),
        author: pickAudiobookAuthor(scannedFiles),
        coverPath: null,
        tracks: buildAudiobookTracks(scannedFiles),
      });
    }

    if (books.length === 0) continue;
    cycles.push({
      slug: cycleSlug,
      title: cycleScan.title ?? titleFromSlug(cycleSlug),
      description: null,
      bookOrder: books.map((book) => book.slug),
      books,
    });
  }

  cycles.sort((a, b) => naturalCompare(a.slug, b.slug));
  musicFiles.sort((a, b) => naturalCompare(a.filename, b.filename));

  return {
    cycles,
    musicAlbums: buildMusicLibrary(musicFiles),
  };
}
