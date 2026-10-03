<script lang="ts">
  let {
    playing = false,
    mode = "variant" as "original" | "variant",
    canToggle = true,
    time = 0,
    duration = 0,
    onplay,
    onpause,
    ontoggle,
    onseek,
  } = $props<{
    playing?: boolean;
    mode?: "original" | "variant";
    canToggle?: boolean;
    time?: number;
    duration?: number;
    onplay?: () => void;
    onpause?: () => void;
    ontoggle?: () => void;
    onseek?: (t: number) => void;
  }>();

  function fmt(t: number): string {
    const m = Math.floor(t / 60);
    const s = Math.floor(t % 60);
    return `${m}:${s.toString().padStart(2, "0")}`;
  }
</script>

<div class="transport">
  <button type="button" onclick={playing ? onpause : onplay}>
    {playing ? "pause" : "play"}
  </button>
  <button type="button" class="toggle" onclick={ontoggle} disabled={!canToggle}>
    {mode === "original" ? "hearing: original" : "hearing: shifted"}
  </button>
  <input
    type="range"
    min="0"
    max={duration || 0}
    step="0.01"
    value={time}
    oninput={(e) => onseek?.(parseFloat((e.currentTarget as HTMLInputElement).value))}
  />
  <span class="time">{fmt(time)} / {fmt(duration)}</span>
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
  button.toggle {
    color: #6aa9ff;
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
