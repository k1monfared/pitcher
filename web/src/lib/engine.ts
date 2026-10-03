import { PitchShifter } from "soundtouchjs";
import type { LoopRegion } from "./audio";

export type Selection = "original" | "variant";

export interface EngineCallbacks {
  onPosition?: (t: number) => void;
}

export const PREVIEW_ENGINE = "soundtouch";
export const RENDER_ENGINE = "rubberband";

const TAIL_SECONDS = 0.25;
const DIRECT_EPSILON_CENTS = 0.5;
const DSP_BUFFER_SIZE = 4096;

export class PitchAudioEngine {
  private ctx: AudioContext | null = null;
  private originalBuffer: AudioBuffer | null = null;
  private shiftedBuffer: AudioBuffer | null = null;
  private shifter: PitchShifter | null = null;
  private shifterBuffer: AudioBuffer | null = null;
  private source: AudioBufferSourceNode | null = null;
  private startedAt = 0;
  private offset = 0;
  private playing = false;
  private selected: Selection = "original";
  private refCents = 0;
  private loop: LoopRegion | null = null;
  private rafId = 0;
  private faderCents = 0;
  private tempo = 1;

  constructor(private callbacks: EngineCallbacks = {}) {}

  private ensureContext(): AudioContext {
    if (!this.ctx) this.ctx = new AudioContext();
    return this.ctx;
  }

  async loadOriginal(arrayBuffer: ArrayBuffer): Promise<void> {
    const t = this.currentTime();
    const wasPlaying = this.playing;
    this.stopAll();
    this.playing = false;
    const ctx = this.ensureContext();
    this.originalBuffer = await ctx.decodeAudioData(arrayBuffer.slice(0));
    this.dropShifter();
    this.offset = Math.min(t, this.originalBuffer.duration);
    if (wasPlaying) this.play();
  }

  async loadShifted(arrayBuffer: ArrayBuffer): Promise<void> {
    const t = this.currentTime();
    const wasPlaying = this.playing;
    this.stopAll();
    this.playing = false;
    const ctx = this.ensureContext();
    this.shiftedBuffer = await ctx.decodeAudioData(arrayBuffer.slice(0));
    this.offset = Math.min(t, this.shiftedBuffer.duration);
    if (wasPlaying) this.play();
  }

  clearShifted(): void {
    this.stopAll();
    this.playing = false;
    this.shiftedBuffer = null;
    if (this.selected === "variant") this.selected = "original";
  }

  get duration(): number {
    return this.originalBuffer?.duration ?? 0;
  }

  get selection(): Selection {
    return this.selected;
  }

  get currentTempo(): number {
    return this.tempo;
  }

  get currentCents(): number {
    return this.faderCents;
  }

  get hasOriginal(): boolean {
    return this.originalBuffer !== null;
  }

  get hasShifted(): boolean {
    return this.shiftedBuffer !== null;
  }

  select(kind: Selection, refCents = 0): void {
    if (this.selected === kind && this.refCents === refCents) return;
    const t = this.playing ? this.currentTime() : this.offset;
    this.selected = kind;
    this.refCents = refCents;
    if (this.playing) {
      this.stopAll();
      this.playing = false;
      this.offset = t;
      this.play();
    }
  }

  activeBufferKind(): "original" | "variant" | null {
    if (this.selected === "variant" && this.shiftedBuffer) return "variant";
    if (this.originalBuffer) return "original";
    if (this.shiftedBuffer) return "variant";
    return null;
  }

  useDirectFile(): boolean {
    return (
      this.selected === "variant" &&
      this.shiftedBuffer !== null &&
      this.tempo === 1 &&
      Math.abs(this.faderCents - this.refCents) < DIRECT_EPSILON_CENTS
    );
  }

  setLoop(loop: LoopRegion | null): void {
    this.loop = loop;
    this.restartAtCurrent();
  }

  setPitchCents(cents: number): void {
    this.faderCents = cents;
    if (!this.playing) return;
    if (this.useDirectFile()) {
      this.restartAtCurrent();
    } else if (this.shifter) {
      this.shifter.pitchSemitones = cents / 100;
    }
  }

