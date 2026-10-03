import { describe, expect, it } from "vitest";
import {
  centerOn,
  clampView,
  followView,
  isTextEntry,
  keyAction,
  MIN_VIEW_SPAN,
  panBy,
  zoomAt,
  zoomCenter,
} from "./view";

describe("clampView", () => {
  it("returns null for full coverage", () => {
    expect(clampView(0, 100, 100)).toBeNull();
    expect(clampView(-10, 200, 100)).toBeNull();
  });
  it("clamps into range", () => {
    expect(clampView(-5, 15, 100)).toEqual({ start: 0, end: 20 });
    expect(clampView(90, 110, 100)).toEqual({ start: 80, end: 100 });
  });
  it("enforces a minimum span", () => {
    const v = clampView(10, 10.1, 100)!;
    expect(v.end - v.start).toBeCloseTo(MIN_VIEW_SPAN, 6);
  });
  it("returns null for empty duration", () => {
    expect(clampView(0, 10, 0)).toBeNull();
  });
});

describe("zoomAt", () => {
  it("zooms in around the cursor", () => {
    const v = zoomAt({ start: 0, end: 100 }, 25, 0.5, 100)!;
    expect(v.end - v.start).toBeCloseTo(50, 6);
    expect(v.start).toBeCloseTo(12.5, 6);
  });
  it("zooms out", () => {
    const v = zoomAt({ start: 20, end: 40 }, 30, 2, 100)!;
    expect(v.end - v.start).toBeCloseTo(40, 6);
    expect(v.start).toBeCloseTo(10, 6);
  });
  it("returns null when zooming out past full", () => {
    expect(zoomAt({ start: 0, end: 100 }, 50, 2, 100)).toBeNull();
    expect(zoomAt(null, 50, 0.5, 100)).not.toBeNull();
  });
  it("keeps the view inside the track", () => {
    const v = zoomAt({ start: 90, end: 100 }, 99, 0.5, 100)!;
    expect(v.end).toBeLessThanOrEqual(100);
    expect(v.start).toBeGreaterThanOrEqual(0);
  });
});

describe("zoomCenter", () => {
  it("zooms around the middle", () => {
    const v = zoomCenter({ start: 0, end: 100 }, 0.5, 100)!;
    expect(v).toEqual({ start: 25, end: 75 });
  });
});

describe("panBy", () => {
  it("shifts the view", () => {
    expect(panBy({ start: 10, end: 30 }, 5, 100)).toEqual({ start: 15, end: 35 });
    expect(panBy({ start: 10, end: 30 }, -5, 100)).toEqual({ start: 5, end: 25 });
  });
  it("clamps at the edges", () => {
    expect(panBy({ start: 10, end: 30 }, -50, 100)).toEqual({ start: 0, end: 20 });
    expect(panBy({ start: 80, end: 100 }, 50, 100)).toEqual({ start: 80, end: 100 });
  });
  it("returns null when already full", () => {
    expect(panBy(null, 5, 100)).toBeNull();
  });
});

describe("centerOn", () => {
  it("centers the span on t", () => {
    expect(centerOn({ start: 0, end: 20 }, 50, 100)).toEqual({ start: 40, end: 60 });
  });
  it("clamps at edges", () => {
    expect(centerOn({ start: 0, end: 20 }, 5, 100)).toEqual({ start: 0, end: 20 });
    expect(centerOn({ start: 0, end: 20 }, 95, 100)).toEqual({ start: 80, end: 100 });
  });
  it("returns null when full", () => {
    expect(centerOn(null, 50, 100)).toBeNull();
  });
});

describe("followView", () => {
  it("leaves the view alone when inside margins", () => {
    const v = { start: 0, end: 100 };
    expect(followView(v, 50, 200)).toBe(v);
    expect(followView(v, 10, 200)).toBe(v);
    expect(followView(v, 90, 200)).toBe(v);
  });
  it("shifts minimally to restore the margin", () => {
    expect(followView({ start: 50, end: 150 }, 55, 200)).toEqual({ start: 45, end: 145 });
    expect(followView({ start: 50, end: 150 }, 145, 200)).toEqual({ start: 55, end: 155 });
  });
  it("clamps at track edges", () => {
    expect(followView({ start: 70, end: 100 }, 99, 100)).toEqual({ start: 70, end: 100 });
    expect(followView({ start: 0, end: 30 }, 1, 100)).toEqual({ start: 0, end: 30 });
  });
  it("passes through null", () => {
    expect(followView(null, 50, 100)).toBeNull();
  });
});

describe("keyAction", () => {
  it("maps arrows to seek", () => {
    expect(keyAction("ArrowLeft", false, false)).toEqual({ type: "seek", delta: -1 });
    expect(keyAction("ArrowRight", false, false)).toEqual({ type: "seek", delta: 1 });
    expect(keyAction("ArrowLeft", true, false)).toEqual({ type: "seek", delta: -5 });
    expect(keyAction("ArrowRight", false, true)).toEqual({ type: "seek", delta: 15 });
  });
  it("maps up/down to pitch nudge", () => {
    expect(keyAction("ArrowUp", false, false)).toEqual({ type: "nudge", delta: 1 });
    expect(keyAction("ArrowDown", false, false)).toEqual({ type: "nudge", delta: -1 });
    expect(keyAction("ArrowUp", true, false)).toEqual({ type: "nudge", delta: 5 });
    expect(keyAction("ArrowDown", false, true)).toEqual({ type: "nudge", delta: -10 });
  });
  it("ignores other keys", () => {
    expect(keyAction("a", false, false)).toBeNull();
    expect(keyAction("Enter", true, false)).toBeNull();
  });
});

describe("isTextEntry", () => {
  it("rejects non elements", () => {
    expect(isTextEntry(null)).toBe(false);
  });
});
