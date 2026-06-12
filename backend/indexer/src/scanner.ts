import { readdir, stat } from "node:fs/promises";
import path from "node:path";
import {
  buildAudiobookTracks,
  buildMusicLibrary,
  isAudioFilename,
  pickAudiobookAuthor,
  titleFromSlug,
  type AudiobookFileScan,
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
    const books = await scanBooksInCycle(cyclePath);
    if (books.length === 0) continue;

    cycles.push({
      slug: cycleSlug,
      title: titleFromSlug(cycleSlug),
      description: null,
      bookOrder: books.map((book) => book.slug),
      books,
    });
  }

  return cycles.sort((a, b) => a.slug.localeCompare(b.slug));
}

async function scanBooksInCycle(cyclePath: string): Promise<ParsedBook[]> {
  const entries = await readdir(cyclePath, { withFileTypes: true });
  const books: ParsedBook[] = [];

  const bookDirs = entries
    .filter((entry) => entry.isDirectory())
    .sort((a, b) => a.name.localeCompare(b.name));

  for (const entry of bookDirs) {
    const bookSlug = entry.name;
    const bookPath = path.join(cyclePath, bookSlug);
    const scannedFiles = await scanAudiobookFiles(bookPath);
    if (scannedFiles.length === 0) continue;

    books.push({
      slug: bookSlug,
      contentType: "audiobook",
      title: titleFromSlug(bookSlug),
      author: pickAudiobookAuthor(scannedFiles),
      coverPath: null,
      tracks: buildAudiobookTracks(scannedFiles),
    });
  }

  return books;
}

async function scanAudiobookFiles(bookPath: string): Promise<AudiobookFileScan[]> {
  const entries = await readdir(bookPath, { withFileTypes: true });
  const filenames = entries
    .filter((entry) => entry.isFile() && isAudioFilename(entry.name))
    .map((entry) => entry.name)
    .sort((a, b) => a.localeCompare(b));

  const files: AudiobookFileScan[] = [];
  for (const filename of filenames) {
    const objectPath = path.join(bookPath, filename);
    const filePath = await resolveStorageObjectPath(objectPath);
    if (!filePath) {
      files.push({ filename, title: null, artist: null });
      continue;
    }

    const tags = await probeAudioTags(filePath);
    files.push({
      filename,
      title: tags?.title ?? null,
      artist: tags?.artist ?? null,
    });
  }

  return files;
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
