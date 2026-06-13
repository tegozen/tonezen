/** Map pointer X within a horizontal bar to a seek fraction in 0..1. */
export function seekFractionFromPointer(clientX: number, rectLeft: number, rectWidth: number): number {
  if (rectWidth <= 0) return 0;
  return Math.max(0, Math.min(1, (clientX - rectLeft) / rectWidth));
}
