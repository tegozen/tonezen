const UTF8_DECODER = new TextDecoder("utf-8", { fatal: true });
const CP1251_BYTE_BY_CHAR = new Map<string, number>();
const CP1251_HIGH =
  "ЂЃ‚ѓ„…†‡€‰Љ‹ЊЌЋЏђ‘’“”•–—\uFFFD™љ›њќћџ ЎўЈ¤Ґ¦§Ё©Є«¬\u00AD®Ї°±Ііґµ¶·ё№є»јЅѕї";

for (let byte = 0; byte <= 0x7f; byte += 1) CP1251_BYTE_BY_CHAR.set(String.fromCharCode(byte), byte);
for (let index = 0; index < CP1251_HIGH.length; index += 1) {
  CP1251_BYTE_BY_CHAR.set(CP1251_HIGH[index], 0x80 + index);
}
for (let code = 0x410; code <= 0x44f; code += 1) {
  CP1251_BYTE_BY_CHAR.set(String.fromCharCode(code), 0xc0 + code - 0x410);
}

function suspiciousScore(value: string): number {
  const markers = value.match(/[РС][\u0400-\u04ff]|[ÐÑ][\u0080-\u00ff]/g)?.length ?? 0;
  const westernMarkers = value.match(/[ÐÑ]/g)?.length ?? 0;
  const replacements = value.match(/\uFFFD/g)?.length ?? 0;
  return markers * 4 + westernMarkers * 2 + replacements * 10;
}

function decodeBytes(bytes: Uint8Array): string | null {
  try {
    return UTF8_DECODER.decode(bytes);
  } catch {
    return null;
  }
}

function recoverCp1251(value: string): string | null {
  const bytes: number[] = [];
  for (const char of value) {
    const byte = CP1251_BYTE_BY_CHAR.get(char);
    if (byte == null) return null;
    bytes.push(byte);
  }
  return decodeBytes(Uint8Array.from(bytes));
}

const CP1252_SPECIAL = new Map<string, number>([
  ["€", 0x80], ["‚", 0x82], ["ƒ", 0x83], ["„", 0x84], ["…", 0x85], ["†", 0x86],
  ["‡", 0x87], ["ˆ", 0x88], ["‰", 0x89], ["Š", 0x8a], ["‹", 0x8b], ["Œ", 0x8c],
  ["Ž", 0x8e], ["‘", 0x91], ["’", 0x92], ["“", 0x93], ["”", 0x94], ["•", 0x95],
  ["–", 0x96], ["—", 0x97], ["˜", 0x98], ["™", 0x99], ["š", 0x9a], ["›", 0x9b],
  ["œ", 0x9c], ["ž", 0x9e], ["Ÿ", 0x9f],
]);

function recoverLatin1(value: string): string | null {
  const bytes: number[] = [];
  for (const char of value) {
    const code = char.codePointAt(0)!;
    const byte = code <= 255 ? code : CP1252_SPECIAL.get(char);
    if (byte == null) return null;
    bytes.push(byte);
  }
  return decodeBytes(Uint8Array.from(bytes));
}

/** Repairs common UTF-8-as-Windows-1251/Latin-1 mojibake without touching valid text. */
export function repairMojibake(value: string): string {
  const original = value.trim();
  if (!original || suspiciousScore(original) === 0) return original;
  const candidates = [recoverCp1251(original), recoverLatin1(original)].filter(
    (candidate): candidate is string => Boolean(candidate?.trim()) && !candidate!.includes("\uFFFD"),
  );
  return candidates.reduce(
    (best, candidate) => suspiciousScore(candidate) < suspiciousScore(best) ? candidate.trim() : best,
    original,
  );
}
