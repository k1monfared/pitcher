<script lang="ts">
  import { hzToNote, midiToHz } from "../lib/notes";

  let {
    detectedHz = null,
    manualHz = $bindable<number | null>(null),
    targetNote = $bindable<string>(""),
    statusText = "",
    ondetect,
    onapply,
  } = $props<{
    detectedHz?: number | null;
    manualHz?: number | null;
    targetNote?: string;
    statusText?: string;
    ondetect?: () => void;
    onapply?: (cents: number) => void;
  }>();

  let manualNote = $state("");

  const reading = $derived(detectedHz ? hzToNote(detectedHz) : null);

  const sourceHz = $derived(
    manualHz && manualHz > 0 ? manualHz : detectedHz ?? null,
  );

  const targetHz = $derived(targetNote ? midiToHz(noteToMidi(targetNote)) : null);

  function noteToMidi(name: string): number {
    const m = name.match(/^([A-G]#?)(-?\d+)$/);
    if (!m) return 69;
    const idx = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"].indexOf(m[1]);
    if (idx < 0) return 69;
    return 12 * (parseInt(m[2], 10) + 1) + idx;
  }

  const interval = $derived(
    sourceHz && targetHz ? 1200 * Math.log2(targetHz / sourceHz) : null,
  );

  function applyManualNote() {
    const midi = noteToMidi(manualNote);
    manualHz = midiToHz(midi);
  }
</script>

<div class="tuner">
  <div class="row">
    <button type="button" onclick={ondetect}>detect at playhead</button>
    {#if reading}
      <span class="detected">
        {reading.name}
        <small>{reading.centsOff >= 0 ? "+" : ""}{reading.centsOff.toFixed(1)}c</small>
        <small>{detectedHz?.toFixed(1)} Hz</small>
      </span>
    {:else if statusText}
      <span class="status">{statusText}</span>
    {/if}
  </div>

  <div class="row">
    <label>
      manual note
      <input bind:value={manualNote} placeholder="C#4" oninput={applyManualNote} />
    </label>
    <label>
      manual Hz
      <input
        type="number"
        step="0.01"
        bind:value={manualHz}
        placeholder="277.18"
      />
    </label>
  </div>

  <div class="row">
    <label>
      target note
      <input bind:value={targetNote} placeholder="C4" />
    </label>
    {#if interval !== null}
      <span class="interval">{interval >= 0 ? "+" : ""}{interval.toFixed(0)} cents</span>
      <button
        type="button"
        title="move the pitch fader to this difference"
        onclick={() => onapply?.(interval)}
      >
        move fader here
      </button>
    {/if}
  </div>

  <div class="row source">
    source: {sourceHz ? `${sourceHz.toFixed(1)} Hz` : "none"}
  </div>
</div>

<style>
  .tuner {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
    background: #16161a;
    border: 1px solid #333;
    border-radius: 0.5rem;
    padding: 0.75rem;
  }
  .row {
    display: flex;
    gap: 0.75rem;
    align-items: center;
    flex-wrap: wrap;
  }
  label {
    display: flex;
    gap: 0.4rem;
    align-items: center;
    font-size: 0.8rem;
    color: #aaa;
  }
  input {
    background: #0e0e12;
    border: 1px solid #333;
    border-radius: 0.3rem;
    color: #eee;
    padding: 0.25rem 0.4rem;
    width: 6rem;
  }
  button {
    background: #1b1b1f;
    color: #ddd;
    border: 1px solid #333;
    border-radius: 0.3rem;
    padding: 0.3rem 0.6rem;
    cursor: pointer;
  }
  .detected {
    display: flex;
    gap: 0.4rem;
    align-items: baseline;
    font-size: 1.05rem;
  }
  .detected small {
    color: #888;
    font-size: 0.75rem;
  }
  .interval {
    color: #6aa9ff;
    font-variant-numeric: tabular-nums;
  }
  .source {
    font-size: 0.75rem;
    color: #777;
  }
</style>