  setTempo(tempo: number): void {
    this.tempo = tempo;
    if (!this.playing) return;
    if (this.useDirectFile()) {
      this.restartAtCurrent();
    } else if (this.shifter) {
      this.shifter.tempo = tempo;
    }
  }

  play(): void {
    if (this.playing) return;
    const ctx = this.ensureContext();
    void ctx.resume();
    this.stopAll();

    if (this.useDirectFile() && this.shiftedBuffer) {
      const source = ctx.createBufferSource();
      source.buffer = this.shiftedBuffer;
      source.connect(ctx.destination);
      const startAt = Math.min(this.offset, this.shiftedBuffer.duration);
      source.start(0, startAt);
      this.startedAt = ctx.currentTime - startAt;
      this.source = source;
    } else {
      if (!this.originalBuffer) return;
      this.ensureShifter(ctx);
      if (!this.shifter) return;
      this.shifter.pitchSemitones = this.faderCents / 100;
      this.shifter.tempo = this.tempo;
      this.shifter.percentagePlayed =
        this.originalBuffer.duration > 0 ? this.offset / this.originalBuffer.duration : 0;
      this.shifter.connect(ctx.destination);
    }
    this.playing = true;
    this.tick();
  }

  pause(): void {
    if (!this.playing) return;
    this.offset = this.currentTime();
    this.stopAll();
    this.playing = false;
    cancelAnimationFrame(this.rafId);
  }

  seek(t: number): void {
    const dur = this.activeDuration();
    this.offset = dur > 0 ? Math.max(0, Math.min(t, dur)) : Math.max(0, t);
    if (this.playing) {
      this.stopAll();
      this.playing = false;
      this.play();
    }
    this.callbacks.onPosition?.(this.offset);
  }

  currentTime(): number {
    if (!this.playing || !this.ctx) return this.offset;
    if (this.shifter && !this.useDirectFile()) return this.shifter.timePlayed;
    return this.ctx.currentTime - this.startedAt;
  }

  private activeDuration(): number {
    if (this.useDirectFile() && this.shiftedBuffer) return this.shiftedBuffer.duration;
    return this.originalBuffer?.duration ?? 0;
  }

  private ensureShifter(ctx: AudioContext): void {
    if (!this.originalBuffer) return;
    if (!this.shifter || this.shifterBuffer !== this.originalBuffer) {
      this.dropShifter();
      this.shifter = new PitchShifter(ctx, this.originalBuffer, DSP_BUFFER_SIZE, () =>
        this.finish(),
      );
      this.shifterBuffer = this.originalBuffer;
    }
  }

  private dropShifter(): void {
    if (this.shifter) {
      try {
        this.shifter.disconnect();
      } catch {
        /* already gone */
      }
      this.shifter = null;
      this.shifterBuffer = null;
    }
  }

  private tick = () => {
    if (!this.playing) return;
    const t = this.currentTime();
    if (this.loop && t >= this.loop.end) {
      this.seek(this.loop.start);
      this.callbacks.onPosition?.(this.loop.start);
      this.rafId = requestAnimationFrame(this.tick);
      return;
    }
    this.callbacks.onPosition?.(t);
    const dur = this.activeDuration();
    if (!this.loop && dur > 0 && t >= dur + TAIL_SECONDS) {
      this.finish();
      return;
    }
    this.rafId = requestAnimationFrame(this.tick);
  };

  private finish(): void {
    if (!this.playing) return;
    this.stopAll();
    this.playing = false;
    cancelAnimationFrame(this.rafId);
    this.offset = this.activeDuration();
    this.callbacks.onPosition?.(this.offset);
  }

  private restartAtCurrent(): void {
    if (!this.playing) return;
    const t = this.currentTime();
    this.stopAll();
    this.playing = false;
    this.offset = t;
    this.play();
  }

  private stopAll(): void {
    if (this.source) {
      try {
        this.source.stop();
      } catch {
        /* already stopped */
      }
      this.source.disconnect();
      this.source = null;
    }
    if (this.shifter) {
      try {
        this.shifter.disconnect();
      } catch {
        /* already gone */
      }
    }
  }
}
