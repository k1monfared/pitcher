import { describe, expect, it } from "vitest";
import {
  abToggle,
  computePeaks,
  nextFrameTime,
  normalizeLoop,
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
