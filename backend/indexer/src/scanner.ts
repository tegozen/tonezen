import { readdir, readFile, stat } from "node:fs/promises";
import path from "node:path";
import {
  buildMusicLibrary,
  buildTracks,
  isAudioFilename,
  parseBookMeta,
  parseCycleMeta,
  type ParsedBook,
  type ParsedCycle,
} from "./parsers.js";
import { probeAudioTags, resolveStorageObjectPath } from "./mediaProbe.js";

async function fileExists(filePath: string): Promise<boolean> {
  try {
    await stat(filePath);
    return true;
  } catch {
    return false;
  }
}

export async function scanContentRoot(contentRoot: string): Promise<{
  cycles: ParsedCycle[];
  musicAlbums: ParsedBook[];
}> {
  const cyclesDir = path.join(contentRoot, "cycles");
  const musicDir = path.join(contentRoot, "music");

  const cycles = (await fileExists(cyclesDir)) ? await scanCycles(cyclesDir) : [];
  const musicAlbums = (await fileExists(musicDir)) ? await scanMusic(musicDir) : [];

  return { cycles, musicAlbums };
}

async function scanCycles(cyclesDir: string): Promise<ParsedCycle[]> {
  const entries = await readdir(cyclesDir, { withFileTypes: true });
  const cycles: ParsedCycle[] = [];

  for (const entry of entries) {
    if (!entry.isDirectory()) continue;
    const cycleSlug = entry.name;
    const cyclePath = path.join(cyclesDir, cycleSlug);
    const cycleJsonPath = path.join(cyclePath, "cycle.json");
    if (!(await fileExists(cycleJsonPath))) continue;

    const raw = JSON.parse(await readFile(cycleJsonPath, "utf-8"));
    const meta = parseCycleMeta(raw);
    const booksDir = path.join(cyclePath, "books");
    const books = (await fileExists(booksDir)) ? await scanBooksInCycle(booksDir, cycleSlug) : [];

    cycles.push({
      slug: cycleSlug,
      title: meta.title,
      description: meta.description ?? null,
      bookOrder: meta.book_order,
      books,
    });
  }

  return cycles;
}

async function scanBooksInCycle(booksDir: string, cycleSlug: string): Promise<ParsedBook[]> {
  const entries = await readdir(booksDir, { withFileTypes: true });
  const books: ParsedBook[] = [];

  for (const entry of entries) {
    if (!entry.isDirectory()) continue;
    const bookSlug = entry.name;
    const bookPath = path.join(booksDir, bookSlug);
    const bookJsonPath = path.join(bookPath, "book.json");
    if (!(await fileExists(bookJsonPath))) continue;

    const raw = JSON.parse(await readFile(bookJsonPath, "utf-8"));
    const meta = parseBookMeta(raw, "audiobook");
    const coverPath = (await fileExists(path.join(bookPath, "cover.jpg")))
      ? `cycles/${cycleSlug}/books/${bookSlug}/cover.jpg`
      : null;

    books.push({
      slug: bookSlug,
      contentType: meta.content_type,
      title: meta.title,
      author: meta.author ?? null,
      coverPath,
      tracks: buildTracks(meta.track_order),
    });
  }

  return books;
}

async function scanMusic(musicDir: string): Promise<ParsedBook[]> {
  const entries = await readdir(musicDir, { withFileTypes: true });
  const files = [];

  for (const entry of entries) {
    if (!isAudioFilename(entry.name)) continue;

    const objectPath = path.join(musicDir, entry.name);
    const filePath = await resolveStorageObjectPath(objectPath);
    if (!filePath) continue;

    const tags = await probeAudioTags(filePath);

    files.push({
      filename: entry.name,
      title: tags?.title ?? null,
      artist: tags?.artist ?? null,
      album: tags?.album ?? null,
      trackNumber: tags?.trackNumber ?? null,
    });
  }

  return buildMusicLibrary(files);
}
