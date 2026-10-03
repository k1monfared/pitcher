import type { LoopRegion } from "./audio";

export interface EngineCallbacks {
  onPosition?: (t: number) => void;
  onModeChange?: (mode: "original" | "variant") => void;
}

export class PitchAudioEngine {
  private ctx: AudioContext | null = null;
  private originalBuffer: AudioBuffer | null = null;
  private shiftedBuffer: AudioBuffer | null = null;
  private worklet: AudioWorkletNode | null = null;
  private source: AudioBufferSourceNode | null = null;
  private startedAt = 0;
  private offset = 0;
  private playing = false;
  private mode: "original" | "variant" = "variant";
  private loop: LoopRegion | null = null;
  private rafId = 0;
  private pitch = 1;
  private tempo = 1;
  private useWorklet = false;

  constructor(private callbacks: EngineCallbacks = {}) {}

  async initWorklet(processorUrl: string): Promise<boolean> {
    try {
      const ctx = this.ensureContext();
      await ctx.audioWorklet.addModule(processorUrl);
      return true;
    } catch {
      return false;
    }
  }

  private ensureContext(): AudioContext {
    if (!this.ctx) this.ctx = new AudioContext();
    return this.ctx;
  }

  async loadOriginal(arrayBuffer: ArrayBuffer): Promise<void> {
    const ctx = this.ensureContext();
    this.originalBuffer = await ctx.decodeAudioData(arrayBuffer.slice(0));
  }

  async loadShifted(arrayBuffer: ArrayBuffer): Promise<void> {
    const ctx = this.ensureContext();
    this.shiftedBuffer = await ctx.decodeAudioData(arrayBuffer.slice(0));
    this.stopSource();
  }

  clearShifted(): void {
    this.stopSource();
    this.shiftedBuffer = null;
    if (this.mode === "variant") this.mode = "original";
  }

  get duration(): number {
    return this.originalBuffer?.duration ?? 0;
  }

  get currentMode(): "original" | "variant" {
    return this.mode;
  }

  get hasOriginal(): boolean {
    return this.originalBuffer !== null;
  }

  get hasShifted(): boolean {
    return this.shiftedBuffer !== null;
  }

  activeBufferKind(): "original" | "variant" | null {
    if (this.mode === "variant" && this.shiftedBuffer) return "variant";
    if (this.originalBuffer) return "original";
    if (this.shiftedBuffer) return "variant";
    return null;
  }

  setLoop(loop: LoopRegion | null): void {
    this.loop = loop;
    this.worklet?.port.postMessage({ type: "loop", loop });
    this.restart();
  }

  setPitchRatio(ratio: number): void {
    this.pitch = ratio;
    if (this.worklet) {
      this.worklet.port.postMessage({ type: "pitch", value: ratio });
    } else {
      this.restart();
    }
  }

  get currentTempo(): number {
    return this.tempo;
  }

  setTempo(tempo: number): void {
    this.tempo = tempo;
    if (this.worklet) {
      this.worklet.port.postMessage({ type: "tempo", value: tempo });
    } else {
      this.restart();
    }
  }

  setMode(mode: "original" | "variant"): void {
    if (this.mode === mode) return;
    this.mode = mode;
    this.callbacks.onModeChange?.(mode);
    this.restart();
  }

  play(): void {
    if (this.playing) return;
    const kind = this.activeBufferKind();
    if (!kind) return;
    const buffer = kind === "original" ? this.originalBuffer : this.shiftedBuffer;
    if (!buffer) return;
    if (kind !== this.mode) {
      this.mode = kind;
      this.callbacks.onModeChange?.(kind);
    }
    const ctx = this.ensureContext();
    ctx.resume();
    this.stopSource();

    const source = ctx.createBufferSource();
    source.buffer = buffer;
    source.playbackRate.value = kind === "original" ? 1 : this.pitch;
    if (this.loop) {
      source.loop = true;
      source.loopStart = this.loop.start;
      source.loopEnd = this.loop.end;
    }
    source.connect(ctx.destination);
    const when = this.loop ? this.offset : Math.min(this.offset, buffer.duration);
    source.start(0, when);
    this.startedAt = ctx.currentTime - when;
    this.source = source;
    this.playing = true;
    this.tick();
  }

  pause(): void {
    if (!this.playing) return;
    this.offset = this.currentTime();
    this.stopSource();
    this.playing = false;
    cancelAnimationFrame(this.rafId);
  }

  seek(t: number): void {
    this.offset = Math.max(0, t);
    if (this.playing) this.play();
  }

  currentTime(): number {
    if (!this.playing || !this.ctx) return this.offset;
    return this.ctx.currentTime - this.startedAt;
  }

  private activeDuration(): number {
    const kind = this.activeBufferKind();
    const buffer = kind === "variant" ? this.shiftedBuffer : this.originalBuffer;
    return buffer?.duration ?? 0;
  }

  private tick = () => {
    if (!this.playing) return;
    const t = this.currentTime();
    this.callbacks.onPosition?.(t);
    const dur = this.activeDuration();
    if (!this.loop && dur > 0 && t >= dur) {
      this.pause();
      return;
    }
    this.rafId = requestAnimationFrame(this.tick);
  };

  private restart(): void {
    if (!this.playing) return;
    const t = this.currentTime();
    this.stopSource();
    this.playing = false;
    this.offset = t;
    this.play();
  }

  private stopSource(): void {
    if (this.source) {
      try {
        this.source.stop();
      } catch {
        /* already stopped */
      }
      this.source.disconnect();
      this.source = null;
    }
  }

  get isWorkletActive(): boolean {
    return this.useWorklet;
  }
}
