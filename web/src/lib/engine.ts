import type { LoopRegion } from "./audio";
import rbWorkletUrl from "../worklets/rb-live.ts?worker&url";

export type PitchEngineKind = "rubberband" | "fallback";

export type Selection = "original" | "variant";

export interface EngineCallbacks {
  onPosition?: (t: number) => void;
  onLiveChange?: (live: boolean) => void;
}

const TAIL_SECONDS = 0.3;
const READY_TIMEOUT_MS = 8000;

export class PitchAudioEngine {
  private ctx: AudioContext | null = null;
  private originalBuffer: AudioBuffer | null = null;
  private shiftedBuffer: AudioBuffer | null = null;
  private worklet: AudioWorkletNode | null = null;
  private workletModuleAdded = false;
  private liveChannels = 0;
  private liveReady = false;
  private wasmModule: WebAssembly.Module | null = null;
  private source: AudioBufferSourceNode | null = null;
  private startedAt = 0;
  private offset = 0;
  private playing = false;
  private selected: Selection = "original";
  private loop: LoopRegion | null = null;
  private rafId = 0;
  private pitch = 1;
  private base = 0;
  private tempo = 1;

  constructor(private callbacks: EngineCallbacks = {}) {}

  get pitchEngine(): PitchEngineKind {
    return this.livePath() ? "rubberband" : "fallback";
  }

  private livePath(): boolean {
    if (!this.worklet || !this.liveReady) return false;
    const kind = this.activeBufferKind();
    const buffer = kind === "variant" ? this.shiftedBuffer : this.originalBuffer;
    return !!buffer && buffer.numberOfChannels === this.liveChannels;
  }

  private ensureContext(): AudioContext {
    if (!this.ctx) this.ctx = new AudioContext();
    return this.ctx;
  }

  private async compileWasm(): Promise<WebAssembly.Module | null> {
    if (this.wasmModule) return this.wasmModule;
    try {
      const resp = await fetch("/rubberband.wasm");
      const bytes = await resp.arrayBuffer();
      this.wasmModule = await WebAssembly.compile(bytes);
      return this.wasmModule;
    } catch {
      return null;
    }
  }

  private waitReady(node: AudioWorkletNode): Promise<boolean> {
    return new Promise((resolve) => {
      const timer = window.setTimeout(() => resolve(false), READY_TIMEOUT_MS);
      const handler = (event: MessageEvent) => {
        const msg = event.data as { type?: string };
        if (msg.type === "ready") {
          window.clearTimeout(timer);
          node.port.removeEventListener("message", handler);
          resolve(true);
        } else if (msg.type === "error") {
          window.clearTimeout(timer);
          node.port.removeEventListener("message", handler);
          resolve(false);
        }
      };
      node.port.addEventListener("message", handler);
      node.port.start();
    });
  }

  private async activateLive(channels: number): Promise<boolean> {
    if (this.worklet && this.liveReady && this.liveChannels === channels) {
      return true;
    }
    this.teardownLive();
    try {
      const ctx = this.ensureContext();
      const module = await this.compileWasm();
      if (!module) return false;
      if (!this.workletModuleAdded) {
        await ctx.audioWorklet.addModule(rbWorkletUrl);
        this.workletModuleAdded = true;
      }
      const node = new AudioWorkletNode(ctx, "rb-live", {
        numberOfInputs: 1,
        numberOfOutputs: 1,
        outputChannelCount: [channels],
      });
      const readyPromise = this.waitReady(node);
      node.port.postMessage({
        type: "init",
        module,
        sampleRate: ctx.sampleRate,
        channels,
      });
      node.port.postMessage({ type: "pitch", value: this.effectivePitchRatio() });
      node.port.postMessage({ type: "tempo", value: this.tempo });
      if (!(await readyPromise)) {
        node.disconnect();
        return false;
      }
      node.connect(ctx.destination);
      this.worklet = node;
      this.liveChannels = channels;
      this.liveReady = true;
      return true;
    } catch {
      this.teardownLive();
      return false;
    }
  }

  private teardownLive(): void {
    if (this.worklet) {
      try {
        this.worklet.disconnect();
      } catch {
        /* already gone */
      }
      this.worklet = null;
    }
    this.liveReady = false;
    this.liveChannels = 0;
  }

  private notifyLive(): void {
    this.callbacks.onLiveChange?.(this.pitchEngine === "rubberband");
  }

