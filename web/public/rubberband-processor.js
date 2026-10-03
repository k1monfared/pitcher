class RubberBandProcessor extends AudioWorkletProcessor {
  static get parameterDescriptors() {
    return [
      { name: "pitch", defaultValue: 1.0, minValue: 0.25, maxValue: 4.0 },
    ];
  }

  constructor() {
    super();
    this.ready = false;
    this.port.onmessage = (e) => this.handle(e.data);
    this.port.postMessage({ type: "init" });
  }

  handle(msg) {
    if (msg.type === "load") {
      this.load(msg).catch((err) =>
        this.port.postMessage({ type: "error", error: String(err) }),
      );
    } else if (msg.type === "pitch") {
      this.pitch = msg.value;
    } else if (msg.type === "tempo") {
      this.tempo = msg.value;
    } else if (msg.type === "loop") {
      this.loop = msg.loop;
    } else if (msg.type === "seek") {
      this.position = Math.floor(msg.time * sampleRate);
    } else if (msg.type === "play") {
      this.playing = true;
    } else if (msg.type === "pause") {
      this.playing = false;
    }
  }

  async load(msg) {
    const wasm = await WebAssembly.instantiateStreaming
      ? await WebAssembly.instantiateStreaming(fetch(msg.wasmUrl), {})
      : await WebAssembly.instantiate(await (await fetch(msg.wasmUrl)).arrayBuffer(), {});
    this.wasm = wasm.instance;
    const channels = msg.channels.map((c) => new Float32Array(c));
    this.channels = channels;
    this.frames = channels[0].length;
    this.position = 0;
    this.pitch = 1;
    this.tempo = 1;
    this.playing = false;
    this.loop = null;
    this.ready = true;
    this.port.postMessage({ type: "loaded", frames: this.frames });
  }

  process(_inputs, outputs) {
    const out = outputs[0];
    if (!this.ready || !this.playing || !this.channels) {
      return true;
    }
    const frames = out[0].length;
    const step = Math.max(1, Math.round(this.tempo));
    for (let ch = 0; ch < out.length; ch++) {
      const src = this.channels[Math.min(ch, this.channels.length - 1)];
      for (let i = 0; i < frames; i++) {
        const idx = this.position + Math.round(i * (this.pitch || 1));
        out[ch][i] = idx < src.length ? src[idx] : 0;
      }
    }
    this.position += Math.round(frames * (this.pitch || 1) * step);
    if (this.loop) {
      const end = Math.floor(this.loop.end * sampleRate);
      if (this.position >= end) {
        this.position = Math.floor(this.loop.start * sampleRate);
      }
    }
    if (this.position >= this.frames) {
      this.position = 0;
    }
    this.port.postMessage({ type: "position", frames: this.position });
    return true;
  }
}

registerProcessor("rubberband-processor", RubberBandProcessor);
