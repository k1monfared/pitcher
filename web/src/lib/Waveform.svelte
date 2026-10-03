<script lang="ts">
  import { onMount } from "svelte";
  import { centerOn, followView, panBy, zoomAt, type View } from "./view";
  import type { Bookmark } from "./api";

  let {
    peaks = [],
    duration = 0,
    loop = null,
    playhead = 0,
    bookmarks = [],
    onseek,
    onloop,
  } = $props<{
    peaks?: number[];
    duration?: number;
    loop?: [number, number] | null;
    playhead?: number;
    bookmarks?: Bookmark[];
    onseek?: (t: number) => void;
    onloop?: (loop: [number, number] | null) => void;
  }>();

  let canvas: HTMLCanvasElement | undefined = $state();
  let width = 900;
  let height = 140;
  let view = $state<View | null>(null);
  let follow = $state(true);
  let downX: number | null = null;
  let downTime: number | null = null;
  let downView: View | null = null;
  let gesture: "maybe" | "loop" | "pan" | null = null;

  const span = $derived(view ?? { start: 0, end: duration });

  const timeToX = (t: number) =>
    span.end > span.start ? ((t - span.start) / (span.end - span.start)) * width : 0;
  const xToTime = (x: number) =>
    span.start + (width > 0 ? (x / width) * (span.end - span.start) : 0);

  function draw() {
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    const dpr = window.devicePixelRatio || 1;
    canvas.width = width * dpr;
    canvas.height = height * dpr;
    ctx.scale(dpr, dpr);
    ctx.clearRect(0, 0, width, height);

    if (loop && duration > 0) {
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
    if (peaks.length > 0 && duration > 0) {
      const n = peaks.length;
      const i0 = Math.max(0, Math.floor(((span.start - 0) / duration) * n));
      const i1 = Math.min(n, Math.ceil((span.end / duration) * n));
      const count = Math.max(1, i1 - i0);
      const step = width / count;
      for (let i = 0; i < count; i++) {
        const p = peaks[i0 + i] ?? 0;
        const x = i * step;
        const h = Math.max(1, p * mid);
        ctx.fillRect(x, mid - h, Math.max(1, step - 1), h * 2);
      }
    } else {
      ctx.fillRect(0, mid - 1, width, 2);
    }

    if (playhead >= span.start && playhead <= span.end) {
      const px = timeToX(playhead);
      ctx.strokeStyle = "#ff6b6b";
      ctx.beginPath();
      ctx.moveTo(px, 0);
      ctx.lineTo(px, height);
      ctx.stroke();
    }

    ctx.font = "10px system-ui, sans-serif";
    for (const b of bookmarks) {
      if (b.t < span.start || b.t > span.end) continue;
      const bx = timeToX(b.t);
      ctx.strokeStyle = "#ffd166";
      ctx.fillStyle = "#ffd166";
      ctx.beginPath();
      ctx.moveTo(bx, 0);
      ctx.lineTo(bx, 12);
      ctx.stroke();
      ctx.beginPath();
      ctx.moveTo(bx, 2);
      ctx.lineTo(bx - 4, 8);
      ctx.lineTo(bx + 4, 8);
      ctx.closePath();
      ctx.fill();
      const label = b.name?.trim() ? b.name : fmtClock(b.t);
      ctx.fillText(label.slice(0, 24), bx + 5, 10);
    }
  }

  function fmtClock(t: number): string {
    const m = Math.floor(t / 60);
    const s = Math.floor(t % 60);
    return `${m}:${s.toString().padStart(2, "0")}`;
  }

  onMount(() => {
    const measure = () => {
      if (canvas?.parentElement) width = canvas.parentElement.clientWidth;
      draw();
    };
    measure();
    window.addEventListener("resize", measure);
    const onWheel = (e: WheelEvent) => {
      e.preventDefault();
      const rect = canvas?.getBoundingClientRect();
      const x = rect ? e.clientX - rect.left : width / 2;
      view = zoomAt(view, xToTime(x), e.deltaY > 0 ? 1.25 : 0.8, duration);
      draw();
    };
    canvas?.addEventListener("wheel", onWheel, { passive: false });
    return () => {
      window.removeEventListener("resize", measure);
      canvas?.removeEventListener("wheel", onWheel);
    };
  });

  $effect(() => {
    peaks;
    loop;
    playhead;
    duration;
    span;
    if (follow && view) {
      const next = followView(view, playhead, duration);
      if (next !== view) view = next;
    }
    draw();
  });

  $effect(() => {
    duration;
    view = null;
    follow = true;
  });

  function localX(e: PointerEvent): number {
    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
    return e.clientX - rect.left;
  }

  function onPointerDown(e: PointerEvent) {
    downX = localX(e);
    downTime = xToTime(downX);
    downView = view;
    gesture = e.shiftKey ? "loop" : "maybe";
    (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
  }

  function onPointerMove(e: PointerEvent) {
    if (gesture === null || downX === null) return;
    const x = localX(e);
    if (gesture === "maybe" && Math.abs(x - downX) > 4) {
      gesture = "pan";
      if (!downView && duration > 0) downView = { start: 0, end: duration };
    }
    if (gesture === "loop" && downTime !== null) {
      const t = xToTime(x);
      onloop?.([Math.min(downTime, t), Math.max(downTime, t)]);
    } else if (gesture === "pan" && downView && duration > 0) {
      const secondsPerPixel = (downView.end - downView.start) / Math.max(1, width);
      view = panBy(downView, (downX - x) * secondsPerPixel, duration);
      follow = false;
      draw();
    }
  }

  function nearestMarker(x: number): number | null {
    let best: number | null = null;
    let bestDist = 9;
    for (const b of bookmarks) {
      const d = Math.abs(timeToX(b.t) - x);
      if (d < bestDist) {
        bestDist = d;
        best = b.t;
      }
    }
    return best;
  }

  function onPointerUp(e: PointerEvent) {
    if (gesture === "maybe" && downX !== null) {
      onseek?.(nearestMarker(downX) ?? xToTime(downX));
    }
    gesture = null;
    downX = null;
    downTime = null;
    downView = null;
    (e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId);
  }

  function clearLoop() {
    onloop?.(null);
  }

  function zoomOnPlayhead(factor: number) {
    const centered = centerOn(view, playhead, duration) ?? {
      start: 0,
      end: duration,
    };
    view = zoomAt(centered, Math.max(0, Math.min(playhead, duration)), factor, duration);
    draw();
  }

  function zoomIn() {
    zoomOnPlayhead(0.5);
  }

  function zoomOut() {
    zoomOnPlayhead(2);
  }

  function zoomReset() {
    view = null;
    follow = true;
    draw();
  }

  function recenter() {
    view = centerOn(view, playhead, duration);
    follow = true;
    draw();
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
    <span>click seek · drag pan · shift-drag loop · wheel zoom</span>
    <span class="zoom">
      <button type="button" onclick={zoomIn} title="zoom in on playhead">+</button>
      <button type="button" onclick={zoomOut} title="zoom out from playhead">-</button>
      {#if view}
        <button type="button" onclick={recenter} title="follow the playhead again">
          {follow ? "following" : "recenter"}
        </button>
      {/if}
      <button type="button" onclick={zoomReset} title="show all">reset</button>
    </span>
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
    flex-wrap: wrap;
  }
  .hint button {
    background: #1b1b1f;
    color: #aaa;
    border: 1px solid #333;
    border-radius: 0.3rem;
    padding: 0.1rem 0.4rem;
    cursor: pointer;
  }
  .zoom {
    display: flex;
    gap: 0.25rem;
  }
</style>
