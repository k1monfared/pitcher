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
