/**
 * Register cycles/ and music/ layout in Storage via API (Studio reads storage.objects).
 */

export const LAYOUT_DIRS = ["cycles", "music"];
export const DEFAULT_BUCKET = "content";

const README = {
  cycles: `Аудиокниги (cycles)

Структура:
  cycles/{cycle-slug}/cycle.json
  cycles/{cycle-slug}/books/{book-slug}/book.json
  cycles/{cycle-slug}/books/{book-slug}/audio/*.mp3

См. docs/content-layout.md в репозитории Tonezen.
`,
  music: `Музыка (music)

Загрузите аудиофайлы (.mp3, .flac, …) прямо в music/.
Метаданные (название, исполнитель, альбом) читаются из тегов файла.
Без тегов используется имя файла.

См. docs/content-layout.md в репозитории Tonezen.
`,
};

function authHeaders(serviceKey) {
  return {
    Authorization: `Bearer ${serviceKey}`,
    apikey: serviceKey,
    "Content-Type": "text/plain",
    "x-upsert": "true",
  };
}

export async function uploadLayoutObject({
  storageUrl,
  serviceKey,
  bucket = DEFAULT_BUCKET,
  objectPath,
  body,
  fetchFn = fetch,
}) {
  const base = storageUrl.replace(/\/$/, "");
  const res = await fetchFn(`${base}/object/${bucket}/${objectPath}`, {
    method: "POST",
    headers: authHeaders(serviceKey),
    body,
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Upload ${objectPath} failed (${res.status}): ${text}`);
  }
  return objectPath;
}

export async function ensureContentLayout({
  storageUrl,
  serviceKey,
  bucket = DEFAULT_BUCKET,
  fetchFn = fetch,
}) {
  const created = [];

  for (const dir of LAYOUT_DIRS) {
    const objectPath = `${dir}/README.txt`;
    await uploadLayoutObject({
      storageUrl,
      serviceKey,
      bucket,
      objectPath,
      body: README[dir],
      fetchFn,
    });
    created.push(objectPath);
  }

  return { created, ensuredDirs: LAYOUT_DIRS, bucket };
}

async function main() {
  const storageUrl = process.env.STORAGE_URL ?? "http://storage:5000";
  const serviceKey = process.env.SERVICE_ROLE_KEY;
  const bucket = process.env.STORAGE_BUCKET ?? DEFAULT_BUCKET;

  if (!serviceKey) {
    throw new Error("SERVICE_ROLE_KEY is required");
  }

  const result = await ensureContentLayout({ storageUrl, serviceKey, bucket });
  console.log(
    `Storage layout in bucket '${bucket}': ${result.created.join(", ")}`,
  );
}

if (import.meta.url === new URL(process.argv[1], "file:").href) {
  main().catch((err) => {
    console.error(err);
    process.exit(1);
  });
}
