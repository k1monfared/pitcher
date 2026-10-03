export interface AudioEngineOptions {
  wasmUrl?: string;
  processorUrl?: string;
}

export interface LoopRegion {
  start: number;
  end: number;
}

export function normalizeLoop(
  start: number,
  end: number,
  duration: number,
): LoopRegion {
  let s = Math.max(0, Math.min(start, duration));
  let e = Math.max(0, Math.min(end, duration));
  if (e < s) [s, e] = [e, s];
  return { start: s, end: e };
}

export function nextFrameTime(
  current: number,
  from: number,
  to: number,
  playing: boolean,
): number {
  if (!playing) return current;
  if (to > from && current >= to) return from;
  return current;
}

export function abToggle(current: "original" | "variant"): "original" | "variant" {
  return current === "original" ? "variant" : "original";
}

export function computePeaks(samples: Float32Array, buckets: number): number[] {
  if (buckets <= 0) return [];
  const size = Math.max(1, Math.floor(samples.length / buckets));
  const peaks: number[] = [];
  for (let b = 0; b < buckets; b++) {
    let max = 0;
    const start = b * size;
    const stop = Math.min(start + size, samples.length);
    for (let i = start; i < stop; i++) {
      const v = Math.abs(samples[i]);
      if (v > max) max = v;
    }
    peaks.push(max);
  }
  const top = Math.max(...peaks, 1e-6);
  return peaks.map((p) => p / top);
}
