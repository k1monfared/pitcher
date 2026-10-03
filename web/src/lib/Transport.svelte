<script lang="ts">
  import { SPEED_OPTIONS, formatTempo } from "./audio";

  let {
    playing = false,
    tempo = 1,
    tempoEnabled = true,
    time = 0,
    duration = 0,
    onplay,
    onpause,
    ontempo,
    onseek,
  } = $props<{
    playing?: boolean;
    tempo?: number;
    tempoEnabled?: boolean;
    time?: number;
    duration?: number;
    onplay?: () => void;
    onpause?: () => void;
    ontempo?: (tempo: number) => void;
    onseek?: (t: number) => void;
  }>();

  let scrubbing = $state(false);
  let scrubValue = $state(0);

  $effect(() => {
    if (!scrubbing) scrubValue = time;
  });

  function fmt(t: number): string {
    const m = Math.floor(t / 60);
    const s = Math.floor(t % 60);
    return `${m}:${s.toString().padStart(2, "0")}`;
  }

  function commitSeek(value: number) {
    scrubbing = false;
    onseek?.(value);
  }
</script>

<div class="transport">
  <button type="button" class="play" onclick={playing ? onpause : onplay}>
    {playing ? "pause" : "play"}
  </button>
  <label
    class="speed"
    title={tempoEnabled
      ? "playback speed, pitch unchanged"
      : "speed needs the live pitch engine"}
  >
    speed
    <select
      value={tempo}
      disabled={!tempoEnabled}
      onchange={(e) => ontempo?.(parseFloat((e.currentTarget as HTMLSelectElement).value))}
    >
      {#each SPEED_OPTIONS as rate}
        <option value={rate}>{formatTempo(rate)}</option>
      {/each}
    </select>
  </label>
  <input
    type="range"
    min="0"
    max={duration || 0}
    step="0.01"
    value={scrubValue}
    onpointerdown={() => (scrubbing = true)}
    oninput={(e) => {
      scrubValue = parseFloat((e.currentTarget as HTMLInputElement).value);
      onseek?.(scrubValue);
    }}
    onpointerup={(e) =>
      commitSeek(parseFloat((e.currentTarget as HTMLInputElement).value))}
    onkeydown={(e) => {
      if (e.key === "ArrowLeft" || e.key === "ArrowRight") {
        scrubbing = false;
      }
    }}
  />
  <span class="time">{fmt(scrubbing ? scrubValue : time)} / {fmt(duration)}</span>
</div>

<style>
  .transport {
    display: flex;
    gap: 0.75rem;
    align-items: center;
  }
  button {
    background: #1b1b1f;
    color: #ddd;
    border: 1px solid #333;
    border-radius: 0.3rem;
    padding: 0.35rem 0.7rem;
    cursor: pointer;
  }
  button:disabled {
    opacity: 0.4;
    cursor: default;
  }
  button.play {
    min-width: 4.2rem;
  }
  .speed {
    display: flex;
    gap: 0.35rem;
    align-items: center;
    font-size: 0.8rem;
    color: #aaa;
  }
  .speed select {
    background: #16161a;
    color: #eee;
    border: 1px solid #333;
    border-radius: 0.3rem;
    padding: 0.2rem 0.35rem;
  }
  input[type="range"] {
    flex: 1;
    accent-color: #6aa9ff;
  }
  .time {
    font-variant-numeric: tabular-nums;
    font-size: 0.8rem;
    color: #888;
  }
</style>
