import { describe, expect, it } from "vitest";
import {
  abToggle,
  computePeaks,
  formatTempo,
  nextFrameTime,
  normalizeLoop,
  SPEED_OPTIONS,
  tempoToTimeRatio,
} from "./audio";

describe("normalizeLoop", () => {
  it("orders start and end", () => {
    expect(normalizeLoop(5, 2, 10)).toEqual({ start: 2, end: 5 });
  });
  it("clamps to duration", () => {
    expect(normalizeLoop(-1, 99, 10)).toEqual({ start: 0, end: 10 });
  });
});

describe("nextFrameTime", () => {
  it("loops back to start", () => {
    expect(nextFrameTime(5.1, 2, 5, true)).toBe(2);
  });
  it("holds when not playing", () => {
    expect(nextFrameTime(5.1, 2, 5, false)).toBe(5.1);
  });
  it("passes through inside loop", () => {
    expect(nextFrameTime(3, 2, 5, true)).toBe(3);
  });
});

describe("abToggle", () => {
  it("toggles", () => {
    expect(abToggle("original")).toBe("variant");
    expect(abToggle("variant")).toBe("original");
  });
});

describe("SPEED_OPTIONS", () => {
  it("contains the requested rates in order", () => {
    expect(SPEED_OPTIONS).toEqual([
      0.5, 0.6, 0.7, 0.8, 0.9, 1, 1.1, 1.2, 1.3, 1.4, 1.5, 1.75, 2,
    ]);
  });
});

describe("tempoToTimeRatio", () => {
  it("inverts tempo", () => {
    expect(tempoToTimeRatio(2)).toBeCloseTo(0.5, 9);
    expect(tempoToTimeRatio(0.5)).toBeCloseTo(2, 9);
    expect(tempoToTimeRatio(1)).toBeCloseTo(1, 9);
  });
});

describe("formatTempo", () => {
  it("formats with x suffix", () => {
    expect(formatTempo(1)).toBe("1x");
    expect(formatTempo(1.75)).toBe("1.75x");
  });
});

describe("computePeaks", () => {
  it("normalizes to 1", () => {
    const samples = new Float32Array([0, 0.5, 1, -1, 0.2, 0]);
    const peaks = computePeaks(samples, 3);
    expect(peaks.length).toBe(3);
    expect(Math.max(...peaks)).toBeCloseTo(1, 6);
  });
  it("returns empty for zero buckets", () => {
    expect(computePeaks(new Float32Array([1, 2]), 0)).toEqual([]);
  });
});
