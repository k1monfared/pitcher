<script lang="ts">
  import { centsToY, clampCents, yToCents } from "../lib/fader";
  import { formatCents } from "../lib/notes";

  let {
    cents = $bindable(0),
    range = 1200,
    height = 360,
    onchange,
  } = $props<{
    cents?: number;
    range?: number;
    height?: number;
    onchange?: (cents: number) => void;
  }>();

  let dragging = $state(false);
  let startY = 0;
  let startCents = 0;
  let stepSemitones = $state("1");

  const knobY = $derived(centsToY(cents, range, height));

  function nudgeSemitones(direction: 1 | -1) {
    const st = parseFloat(stepSemitones);
    if (!Number.isFinite(st) || st === 0) return;
    cents = clampCents(Math.round(cents + direction * st * 100), range);
    onchange?.(cents);
  }

  function setFromY(y: number) {
    const raw = clampCents(yToCents(y, range, height), range);
    cents = Math.round(raw);
    onchange?.(cents);
  }

  function onPointerDown(e: PointerEvent) {
    dragging = true;
    startY = e.clientY;
    startCents = cents;
    (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
  }

  function onPointerMove(e: PointerEvent) {
    if (!dragging) return;
    const deltaY = e.clientY - startY;
    const semitones = -deltaY / (height / (2 * range / 100));
    const raw = clampCents(startCents + semitones * 100, range);
    cents = Math.round(raw);
    onchange?.(cents);
  }

  function onPointerUp(e: PointerEvent) {
    dragging = false;
    (e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId);
  }

  function onTrackPointerDown(e: PointerEvent) {
    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
    setFromY(e.clientY - rect.top);
    onPointerDown(e);
  }
</script>

<div class="fader">
  <div class="marks">
    <span class="mark top">+{range / 100}</span>
    <span class="mark zero">0</span>
    <span class="mark bottom">-{range / 100}</span>
  </div>
  <div
    class="track"
    style="height: {height}px"
    role="slider"
    tabindex="0"
    aria-label="pitch in cents"
    aria-valuemin={-range}
    aria-valuemax={range}
    aria-valuenow={cents}
    onpointerdown={onTrackPointerDown}
    onpointermove={onPointerMove}
    onpointerup={onPointerUp}
  >
    <div class="centerline"></div>
    <div class="knob" style="top: {knobY}px"></div>
    <div class="fill" style="top: {Math.min(knobY, height / 2)}px; height: {Math.abs(knobY - height / 2)}px"></div>
  </div>
  <div class="readout">
    <span class="cents">{formatCents(cents)}</span>
    <span class="unit">cents</span>
  </div>
  <div class="stepper">
    <input
      type="number"
      step="0.25"
      min="0"
      bind:value={stepSemitones}
      title="semitones per step"
      aria-label="semitones per step"
    />
    <button type="button" title="move down by this many semitones" onclick={() => nudgeSemitones(-1)}>
      down
    </button>
    <button type="button" title="move up by this many semitones" onclick={() => nudgeSemitones(1)}>
      up
    </button>
  </div>
</div>

<style>
  .fader {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
    align-items: center;
    user-select: none;
  }
  .marks {
    display: flex;
    flex-direction: column;
    align-items: center;
    height: 100%;
    font-size: 0.7rem;
    color: #888;
  }
  .track {
    position: relative;
    width: 3.5rem;
    background: #1b1b1f;
    border: 1px solid #333;
    border-radius: 0.5rem;
    cursor: ns-resize;
    touch-action: none;
  }
  .track:focus-visible {
    outline: 2px solid #6aa9ff;
  }
  .centerline {
    position: absolute;
    left: 0;
    right: 0;
    top: 50%;
    border-top: 1px dashed #444;
  }
  .knob {
    position: absolute;
    left: 50%;
    width: 1.6rem;
    height: 1.6rem;
    margin-left: -0.8rem;
    margin-top: -0.8rem;
    border-radius: 50%;
    background: #6aa9ff;
    box-shadow: 0 0 0 3px #0e0e12;
  }
  .fill {
    position: absolute;
    left: 50%;
    width: 2px;
    margin-left: -1px;
    background: #6aa9ff55;
  }
  .readout {
    display: flex;
    align-items: baseline;
    gap: 0.3rem;
  }
  .cents {
    font-size: 1.4rem;
    font-variant-numeric: tabular-nums;
  }
  .unit {
    font-size: 0.75rem;
    color: #888;
  }
  .stepper {
    display: flex;
    gap: 0.25rem;
    align-items: center;
  }
  .stepper input {
    background: #0e0e12;
    border: 1px solid #333;
    border-radius: 0.3rem;
    color: #eee;
    padding: 0.2rem 0.3rem;
    width: 3.5rem;
    font-size: 0.75rem;
  }
  .stepper button {
    background: #1b1b1f;
    color: #aaa;
    border: 1px solid #333;
    border-radius: 0.3rem;
    padding: 0.2rem 0.45rem;
    font-size: 0.75rem;
    cursor: pointer;
  }
</style>
