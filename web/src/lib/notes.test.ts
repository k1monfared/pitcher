import { describe, expect, it } from "vitest";
import {
  centsBetweenHz,
  formatCents,
  hzToMidi,
  hzToNote,
  midiToHz,
  midiToNote,
  noteToHz,
  noteToMidi,
  ratioFromCents,
  semitonesToCents,
} from "./notes";

describe("ratioFromCents", () => {
  it("is 1 at 0 cents", () => {
    expect(ratioFromCents(0)).toBeCloseTo(1, 12);
  });
  it("is 2 at an octave", () => {
    expect(ratioFromCents(1200)).toBeCloseTo(2, 12);
    expect(ratioFromCents(-1200)).toBeCloseTo(0.5, 12);
  });
});

describe("hz/midi", () => {
  it("maps A4 to 69", () => {
    expect(hzToMidi(440)).toBeCloseTo(69, 9);
    expect(midiToHz(69)).toBeCloseTo(440, 9);
  });
  it("maps C4 correctly", () => {
    expect(midiToNote(60)).toBe("C4");
    expect(midiToHz(60)).toBeCloseTo(261.6255653, 6);
  });
});

describe("hzToNote", () => {
  it("names C#4 for 277.18", () => {
    const n = hzToNote(277.18);
    expect(n.name).toBe("C#4");
    expect(Math.abs(n.centsOff)).toBeLessThan(2);
  });
  it("reports -30 cents below A4", () => {
    const n = hzToNote(440 * Math.pow(2, -30 / 1200));
    expect(n.name).toBe("A4");
    expect(n.centsOff).toBeCloseTo(-30, 6);
  });
});

describe("intervals", () => {
  it("C#4 to C4 is -100", () => {
    const c = centsBetweenHz(midiToHz(61), midiToHz(60));
    expect(c).toBeCloseTo(-100, 6);
  });
  it("C#4 to G5 is 1800", () => {
    const c = centsBetweenHz(midiToHz(61), midiToHz(79));
    expect(c).toBeCloseTo(1800, 6);
  });
});

describe("fractional semitones", () => {
  it("1/8 semitone is 12.5 cents", () => {
    expect(semitonesToCents(1 / 8)).toBeCloseTo(12.5, 9);
  });
  it("5 + 1/16 semitones is 506.25 cents", () => {
    expect(semitonesToCents(5 + 1 / 16)).toBeCloseTo(506.25, 9);
  });
});

describe("noteToMidi", () => {
  it("parses notes and accidentals", () => {
    expect(noteToMidi("C4")).toBe(60);
    expect(noteToMidi("C#4")).toBe(61);
    expect(noteToMidi("Db4")).toBe(61);
    expect(noteToMidi("A4")).toBe(69);
    expect(noteToMidi("G5")).toBe(79);
  });
  it("rejects bad names", () => {
    expect(noteToMidi("H4")).toBeNull();
    expect(noteToMidi("C")).toBeNull();
    expect(noteToMidi("")).toBeNull();
  });
});

describe("noteToHz", () => {
  it("matches midiToHz", () => {
    expect(noteToHz("A4")).toBeCloseTo(440, 6);
    expect(noteToHz("C#4")).toBeCloseTo(midiToHz(61), 6);
  });
});

describe("formatCents", () => {
  it("shows sign", () => {
    expect(formatCents(-100)).toBe("-100");
    expect(formatCents(12.4)).toBe("+12");
    expect(formatCents(0)).toBe("+0");
  });
});
