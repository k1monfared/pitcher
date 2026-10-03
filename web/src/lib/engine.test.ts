import { describe, expect, it } from "vitest";
import { PitchAudioEngine } from "./engine";

function engineWith(buffers: { original?: object; shifted?: object }) {
  const e = new PitchAudioEngine();
  const anyE = e as unknown as Record<string, unknown>;
  anyE["originalBuffer"] = buffers.original ?? null;
  anyE["shiftedBuffer"] = buffers.shifted ?? null;
  return e;
}

const fakeBuffer = { duration: 10 } as unknown as AudioBuffer;

describe("selection", () => {
  it("defaults to original", () => {
    const e = engineWith({ original: fakeBuffer, shifted: fakeBuffer });
    expect(e.selection).toBe("original");
    expect(e.activeBufferKind()).toBe("original");
  });

  it("select switches to the variant buffer", () => {
    const e = engineWith({ original: fakeBuffer, shifted: fakeBuffer });
    e.select("variant");
    expect(e.selection).toBe("variant");
    expect(e.activeBufferKind()).toBe("variant");
  });

  it("falls back to original when no variant is loaded", () => {
    const e = engineWith({ original: fakeBuffer });
    e.select("variant");
    expect(e.activeBufferKind()).toBe("original");
  });

  it("falls back to shifted when original is missing", () => {
    const e = engineWith({ shifted: fakeBuffer });
    expect(e.activeBufferKind()).toBe("variant");
  });

  it("returns null when nothing is loaded", () => {
    const e = engineWith({});
    expect(e.activeBufferKind()).toBeNull();
  });

  it("select is a no-op for the current selection", () => {
    const e = engineWith({ original: fakeBuffer });
    e.select("original");
    expect(e.selection).toBe("original");
  });
});

describe("hasOriginal/hasShifted", () => {
  it("reports loaded buffers", () => {
    const e = engineWith({ original: fakeBuffer });
    expect(e.hasOriginal).toBe(true);
    expect(e.hasShifted).toBe(false);
  });
});

describe("absolute fader with base compensation", () => {
  it("defaults to base 0, ratio passes through", () => {
    const e = engineWith({});
    expect(e.effectivePitchRatio(1)).toBeCloseTo(1, 9);
  });

  it("variant at base plays unmodified when fader matches", () => {
    const e = engineWith({ shifted: fakeBuffer });
    e.setBaseCents(-600);
    e.setPitchRatio(Math.pow(2, -600 / 1200));
    expect(e.effectivePitchRatio(e.currentPitchRatio)).toBeCloseTo(1, 9);
  });

  it("moving the fader above base shifts up from the variant", () => {
    const e = engineWith({ shifted: fakeBuffer });
    e.setBaseCents(-600);
    e.setPitchRatio(Math.pow(2, -500 / 1200));
    expect(e.effectivePitchRatio(e.currentPitchRatio)).toBeCloseTo(
      Math.pow(2, 100 / 1200),
      9,
    );
  });

  it("total from original always equals the fader", () => {
    const e = engineWith({ original: fakeBuffer, shifted: fakeBuffer });
    for (const [base, fader] of [[-600, -600], [-600, -500], [0, 300], [200, -100]] as const) {
      e.setBaseCents(base);
      const ratio = Math.pow(2, fader / 1200);
      const total = Math.pow(2, base / 1200) * e.effectivePitchRatio(ratio);
      expect(total).toBeCloseTo(ratio, 9);
    }
  });
});

describe("tempo", () => {
  it("defaults to 1 and stores set values", () => {
    const e = engineWith({});
    expect(e.currentTempo).toBe(1);
    e.setTempo(1.5);
    expect(e.currentTempo).toBe(1.5);
  });
});

describe("clearShifted", () => {
  it("drops the variant and returns to original selection", () => {
    const e = engineWith({ original: fakeBuffer, shifted: fakeBuffer });
    e.select("variant");
    e.clearShifted();
    expect(e.hasShifted).toBe(false);
    expect(e.selection).toBe("original");
    expect(e.activeBufferKind()).toBe("original");
  });
});
