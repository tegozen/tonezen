import {
  audiobookBookSlug,
  buildAudiobookTracks,
  buildMusicLibrary,
  isAudioFilename,
  naturalCompare,
  pickAudiobookAuthor,
  titleFromSlug,
  type AudiobookFileScan,
  type MusicFileScan,
  type ParsedBook,
  type ParsedCycle,
} from "./parsers.js";
import type { AudioTags } from "./mediaProbe.js";

export interface StorageObjectInput {
  name: string;
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

export async function scanStorageObjects(
  objects: StorageObjectInput[],
  options: ScanStorageOptions = {},
): Promise<{ cycles: ParsedCycle[]; musicAlbums: ParsedBook[] }> {
  const cycleBooks = new Map<string, Map<string, AudiobookFileScan[]>>();
  const musicFiles: MusicFileScan[] = [];

  for (const object of objects) {
    const name = object.name;
    if (!isIndexableAudioPath(name)) continue;

    if (name.startsWith("music/")) {
      const filename = name.slice("music/".length);
      if (!filename || filename.includes("/")) continue;
      const tags = options.probeTags ? await options.probeTags(name) : null;
      musicFiles.push({
        filename,
        title: tags?.title ?? null,
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
    const files = cycleBooks.get(cycleSlug) ?? new Map<string, AudiobookFileScan[]>();
    const bookFiles = files.get(bookSlug) ?? [];
    bookFiles.push({
      filename,
      title: tags?.title ?? null,
      artist: tags?.artist ?? null,
      durationMs: tags?.durationMs ?? null,
    });
    files.set(bookSlug, bookFiles);
    cycleBooks.set(cycleSlug, files);
  }

  const cycles: ParsedCycle[] = [];
  for (const [cycleSlug, booksMap] of cycleBooks) {
    const bookSlugs = [...booksMap.keys()].sort(naturalCompare);
    const books: ParsedBook[] = [];

    for (const bookSlug of bookSlugs) {
      const scannedFiles = [...(booksMap.get(bookSlug) ?? [])].sort((a, b) =>
        naturalCompare(a.filename, b.filename),
      );
      if (scannedFiles.length === 0) continue;

      books.push({
        slug: audiobookBookSlug(cycleSlug, bookSlug),
        storageSlug: bookSlug,
        contentType: "audiobook",
        title: titleFromSlug(bookSlug),
        author: pickAudiobookAuthor(scannedFiles),
        coverPath: null,
        tracks: buildAudiobookTracks(scannedFiles),
      });
    }

    if (books.length === 0) continue;
    cycles.push({
      slug: cycleSlug,
      title: titleFromSlug(cycleSlug),
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