  async loadOriginal(arrayBuffer: ArrayBuffer): Promise<void> {
    const ctx = this.ensureContext();
    this.originalBuffer = await ctx.decodeAudioData(arrayBuffer.slice(0));
    await this.activateLive(this.originalBuffer.numberOfChannels).catch(() => false);
    this.notifyLive();
  }

  async loadShifted(arrayBuffer: ArrayBuffer): Promise<void> {
    const ctx = this.ensureContext();
    this.shiftedBuffer = await ctx.decodeAudioData(arrayBuffer.slice(0));
    this.stopSource();
    await this.activateLive(this.shiftedBuffer.numberOfChannels).catch(() => false);
    this.notifyLive();
  }

  clearShifted(): void {
    this.stopSource();
    this.shiftedBuffer = null;
    if (this.selected === "variant") this.selected = "original";
  }

  get selection(): Selection {
    return this.selected;
  }

  select(kind: Selection): void {
    if (this.selected === kind) return;
    this.selected = kind;
    if (this.playing) {
      const t = this.currentTime();
      this.stopSource();
      this.playing = false;
      this.offset = t;
      this.play();
    }
  }

  get duration(): number {
    return this.originalBuffer?.duration ?? 0;
  }

  get hasOriginal(): boolean {
    return this.originalBuffer !== null;
  }

  get hasShifted(): boolean {
    return this.shiftedBuffer !== null;
  }

  get currentTempo(): number {
    return this.tempo;
  }

  activeBufferKind(): "original" | "variant" | null {
    if (this.selected === "variant" && this.shiftedBuffer) return "variant";
    if (this.originalBuffer) return "original";
    if (this.shiftedBuffer) return "variant";
    return null;
  }

  setLoop(loop: LoopRegion | null): void {
    this.loop = loop;
    this.restart();
  }

  setPitchRatio(ratio: number): void {
    this.pitch = ratio;
    if (this.worklet && this.liveReady) {
      this.worklet.port.postMessage({ type: "pitch", value: this.effectivePitchRatio() });
    } else {
      this.restart();
    }
  }

  get currentPitchRatio(): number {
    return this.pitch;
  }

  get baseCents(): number {
    return this.base;
  }

  setBaseCents(cents: number): void {
    this.base = cents;
    if (this.worklet && this.liveReady) {
      this.worklet.port.postMessage({ type: "pitch", value: this.effectivePitchRatio() });
    } else {
      this.restart();
    }
  }

  effectivePitchRatio(ratio: number = this.pitch): number {
    return ratio / Math.pow(2, this.base / 1200);
  }

  setTempo(tempo: number): void {
    this.tempo = tempo;
    if (this.worklet && this.liveReady) {
      this.worklet.port.postMessage({ type: "tempo", value: tempo });
    } else {
      this.restart();
    }
  }

  play(): void {
    if (this.playing) return;
    const kind = this.activeBufferKind();
    if (!kind) return;
    const buffer = kind === "original" ? this.originalBuffer : this.shiftedBuffer;
    if (!buffer) return;
    const ctx = this.ensureContext();
    void ctx.resume();
    this.stopSource();

    const live = this.livePath();
    const source = ctx.createBufferSource();
    source.buffer = buffer;
    if (live && this.worklet) {
      this.worklet.port.postMessage({ type: "reset" });
      this.worklet.port.postMessage({ type: "pitch", value: this.effectivePitchRatio() });
      this.worklet.port.postMessage({ type: "tempo", value: this.tempo });
      source.connect(this.worklet);
    } else {
      source.playbackRate.value =
        kind === "original" ? 1 : this.effectivePitchRatio() * this.tempo;
      source.connect(ctx.destination);
    }
    const startAt = Math.min(this.offset, buffer.duration);
    source.start(0, startAt);
    this.startedAt = ctx.currentTime - startAt;
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
    const dur = this.activeDuration();
    this.offset = dur > 0 ? Math.max(0, Math.min(t, dur)) : Math.max(0, t);
    if (this.playing) this.restart();
    if (this.worklet && this.liveReady) {
      this.worklet.port.postMessage({ type: "reset" });
    }
    this.callbacks.onPosition?.(this.offset);
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
    let t = this.currentTime();
    if (this.loop && t >= this.loop.end) {
      this.seek(this.loop.start);
      t = this.loop.start;
      this.callbacks.onPosition?.(t);
      this.rafId = requestAnimationFrame(this.tick);
      return;
    }
    this.callbacks.onPosition?.(t);
    const dur = this.activeDuration();
    if (!this.loop && dur > 0 && t >= dur + TAIL_SECONDS) {
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
    return this.liveReady;
  }
}
