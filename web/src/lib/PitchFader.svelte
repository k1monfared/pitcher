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

  const knobY = $derived(centsToY(cents, range, height));

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
  <div class="track-row">
    <div class="marks" style="height: {height}px" title="semitones">
      <span>+{range / 100}</span>
      <span>0</span>
      <span>-{range / 100}</span>
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
  </div>
  <div class="readout">
    <span class="cents">{formatCents(cents)}</span>
    <span class="unit">cents</span>
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
  .track-row {
    display: flex;
    gap: 0.4rem;
    align-items: flex-start;
  }
  .marks {
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    align-items: flex-end;
    font-size: 0.7rem;
    color: #888;
    padding: 0;
    line-height: 1;
  }
  .marks span:first-child {
    margin-top: -0.35em;
  }
  .marks span:last-child {
    margin-bottom: -0.35em;
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
</style>
