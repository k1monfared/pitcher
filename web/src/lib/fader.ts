export interface SnapMode {
  label: string;
  cents: number;
}

export const SNAP_MODES: SnapMode[] = [
  { label: "off", cents: 0 },
  { label: "semitone", cents: 100 },
  { label: "10c", cents: 10 },
  { label: "1c", cents: 1 },
];

export function snapCents(value: number, step: number): number {
  if (step <= 0) return Math.round(value);
  return Math.round(value / step) * step;
}

export function clampCents(value: number, range: number): number {
  return Math.max(-range, Math.min(range, value));
}

export function centsFromDrag(
  startCents: number,
  deltaY: number,
  pixelsPerSemitone: number,
): number {
  const semitones = -deltaY / pixelsPerSemitone;
  return startCents + semitones * 100;
}

export function centsToY(cents: number, range: number, height: number): number {
  const frac = (range - cents) / (2 * range);
  return frac * height;
}

export function yToCents(y: number, range: number, height: number): number {
  const frac = y / height;
  return range - frac * 2 * range;
}
