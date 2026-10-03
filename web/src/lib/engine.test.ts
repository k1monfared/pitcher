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

  it("select switches buffers", () => {
    const e = engineWith({ original: fakeBuffer, shifted: fakeBuffer });
    e.select("variant", -600);
    expect(e.selection).toBe("variant");
    expect(e.activeBufferKind()).toBe("variant");
    e.select("original");
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
});

describe("direct file playback", () => {
  it("plays the variant file untouched when fader matches", () => {
    const e = engineWith({ original: fakeBuffer, shifted: fakeBuffer });
    e.select("variant", -600);
    e.setPitchCents(-600);
    expect(e.useDirectFile()).toBe(true);
  });

  it("uses live preview when the fader moves off the variant", () => {
    const e = engineWith({ original: fakeBuffer, shifted: fakeBuffer });
    e.select("variant", -600);
    e.setPitchCents(-550);
    expect(e.useDirectFile()).toBe(false);
  });

  it("uses live preview when tempo is not 1", () => {
    const e = engineWith({ original: fakeBuffer, shifted: fakeBuffer });
    e.select("variant", -600);
    e.setPitchCents(-600);
    e.setTempo(1.5);
    expect(e.useDirectFile()).toBe(false);
  });

  it("never uses direct file for original selection", () => {
    const e = engineWith({ original: fakeBuffer, shifted: fakeBuffer });
    e.setPitchCents(0);
    expect(e.useDirectFile()).toBe(false);
  });
});

describe("pitch and tempo state", () => {
  it("stores fader cents and tempo", () => {
    const e = engineWith({});
    expect(e.currentCents).toBe(0);
    expect(e.currentTempo).toBe(1);
    e.setPitchCents(-100);
    e.setTempo(1.5);
    expect(e.currentCents).toBe(-100);
    expect(e.currentTempo).toBe(1.5);
  });
});

describe("hasOriginal/hasShifted", () => {
  it("reports loaded buffers", () => {
    const e = engineWith({ original: fakeBuffer });
    expect(e.hasOriginal).toBe(true);
    expect(e.hasShifted).toBe(false);
  });
});

describe("clearShifted", () => {
  it("drops the variant and returns to original selection", () => {
    const e = engineWith({ original: fakeBuffer, shifted: fakeBuffer });
    e.select("variant", -100);
    e.clearShifted();
    expect(e.hasShifted).toBe(false);
    expect(e.selection).toBe("original");
    expect(e.activeBufferKind()).toBe("original");
  });
});
