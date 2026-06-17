export const SPECTRUM_BAR_COUNT = 26;

export interface SpectrumBar {
  level: number;
  delayStep: number;
}

export function buildSpectrumBars(seed: string, count = SPECTRUM_BAR_COUNT): SpectrumBar[] {
  if (count <= 0) return [];
  let state = hashSpectrumSeed(seed);
  return Array.from({ length: count }, (_, index) => {
    state = mixSpectrumState((state + index) | 0);
    const positive = state & 0x7fffffff;
    return {
      level: 2 + (positive % 8),
      delayStep: (positive >>> 4) % 8,
    };
  });
}

function hashSpectrumSeed(seed: string): number {
  let hash = 0x811c9dc5;
  for (let index = 0; index < seed.length; index += 1) {
    hash = Math.imul(hash ^ seed.charCodeAt(index), 16777619);
  }
  return hash | 0;
}

function mixSpectrumState(value: number): number {
  let state = value | 0;
  state ^= state >>> 16;
  state = Math.imul(state, 0x85ebca6b);
  state ^= state >>> 13;
  state = Math.imul(state, 0xc2b2ae35);
  return (state ^ (state >>> 16)) | 0;
}
