import { describe, expect, it } from "vitest";
import { PitchAudioEngine } from "./engine";

function engineWith(buffers: { original?: object; shifted?: object }, mode: "original" | "variant" = "variant") {
  const e = new PitchAudioEngine();
  const anyE = e as unknown as Record<string, unknown>;
  anyE["originalBuffer"] = buffers.original ?? null;
  anyE["shiftedBuffer"] = buffers.shifted ?? null;
  anyE["mode"] = mode;
  return e;
}

const fakeBuffer = { duration: 10 } as unknown as AudioBuffer;

describe("activeBufferKind", () => {
  it("prefers the shifted buffer in variant mode", () => {
    const e = engineWith({ original: fakeBuffer, shifted: fakeBuffer }, "variant");
    expect(e.activeBufferKind()).toBe("variant");
  });

  it("falls back to original when no variant is loaded", () => {
    const e = engineWith({ original: fakeBuffer }, "variant");
    expect(e.activeBufferKind()).toBe("original");
  });

  it("falls back to shifted when original is missing", () => {
    const e = engineWith({ shifted: fakeBuffer }, "original");
    expect(e.activeBufferKind()).toBe("variant");
  });

  it("returns null when nothing is loaded", () => {
    const e = engineWith({});
    expect(e.activeBufferKind()).toBeNull();
  });
});

describe("hasOriginal/hasShifted", () => {
  it("reports loaded buffers", () => {
    const e = engineWith({ original: fakeBuffer });
    expect(e.hasOriginal).toBe(true);
    expect(e.hasShifted).toBe(false);
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
  it("drops the variant and returns to original mode", () => {
    const e = engineWith({ original: fakeBuffer, shifted: fakeBuffer }, "variant");
    e.clearShifted();
    expect(e.hasShifted).toBe(false);
    expect(e.currentMode).toBe("original");
    expect(e.activeBufferKind()).toBe("original");
  });
});
