import { describe, expect, it, vi } from "vitest";
import { ApiClient } from "./api";

function response(body: unknown, ok = true, status = 200): Response {
  return {
    ok,
    status,
    json: async () => body,
    text: async () => (typeof body === "string" ? body : JSON.stringify(body)),
  } as unknown as Response;
}

function mockFetch(payloads: Record<string, unknown>): typeof fetch {
  const fn = vi.fn(async (input: RequestInfo | URL, _init?: RequestInit) => {
    const url = String(input);
    return response(payloads[url] ?? {});
  });
  return fn as unknown as typeof fetch;
}

describe("ApiClient", () => {
  it("builds a media url", () => {
    const c = new ApiClient();
    expect(c.mediaUrl(7)).toBe("/api/media/7");
    const c2 = new ApiClient("http://x:7373");
    expect(c2.mediaUrl(3)).toBe("http://x:7373/api/media/3");
  });

  it("builds a track audio url", () => {
    const c = new ApiClient();
    expect(c.trackAudioUrl(4)).toBe("/api/tracks/4/audio");
  });

  it("renames a track via PATCH", async () => {
    const spy = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) =>
      response({ track: { id: 1, title: "New" } }),
    );
    globalThis.fetch = spy as unknown as typeof fetch;
    const c = new ApiClient();
    const r = await c.renameTrack(1, "New", "Artist");
    expect(r).toEqual({ track: { id: 1, title: "New" } });
    const call = spy.mock.calls[0];
    expect(String(call[0])).toBe("/api/tracks/1");
    expect((call[1] as RequestInit).method).toBe("PATCH");
    expect(JSON.parse((call[1] as RequestInit).body as string)).toEqual({
      title: "New",
      artist: "Artist",
    });
  });

  it("deletes a track via DELETE", async () => {
    const spy = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) =>
      response({ deleted: 1 }),
    );
    globalThis.fetch = spy as unknown as typeof fetch;
    const c = new ApiClient();
    await c.deleteTrack(1);
    const call = spy.mock.calls[0];
    expect(String(call[0])).toBe("/api/tracks/1");
    expect((call[1] as RequestInit).method).toBe("DELETE");
  });

  it("fetches tracks", async () => {
    const tracks = [{ id: 1, title: "A" }];
    globalThis.fetch = mockFetch({ "/api/tracks": tracks });
    const c = new ApiClient();
    expect(await c.listTracks()).toEqual(tracks);
  });

  it("posts a shift body", async () => {
    const spy = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) =>
      response({ id: 9, cents: -100 }),
    );
    globalThis.fetch = spy as unknown as typeof fetch;
    const c = new ApiClient();
    const v = await c.shift(1, { cents: -100, formant: true });
    expect(v).toEqual({ id: 9, cents: -100 });
    const call = spy.mock.calls[0];
    expect(String(call[0])).toBe("/api/tracks/1/shift");
    expect(JSON.parse(call[1]!.body as string)).toEqual({ cents: -100, formant: true });
  });

  it("encodes interval query", async () => {
    globalThis.fetch = mockFetch({ "/api/interval?source=C%234&target=C4": { cents: -100 } });
    const c = new ApiClient();
    const r = await c.interval("C#4", "C4");
    expect(r.cents).toBe(-100);
  });

  it("throws on error responses", async () => {
    globalThis.fetch = (async () => response("not found", false, 404)) as unknown as typeof fetch;
    const c = new ApiClient();
    await expect(c.listTracks()).rejects.toThrow("404");
  });
});
