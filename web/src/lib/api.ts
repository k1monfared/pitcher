export interface Track {
  id: number;
  source_path: string;
  source_kind: string;
  source_url: string | null;
  title: string;
  artist: string | null;
  duration_s: number;
  sample_rate: number | null;
  created_at: string;
  variant_count: number;
}

export interface Variant {
  id: number;
  track_id: number;
  name: string | null;
  cents: number;
  formant: boolean;
  engine: string;
  pitch_quality: string;
  section_start: number | null;
  section_end: number | null;
  output_path: string;
  output_format: string | null;
  src_note: string | null;
  src_hz: number | null;
  target_note: string | null;
  target_hz: number | null;
  favorite: boolean;
  created_at: string;
}

export interface Bookmark {
  id: number;
  track_id: number;
  t: number;
  name: string | null;
  created_at: string;
}

export interface NoteReading {
  name: string;
  midi: number;
  cents_off: number;
}

export interface PitchReading {
  hz: number;
  midi: number;
  note: NoteReading;
  confidence: number;
}

export interface ShiftBody {
  cents?: number;
  formant?: boolean;
  format?: string;
  to_note?: string;
  from_note?: string;
  from_hz?: number;
  to_hz?: number;
  section?: [number, number];
}

async function json<T>(resp: Response): Promise<T> {
  if (!resp.ok) {
    const text = await resp.text();
    throw new Error(`${resp.status}: ${text}`);
  }
  return (await resp.json()) as T;
}

export class ApiClient {
  constructor(private base = "") {}

  async health(): Promise<string> {
    const resp = await fetch(`${this.base}/api/health`);
    return resp.text();
  }

  async listTracks(): Promise<Track[]> {
    return json(await fetch(`${this.base}/api/tracks`));
  }

  async getTrack(id: number): Promise<{ track: Track; variants: Variant[] }> {
    return json(await fetch(`${this.base}/api/tracks/${id}`));
  }

  async importPath(path: string, title?: string): Promise<{ track: Track }> {
    return json(
      await fetch(`${this.base}/api/import`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ path, title }),
      }),
    );
  }

  async importUrl(url: string, title?: string): Promise<{ track: Track }> {
    return json(
      await fetch(`${this.base}/api/import`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ url, title }),
      }),
    );
  }

  async shift(trackId: number, body: ShiftBody): Promise<Variant> {
    return json(
      await fetch(`${this.base}/api/tracks/${trackId}/shift`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(body),
      }),
    );
  }

  async detect(trackId: number, at: number): Promise<PitchReading> {
    return json(
      await fetch(`${this.base}/api/detect`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ track: trackId, at }),
      }),
    );
  }

  async note(hz: number): Promise<{ hz: number; note: NoteReading }> {
    return json(await fetch(`${this.base}/api/note?hz=${hz}`));
  }

  async interval(source: string, target: string): Promise<{ cents: number }> {
    const q = new URLSearchParams({ source, target });
    return json(await fetch(`${this.base}/api/interval?${q}`));
  }

  async star(variantId: number, favorite: boolean): Promise<unknown> {
    return json(
      await fetch(`${this.base}/api/variants/${variantId}/star`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ favorite }),
      }),
    );
  }

  async deleteVariant(variantId: number): Promise<unknown> {
    return json(
      await fetch(`${this.base}/api/variants/${variantId}`, { method: "DELETE" }),
    );
  }

  async renameVariant(variantId: number, name: string): Promise<Variant> {
    return json(
      await fetch(`${this.base}/api/variants/${variantId}`, {
        method: "PATCH",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ name }),
      }),
    );
  }

  exportAllUrl(trackId: number, format?: string): string {
    const q = format ? `?format=${encodeURIComponent(format)}` : "";
    return `${this.base}/api/tracks/${trackId}/export${q}`;
  }

  mediaUrl(variantId: number, format?: string): string {
    const q = format ? `?format=${encodeURIComponent(format)}` : "";
    return `${this.base}/api/media/${variantId}${q}`;
  }

  trackAudioUrl(trackId: number): string {
    return `${this.base}/api/tracks/${trackId}/audio`;
  }

  async renameTrack(id: number, title?: string, artist?: string): Promise<{ track: Track }> {
    return json(
      await fetch(`${this.base}/api/tracks/${id}`, {
        method: "PATCH",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ title, artist }),
      }),
    );
  }

  async deleteTrack(id: number): Promise<unknown> {
    return json(await fetch(`${this.base}/api/tracks/${id}`, { method: "DELETE" }));
  }

  async listBookmarks(trackId: number): Promise<Bookmark[]> {
    return json(await fetch(`${this.base}/api/tracks/${trackId}/bookmarks`));
  }

  async addBookmark(trackId: number, t: number, name?: string): Promise<Bookmark> {
    return json(
      await fetch(`${this.base}/api/tracks/${trackId}/bookmarks`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ t, name }),
      }),
    );
  }

  async renameBookmark(id: number, name: string): Promise<unknown> {
    return json(
      await fetch(`${this.base}/api/bookmarks/${id}`, {
        method: "PATCH",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ name }),
      }),
    );
  }

  async deleteBookmark(id: number): Promise<unknown> {
    return json(await fetch(`${this.base}/api/bookmarks/${id}`, { method: "DELETE" }));
  }
}
