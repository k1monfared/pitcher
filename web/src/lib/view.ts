export interface View {
  start: number;
  end: number;
}

export const MIN_VIEW_SPAN = 0.25;

export function clampView(start: number, end: number, duration: number): View | null {
  if (duration <= 0) return null;
  let span = Math.min(Math.max(end - start, MIN_VIEW_SPAN), duration);
  if (span >= duration) return null;
  let s = Math.max(0, Math.min(start, duration - span));
  return { start: s, end: s + span };
}

export function zoomAt(
  view: View | null,
  center: number,
  factor: number,
  duration: number,
): View | null {
  const cur = view ?? { start: 0, end: duration };
  const span = cur.end - cur.start;
  if (span <= 0 || duration <= 0) return null;
  const c = Math.max(0, Math.min(center, duration));
  const newSpan = span * factor;
  if (newSpan >= duration) return null;
  const ratio = (c - cur.start) / span;
  const start = c - ratio * newSpan;
  return clampView(start, start + newSpan, duration);
}

export function zoomCenter(
  view: View | null,
  factor: number,
  duration: number,
): View | null {
  const cur = view ?? { start: 0, end: duration };
  return zoomAt(cur, (cur.start + cur.end) / 2, factor, duration);
}

export function panBy(
  view: View | null,
  deltaSeconds: number,
  duration: number,
): View | null {
  const cur = view ?? { start: 0, end: duration };
  const span = cur.end - cur.start;
  if (span >= duration || duration <= 0) return null;
  return clampView(cur.start + deltaSeconds, cur.end + deltaSeconds, duration);
}

export function centerOn(
  view: View | null,
  t: number,
  duration: number,
): View | null {
  const cur = view ?? { start: 0, end: duration };
  const span = cur.end - cur.start;
  if (span >= duration || duration <= 0) return null;
  const c = Math.max(0, Math.min(t, duration));
  return clampView(c - span / 2, c + span / 2, duration);
}

export function smoothFollow(
  view: View | null,
  t: number,
  duration: number,
  ahead = 0.3,
  factor = 0.35,
): View | null {
  if (!view || duration <= 0) return view;
  const span = view.end - view.start;
  if (span >= duration) return null;
  const want = Math.max(0, Math.min(t - (1 - ahead) * span, duration - span));
  const next = view.start + (want - view.start) * factor;
  if (Math.abs(next - view.start) < span * 0.001) return view;
  return { start: next, end: next + span };
}

export type KeyAction =
  | { type: "seek"; delta: number }
  | { type: "nudge"; delta: number };

const SEEK_STEPS = [1, 5, 15];
const NUDGE_STEPS = [1, 5, 10];

export function keyAction(
  key: string,
  shift: boolean,
  ctrl: boolean,
): KeyAction | null {
  const level = ctrl ? 2 : shift ? 1 : 0;
  switch (key) {
    case "ArrowLeft":
      return { type: "seek", delta: -SEEK_STEPS[level] };
    case "ArrowRight":
      return { type: "seek", delta: SEEK_STEPS[level] };
    case "ArrowUp":
      return { type: "nudge", delta: NUDGE_STEPS[level] };
    case "ArrowDown":
      return { type: "nudge", delta: -NUDGE_STEPS[level] };
    default:
      return null;
  }
}

export function isTextEntry(target: EventTarget | null): boolean {
  if (typeof HTMLElement === "undefined") return false;
  if (!(target instanceof HTMLElement)) return false;
  if (target.isContentEditable) return true;
  const tag = target.tagName;
  return tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT";
}
