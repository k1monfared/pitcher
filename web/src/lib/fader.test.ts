import { describe, expect, it } from "vitest";
import { centsFromDrag, centsToY, clampCents, snapCents, yToCents } from "./fader";

describe("snapCents", () => {
  it("snaps to semitone", () => {
    expect(snapCents(112, 100)).toBe(100);
    expect(snapCents(151, 100)).toBe(200);
  });
  it("snaps to 10 and 1", () => {
    expect(snapCents(14, 10)).toBe(10);
    expect(snapCents(14, 1)).toBe(14);
  });
  it("off rounds only", () => {
    expect(snapCents(14.7, 0)).toBe(15);
  });
});

describe("clampCents", () => {
  it("clamps to range", () => {
    expect(clampCents(1500, 1200)).toBe(1200);
    expect(clampCents(-1500, 1200)).toBe(-1200);
    expect(clampCents(50, 1200)).toBe(50);
  });
});

describe("drag", () => {
  it("drag up increases cents", () => {
    const c = centsFromDrag(0, -120, 120);
    expect(c).toBeCloseTo(100, 6);
  });
  it("drag down decreases cents", () => {
    const c = centsFromDrag(0, 120, 120);
    expect(c).toBeCloseTo(-100, 6);
  });
});

describe("y mapping", () => {
  it("centers zero at mid height", () => {
    expect(centsToY(0, 1200, 400)).toBeCloseTo(200, 6);
    expect(yToCents(200, 1200, 400)).toBeCloseTo(0, 6);
  });
  it("round trips", () => {
    for (const c of [-1200, -37, 0, 250, 1200]) {
      expect(yToCents(centsToY(c, 1200, 500), 1200, 500)).toBeCloseTo(c, 6);
    }
  });
  it("top is max, bottom is min", () => {
    expect(yToCents(0, 1200, 400)).toBeCloseTo(1200, 6);
    expect(yToCents(400, 1200, 400)).toBeCloseTo(-1200, 6);
  });
});
