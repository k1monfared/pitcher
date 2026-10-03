<script lang="ts">
  import { onMount } from "svelte";

  let {
    peaks = [],
    duration = 0,
    loop = null,
    playhead = 0,
    onseek,
    onloop,
  } = $props<{
    peaks?: number[];
    duration?: number;
    loop?: [number, number] | null;
    playhead?: number;
    onseek?: (t: number) => void;
    onloop?: (loop: [number, number] | null) => void;
  }>();

  let canvas: HTMLCanvasElement | undefined = $state();
  let width = 900;
  let height = 140;
  let dragStart: number | null = null;
  let dragging = $state(false);

  const timeToX = (t: number) => (duration > 0 ? (t / duration) * width : 0);
  const xToTime = (x: number) => (width > 0 ? (x / width) * duration : 0);

  function draw() {
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    const dpr = window.devicePixelRatio || 1;
    canvas.width = width * dpr;
    canvas.height = height * dpr;
    ctx.scale(dpr, dpr);
    ctx.clearRect(0, 0, width, height);

    if (loop) {
      const x0 = timeToX(loop[0]);
      const x1 = timeToX(loop[1]);
      ctx.fillStyle = "#6aa9ff22";
      ctx.fillRect(x0, 0, x1 - x0, height);
      ctx.strokeStyle = "#6aa9ff";
      ctx.beginPath();
      ctx.moveTo(x0, 0);
      ctx.lineTo(x0, height);
      ctx.moveTo(x1, 0);
      ctx.lineTo(x1, height);
      ctx.stroke();
    }

    ctx.fillStyle = "#7a7a85";
    const mid = height / 2;
    if (peaks.length > 0) {
      const step = width / peaks.length;
      for (let i = 0; i < peaks.length; i++) {
        const p = peaks[i];
        const x = i * step;
        const h = Math.max(1, p * mid);
        ctx.fillRect(x, mid - h, Math.max(1, step - 1), h * 2);
      }
    } else {
      ctx.fillRect(0, mid - 1, width, 2);
    }

    const px = timeToX(playhead);
    ctx.strokeStyle = "#ff6b6b";
    ctx.beginPath();
    ctx.moveTo(px, 0);
    ctx.lineTo(px, height);
    ctx.stroke();
  }

  onMount(() => {
    const measure = () => {
      if (canvas?.parentElement) width = canvas.parentElement.clientWidth;
      draw();
    };
    measure();
    window.addEventListener("resize", measure);
    return () => window.removeEventListener("resize", measure);
  });

  $effect(() => {
    peaks;
    loop;
    playhead;
    duration;
    draw();
  });

  function localX(e: PointerEvent): number {
    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
    return e.clientX - rect.left;
  }

  function onPointerDown(e: PointerEvent) {
    if (e.shiftKey) {
      dragging = true;
      dragStart = xToTime(localX(e));
      (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
    } else {
      onseek?.(xToTime(localX(e)));
    }
  }

  function onPointerMove(e: PointerEvent) {
    if (!dragging || dragStart === null) return;
    const t = xToTime(localX(e));
    onloop?.([Math.min(dragStart, t), Math.max(dragStart, t)]);
  }

  function onPointerUp(e: PointerEvent) {
    dragging = false;
    dragStart = null;
    (e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId);
  }

  function clearLoop() {
    onloop?.(null);
  }
</script>

<div class="wave">
  <canvas
    bind:this={canvas}
    style="width: 100%; height: {height}px"
    onpointerdown={onPointerDown}
    onpointermove={onPointerMove}
    onpointerup={onPointerUp}
  ></canvas>
  <div class="hint">
    click to seek, shift-drag to set loop
    {#if loop}
      <button type="button" onclick={clearLoop}>clear loop</button>
    {/if}
  </div>
</div>

<style>
  .wave {
    width: 100%;
  }
  canvas {
    display: block;
    background: #121216;
    border: 1px solid #333;
    border-radius: 0.5rem;
    cursor: crosshair;
    touch-action: none;
  }
  .hint {
    font-size: 0.7rem;
    color: #777;
    margin-top: 0.25rem;
    display: flex;
    gap: 0.5rem;
    align-items: center;
  }
  .hint button {
    background: #1b1b1f;
    color: #aaa;
    border: 1px solid #333;
    border-radius: 0.3rem;
    padding: 0.1rem 0.4rem;
    cursor: pointer;
  }
</style>
