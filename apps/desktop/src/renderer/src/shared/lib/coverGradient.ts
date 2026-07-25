const AUDIOBOOK_GRADIENTS = [
  "linear-gradient(180deg, #061826 0%, #102A43 50%, #0B1120 100%)",
  "linear-gradient(180deg, #33210E 0%, #C78538 50%, #FAECD2 100%)",
  "linear-gradient(180deg, #461C12 0%, #D94D28 50%, #7F1D1D 100%)",
];

const TRACK_GRADIENTS = [
  "linear-gradient(180deg, #103344 0%, #9BD6E3 50%, #0F172A 100%)",
  "linear-gradient(180deg, #1D1712 0%, #70513A 50%, #111827 100%)",
  "linear-gradient(180deg, #0F3B39 0%, #69B3A2 50%, #10201F 100%)",
  "linear-gradient(180deg, #2A1B3D 0%, #7C5CBF 50%, #1A1025 100%)",
];

function hashIndex(seed: string, count: number): number {
  let hash = 0;
  for (let i = 0; i < seed.length; i += 1) {
    hash = (hash * 31 + seed.charCodeAt(i)) | 0;
  }
  return Math.abs(hash) % count;
}

export function bookCoverGradient(seed: string, audiobook: boolean): string {
  const variants = audiobook ? AUDIOBOOK_GRADIENTS : TRACK_GRADIENTS;
  return variants[hashIndex(seed, variants.length)];
}

export function trackCoverGradient(seed: string): string {
  return TRACK_GRADIENTS[hashIndex(seed, TRACK_GRADIENTS.length)];
}
