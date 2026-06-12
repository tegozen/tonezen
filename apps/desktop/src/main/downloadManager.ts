import fs from "node:fs";
import path from "node:path";
import { pipeline } from "node:stream/promises";
import { apiV1Url } from "../shared/serverPaths.js";
import { LocalDatabase } from "./database.js";

export class DownloadManager {
  constructor(
    private downloadsRoot: string,
    private baseUrl: string,
    private getAccessToken: () => string | null,
  ) {
    fs.mkdirSync(downloadsRoot, { recursive: true });
  }

  async downloadTrack(bookId: string, trackId: string): Promise<string> {
    const token = this.getAccessToken();
    if (!token) throw new Error("Authentication required for downloads");

    const response = await fetch(apiV1Url(this.baseUrl, "/downloads/sign"), {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({ track_ids: [trackId] }),
    });
    if (!response.ok) throw new Error(`Sign failed: ${response.status}`);
    const json = (await response.json()) as { urls: Array<{ track_id: string; url: string }> };
    const signed = json.urls.find((u) => u.track_id === trackId);
    if (!signed) throw new Error("No signed URL returned");

    const bookDir = path.join(this.downloadsRoot, bookId);
    fs.mkdirSync(bookDir, { recursive: true });
    const targetPath = path.join(bookDir, `${trackId}.mp3`);

    const fileRes = await fetch(signed.url);
    if (!fileRes.ok || !fileRes.body) throw new Error(`Download failed: ${fileRes.status}`);
    await pipeline(fileRes.body as unknown as NodeJS.ReadableStream, fs.createWriteStream(targetPath));

    LocalDatabase.setTrackLocalPath(trackId, targetPath);
    return targetPath;
  }

  deleteLocalTrack(bookId: string, trackId: string): void {
    const filePath = path.join(this.downloadsRoot, bookId, `${trackId}.mp3`);
    if (fs.existsSync(filePath)) fs.unlinkSync(filePath);
    LocalDatabase.setTrackLocalPath(trackId, null);
  }
}
