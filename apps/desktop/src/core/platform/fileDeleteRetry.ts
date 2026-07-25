import fsPromises from "node:fs/promises";

const DEFAULT_ATTEMPTS = 8;
const DEFAULT_DELAY_MS = 50;
const RETRY_CODES = new Set(["EBUSY", "EPERM"]);

export async function delay(ms: number): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, ms));
}

export async function retryFileDelete<T>(
  operation: () => Promise<T>,
  options?: { attempts?: number; delayMs?: number },
): Promise<T> {
  const attempts = options?.attempts ?? DEFAULT_ATTEMPTS;
  const delayMs = options?.delayMs ?? DEFAULT_DELAY_MS;

  for (let attempt = 0; attempt < attempts; attempt++) {
    try {
      return await operation();
    } catch (error) {
      const code = (error as NodeJS.ErrnoException).code;
      if (code === "ENOENT") {
        return undefined as T;
      }
      if (attempt === attempts - 1 || !code || !RETRY_CODES.has(code)) {
        throw error;
      }
      await delay(delayMs * (attempt + 1));
    }
  }

  throw new Error("retryFileDelete exhausted attempts");
}

export async function unlinkWithRetry(filePath: string): Promise<void> {
  await retryFileDelete(async () => {
    await fsPromises.unlink(filePath);
  });
}

export async function rmWithRetry(targetPath: string): Promise<void> {
  await retryFileDelete(async () => {
    await fsPromises.rm(targetPath, { recursive: true, force: true });
  });
}
