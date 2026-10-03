export const NOTE_NAMES = [
  "C",
  "C#",
  "D",
  "D#",
  "E",
  "F",
  "F#",
  "G",
  "G#",
  "A",
  "A#",
  "B",
] as const;

export interface NoteReading {
  name: string;
  midi: number;
  centsOff: number;
}

export function ratioFromCents(cents: number): number {
  return Math.pow(2, cents / 1200);
}

export function hzToMidi(hz: number): number {
  return 69 + 12 * Math.log2(hz / 440);
}

export function midiToHz(midi: number): number {
  return 440 * Math.pow(2, (midi - 69) / 12);
}

export function midiToNote(midi: number): string {
  const m = Math.round(midi);
  const idx = ((m % 12) + 12) % 12;
  const octave = Math.floor(m / 12) - 1;
  return `${NOTE_NAMES[idx]}${octave}`;
}

export function hzToNote(hz: number): NoteReading {
  const m = hzToMidi(hz);
  const rounded = Math.round(m);
  return {
    name: midiToNote(rounded),
    midi: rounded,
    centsOff: 100 * (m - rounded),
  };
}

export function noteToMidi(name: string): number | null {
  const m = name.trim().match(/^([A-Ga-g])(#|b)?(-?\d+)$/);
  if (!m) return null;
  const letters: Record<string, number> = { C: 0, D: 2, E: 4, F: 5, G: 7, A: 9, B: 11 };
  const base = letters[m[1].toUpperCase()];
  const acc = m[2] === "#" ? 1 : m[2] === "b" ? -1 : 0;
  return 12 * (parseInt(m[3], 10) + 1) + base + acc;
}

export function noteToHz(name: string): number | null {
  const midi = noteToMidi(name);
  return midi === null ? null : midiToHz(midi);
}

export function centsBetweenHz(source: number, target: number): number {
  return 1200 * Math.log2(target / source);
}

export function semitonesToCents(semitones: number): number {
  return semitones * 100;
}

export function formatCents(cents: number): string {
  const c = Math.round(cents);
  return `${c >= 0 ? "+" : ""}${c}`;
}
