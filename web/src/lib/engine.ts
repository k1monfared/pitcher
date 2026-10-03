import type { LoopRegion } from "./audio";

export interface EngineCallbacks {
  onPosition?: (t: number) => void;
  onModeChange?: (mode: "original" | "variant") => void;
}

export class PitchAudioEngine {
  private ctx: AudioContext | null = null;
  private originalBuffer: AudioBuffer | null = null;
  private shifted: { buffer: AudioBuffer; source: AudioBufferSourceNode } | null = null;
  private worklet: AudioWorkletNode | null = null;
  private source: AudioBufferSourceNode | null = null;
  private startedAt = 0;
  private offset = 0;
  private playing = false;
  private mode: "original" | "variant" = "variant";
  private loop: LoopRegion | null = null;
  private rafId = 0;
  private pitch = 1;
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
    const buffer = await ctx.decodeAudioData(arrayBuffer.slice(0));
    this.stopSource();
    this.shifted = { buffer, source: null as unknown as AudioBufferSourceNode };
  }

  get duration(): number {
    return this.originalBuffer?.duration ?? 0;
  }

  get currentMode(): "original" | "variant" {
    return this.mode;
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

  setMode(mode: "original" | "variant"): void {
    if (this.mode === mode) return;
    this.mode = mode;
    this.callbacks.onModeChange?.(mode);
    this.restart();
  }

  play(): void {
    if (this.playing) return;
    const buffer = this.mode === "original" ? this.originalBuffer : this.shifted?.buffer;
    if (!buffer) return;
    const ctx = this.ensureContext();
    ctx.resume();
    this.stopSource();

    const source = ctx.createBufferSource();
    source.buffer = buffer;
    source.playbackRate.value = this.mode === "original" ? 1 : this.pitch;
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

  private tick = () => {
    if (!this.playing) return;
    const t = this.currentTime();
    this.callbacks.onPosition?.(t);
    if (!this.loop && this.originalBuffer && t >= this.originalBuffer.duration) {
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
