import { RubberBandInterface, RubberBandOption } from "rubberband-wasm";

declare function registerProcessor(
  name: string,
  processor: new (options?: unknown) => AudioWorkletProcessor,
): void;

declare class AudioWorkletProcessor {
  readonly port: MessagePort;
  process(
    inputs: Float32Array[][],
    outputs: Float32Array[][],
    parameters?: Record<string, Float32Array>,
  ): boolean;
}

interface InitMessage {
  type: "init";
  module: WebAssembly.Module;
  sampleRate: number;
  channels: number;
}

interface PitchMessage {
  type: "pitch";
  value: number;
}

interface TempoMessage {
  type: "tempo";
  value: number;
}

interface ResetMessage {
  type: "reset";
}

type InMessage = InitMessage | PitchMessage | TempoMessage | ResetMessage;

const CHUNK = 512;

function liveOptions(): number {
  return (
    RubberBandOption.RubberBandOptionProcessRealTime |
    RubberBandOption.RubberBandOptionStretchPrecise |
    RubberBandOption.RubberBandOptionTransientsCrisp |
    RubberBandOption.RubberBandOptionDetectorCompound |
    RubberBandOption.RubberBandOptionPhaseLaminar |
    RubberBandOption.RubberBandOptionFormantPreserved |
    RubberBandOption.RubberBandOptionPitchHighQuality |
    RubberBandOption.RubberBandOptionChannelsApart |
    RubberBandOption.RubberBandOptionEngineFiner
  );
}

class RbLiveProcessor extends AudioWorkletProcessor {
  private rb: RubberBandInterface | null = null;
  private state = 0;
  private channels = 0;
  private ready = false;

  private inPtrs = 0;
  private outPtrs = 0;
  private inBuf = 0;
  private outBuf = 0;

  private pending: Float32Array[] = [];
  private pendingLen = 0;
  private outQueue: Float32Array[] = [];
  private outLen = 0;

  constructor() {
    super();
    this.port.onmessage = (event: MessageEvent) => {
      void this.handle(event.data as InMessage);
    };
  }

  private async handle(msg: InMessage): Promise<void> {
    if (msg.type === "init") {
      await this.init(msg);
    } else if (!this.rb) {
      return;
    } else if (msg.type === "pitch") {
      this.rb.rubberband_set_pitch_scale(this.state, msg.value);
    } else if (msg.type === "tempo") {
      this.rb.rubberband_set_time_ratio(this.state, 1 / msg.value);
    } else if (msg.type === "reset") {
      this.reset();
    }
  }

  private async init(msg: InitMessage): Promise<void> {
    try {
      this.teardown();
      const rb = await RubberBandInterface.initialize(msg.module);
      const state = rb.rubberband_new(
        msg.sampleRate,
        msg.channels,
        liveOptions(),
        1,
        1,
      );
      const ch = msg.channels;
      this.inPtrs = rb.malloc(ch * 4);
      this.outPtrs = rb.malloc(ch * 4);
      this.inBuf = rb.malloc(ch * CHUNK * 4);
      this.outBuf = rb.malloc(ch * CHUNK * 4);
      for (let c = 0; c < ch; c++) {
        rb.memWritePtr(this.inPtrs + c * 4, this.inBuf + c * CHUNK * 4);
        rb.memWritePtr(this.outPtrs + c * 4, this.outBuf + c * CHUNK * 4);
      }
      this.rb = rb;
      this.state = state;
      this.channels = ch;
      this.pending = Array.from({ length: ch }, () => new Float32Array(0));
      this.outQueue = Array.from({ length: ch }, () => new Float32Array(0));
      this.pendingLen = 0;
      this.outLen = 0;
      this.ready = true;
      this.port.postMessage({ type: "ready", channels: ch });
    } catch (err) {
      this.port.postMessage({ type: "error", error: String(err) });
    }
  }

  private reset(): void {
    if (this.rb) this.rb.rubberband_reset(this.state);
    for (let c = 0; c < this.channels; c++) {
      this.pending[c] = new Float32Array(0);
      this.outQueue[c] = new Float32Array(0);
    }
    this.pendingLen = 0;
    this.outLen = 0;
  }

  private teardown(): void {
    if (this.rb) {
      try {
        this.rb.rubberband_delete(this.state);
      } catch {
        /* already gone */
      }
      this.rb = null;
    }
    this.ready = false;
  }

  private appendInput(block: Float32Array[]): void {
    for (let c = 0; c < this.channels; c++) {
      const src = block[Math.min(c, block.length - 1)] ?? new Float32Array(128);
      const merged = new Float32Array(this.pending[c].length + src.length);
      merged.set(this.pending[c], 0);
      merged.set(src, this.pending[c].length);
      this.pending[c] = merged;
    }
    this.pendingLen += block[0]?.length ?? 128;
  }

  private consumeChunk(): void {
    const rb = this.rb;
    if (!rb) return;
    for (let c = 0; c < this.channels; c++) {
      rb.memWrite(this.inBuf + c * CHUNK * 4, this.pending[c].subarray(0, CHUNK));
      this.pending[c] = this.pending[c].slice(CHUNK);
    }
    this.pendingLen -= CHUNK;
    rb.rubberband_process(this.state, this.inPtrs, CHUNK, 0);
    const avail = rb.rubberband_available(this.state);
    const take = Math.min(avail, CHUNK);
    if (take > 0) {
      const got = rb.rubberband_retrieve(this.state, this.outPtrs, take);
      for (let c = 0; c < this.channels; c++) {
        const data = rb.memReadF32(this.outBuf + c * CHUNK * 4, got);
        const merged = new Float32Array(this.outQueue[c].length + got);
        merged.set(this.outQueue[c], 0);
        merged.set(data, this.outQueue[c].length);
        this.outQueue[c] = merged;
      }
      this.outLen += got;
    }
  }

  process(inputs: Float32Array[][], outputs: Float32Array[][]): boolean {
    const out = outputs[0];
    if (!this.ready || !this.rb || inputs.length === 0 || out.length === 0) {
      return true;
    }
    this.appendInput(inputs[0] as Float32Array[]);
    while (this.pendingLen >= CHUNK) {
      this.consumeChunk();
    }
    const n = out[0].length;
    for (let c = 0; c < out.length; c++) {
      const q = this.outQueue[Math.min(c, this.channels - 1)] ?? new Float32Array(0);
      const take = Math.min(n, q.length);
      out[c].set(q.subarray(0, take), 0);
      if (take < n) out[c].fill(0, take);
      this.outQueue[Math.min(c, this.channels - 1)] = q.slice(take);
    }
    this.outLen = Math.max(0, this.outLen - n);
    return true;
  }
}

registerProcessor("rb-live", RbLiveProcessor);
