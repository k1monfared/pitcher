<script lang="ts">
  import { onMount } from "svelte";
  import PitchFader from "./lib/PitchFader.svelte";
  import Waveform from "./lib/Waveform.svelte";
  import BookmarkStrip from "./lib/BookmarkStrip.svelte";
  import NoteTuner from "./lib/NoteTuner.svelte";
  import Transport from "./lib/Transport.svelte";
  import VariantShelf from "./lib/VariantShelf.svelte";
  import { ApiClient, type Bookmark, type Track, type Variant } from "./lib/api";
  import { computePeaks } from "./lib/audio";
  import { isTextEntry, keyAction } from "./lib/view";
  import { PitchAudioEngine } from "./lib/engine";
  import { centsBetweenHz, midiToHz, noteToMidi } from "./lib/notes";

  const api = new ApiClient();
  const engine = new PitchAudioEngine({
    onPosition: (t) => {
      time = t;
    },
  });

  let tracks: Track[] = $state([]);
  let activeTrack: Track | null = $state(null);
  let variants: Variant[] = $state([]);
  let bookmarks: Bookmark[] = $state([]);
  let activeVariant: Variant | null = $state(null);
  let cents = $state(0);
  let formant = $state(true);
  let outputFormat = $state("opus");
  let targetNote = $state("");
  let manualTargetHz = $state<number | null>(null);
  let manualHz = $state<number | null>(null);
  let detectedHz = $state<number | null>(null);
  let detectStatus = $state("");
  let loop = $state<[number, number] | null>(null);
  let playhead = $state(0);
  let time = $state(0);
  let playing = $state(false);
  let tempo = $state(1);
  let peaks: number[] = $state([]);
  let pathInput = $state("");
  let urlInput = $state("");
  let importingFile = $state(false);
  let importingUrl = $state(false);
  let rendering = $state(false);
  let importFileMsg = $state("");
  let importUrlMsg = $state("");
  let renderMsg = $state("");
  let status = $state("");

  onMount(() => {
    void refreshTracks();
    const togglePlay = () => {
      if (playing) {
        engine.pause();
        playing = false;
      } else if (engine.hasOriginal || engine.hasShifted) {
        engine.play();
        playing = true;
      }
    };

    const onKey = (e: KeyboardEvent) => {
      if (!activeTrack || isTextEntry(e.target)) return;
      if (e.key === " ") {
        e.preventDefault();
        togglePlay();
        return;
      }
      const action = keyAction(e.key, e.shiftKey, e.ctrlKey || e.metaKey);
      if (!action) return;
      e.preventDefault();
      if (action.type === "seek") {
        const dur = activeTrack.duration_s;
        const t = Math.max(0, Math.min(dur, time + action.delta));
        engine.seek(t);
        playhead = t;
      } else {
        const next = Math.max(-1200, Math.min(1200, Math.round(cents + action.delta)));
        cents = next;
        engine.setPitchCents(next);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  });

  async function refreshTracks() {
    try {
      tracks = await api.listTracks();
    } catch (e) {
      status = `cannot reach server: ${e}`;
    }
  }

  async function openTrack(track: Track) {
    engine.pause();
    playing = false;
    activeTrack = track;
    try {
      const detail = await api.getTrack(track.id);
      variants = detail.variants;
      bookmarks = await api.listBookmarks(track.id);
    } catch (e) {
      status = `cannot open track: ${e}`;
      return;
    }
    activeVariant = null;
    engine.clearShifted();
    cents = 0;
    engine.setPitchCents(0);
    peaks = [];
    time = 0;
    playhead = 0;
    try {
      const buf = await (await fetch(api.trackAudioUrl(track.id))).arrayBuffer();
      await engine.loadOriginal(buf);
      const ctx = new AudioContext();
      const audio = await ctx.decodeAudioData(buf.slice(0));
      peaks = computePeaks(audio.getChannelData(0), 2000);
      await ctx.close();
      engine.select("original");
      engine.seek(0);
    } catch (e) {
      status = `cannot load audio: ${e}`;
    }
  }

  async function doImportPath() {
    if (!pathInput || importingFile) return;
    importingFile = true;
    importFileMsg = "importing...";
    try {
      const r = await api.importPath(pathInput);
      await refreshTracks();
      await openTrack(r.track);
      importFileMsg = "";
    } catch (e) {
      importFileMsg = `import failed: ${e}`;
    } finally {
      importingFile = false;
    }
  }

  async function doImportUrl() {
    if (!urlInput || importingUrl) return;
    importingUrl = true;
    importUrlMsg = "downloading...";
    try {
      const r = await api.importUrl(urlInput);
      await refreshTracks();
      await openTrack(r.track);
      importUrlMsg = "";
    } catch (e) {
      importUrlMsg = `download failed: ${e}`;
    } finally {
      importingUrl = false;
    }
  }

  async function renderCurrent() {
    if (!activeTrack || rendering) return;
    rendering = true;
    renderMsg = "rendering...";
    try {
      const body: Record<string, unknown> = {
        cents,
        formant,
        format: outputFormat,
      };
      const srcHz = manualHz && manualHz > 0 ? manualHz : detectedHz;
      const dstHz = manualTargetHz && manualTargetHz > 0 ? manualTargetHz : null;
      if (srcHz && dstHz) {
        body.from_hz = srcHz;
        body.to_hz = dstHz;
      } else if (srcHz && targetNote) {
        body.from_hz = srcHz;
        body.target_note = targetNote;
      }
      if (loop) body.section = loop;
      const v = await api.shift(activeTrack.id, body);
      const detail = await api.getTrack(activeTrack.id);
      variants = detail.variants;
      activeVariant = v;
      await selectVariant(v);
      renderMsg = "";
    } catch (e) {
      renderMsg = `render failed: ${e}`;
    } finally {
      rendering = false;
    }
  }

  async function selectOriginal() {
    activeVariant = null;
    cents = 0;
    engine.select("original");
    engine.setPitchCents(0);
    await showOriginalPeaks();
  }

  async function showOriginalPeaks() {
    if (!activeTrack) return;
    try {
      const buf = await (await fetch(api.trackAudioUrl(activeTrack.id))).arrayBuffer();
      const ctx = new AudioContext();
      const audio = await ctx.decodeAudioData(buf);
      peaks = computePeaks(audio.getChannelData(0), 2000);
      await ctx.close();
    } catch {
      peaks = [];
    }
  }

  async function selectVariant(v: Variant) {
    activeVariant = v;
    cents = v.cents;
    formant = v.formant;
    const resume = engine.isPlaying;
    try {
      const buf = await (await fetch(api.mediaUrl(v.id))).arrayBuffer();
      await engine.loadShifted(buf);
      const ctx = new AudioContext();
      const audio = await ctx.decodeAudioData(buf.slice(0));
      peaks = computePeaks(audio.getChannelData(0), 2000);
      await ctx.close();
      engine.setPitchCents(v.cents);
      engine.select("variant", v.cents, v.id);
      if (resume) engine.play();
    } catch (e) {
      status = `cannot load variant: ${e}`;
    }
  }

  function fmtClock(t: number): string {
    const m = Math.floor(t / 60);
    const s = Math.floor(t % 60);
    return `${m}:${s.toString().padStart(2, "0")}`;
  }

  async function detectAtPlayhead() {
    if (!activeTrack) return;
    const at = time;
    try {
      const r = await api.detect(activeTrack.id, at);
      if (r.hz > 0 && r.note) {
        detectedHz = r.hz;
        detectStatus = "";
      } else {
        detectedHz = null;
        detectStatus = `no clear pitch at ${fmtClock(at)}`;
      }
    } catch (e) {
      detectedHz = null;
      detectStatus = `detect failed: ${e}`;
    }
  }

  async function applyIntervalToFader(c: number) {
    await selectOriginal();
    const rounded = Math.max(-1200, Math.min(1200, Math.round(c)));
    cents = rounded;
    engine.setPitchCents(rounded);
  }

  function onFaderChange(value: number) {
    cents = value;
    engine.setPitchCents(value);
  }

  function onTempoChange(value: number) {
    tempo = value;
    engine.setTempo(value);
  }

  const renderSummary = $derived.by(() => {
    const parts: string[] = [];
    const sign = cents >= 0 ? "+" : "";
    parts.push(`shift ${sign}${Math.round(cents)}c`);
    parts.push(formant ? "formants kept" : "formants shifted");
    if (loop) {
      parts.push(`${loop[0].toFixed(2)}-${loop[1].toFixed(2)}s section`);
    } else {
      parts.push("full track");
    }
    parts.push(outputFormat);
    return parts.join(" · ");
  });

  function onLoop(l: [number, number] | null) {
    loop = l;
    if (l) engine.setLoop({ start: l[0], end: l[1] });
    else engine.setLoop(null);
  }

  async function renameVariant(v: Variant, name: string) {
    try {
      const updated = await api.renameVariant(v.id, name);
      variants = variants.map((x) => (x.id === v.id ? updated : x));
    } catch (e) {
      status = `rename failed: ${e}`;
    }
  }

  async function addBookmark(name: string | null) {
    if (!activeTrack) return;
    try {
      await api.addBookmark(activeTrack.id, time, name ?? undefined);
      bookmarks = await api.listBookmarks(activeTrack.id);
    } catch (e) {
      status = `bookmark failed: ${e}`;
    }
  }

  async function renameBookmark(id: number, name: string) {
    if (!activeTrack) return;
    try {
      await api.renameBookmark(id, name);
      bookmarks = await api.listBookmarks(activeTrack.id);
    } catch (e) {
      status = `rename failed: ${e}`;
    }
  }

  async function deleteBookmark(id: number) {
    if (!activeTrack) return;
    try {
      await api.deleteBookmark(id);
      bookmarks = bookmarks.filter((b) => b.id !== id);
    } catch (e) {
      status = `delete failed: ${e}`;
    }
  }

  async function deleteVariant(v: Variant) {
    await api.deleteVariant(v.id);
    variants = variants.filter((x) => x.id !== v.id);
    if (activeVariant?.id === v.id) {
      activeVariant = null;
      engine.clearShifted();
    }
  }

  let renamingId = $state<number | null>(null);
  let renameTitle = $state("");
  let renameArtist = $state("");

  function startRename(t: Track) {
    renamingId = t.id;
    renameTitle = t.title;
    renameArtist = t.artist ?? "";
  }

  async function commitRename() {
    if (renamingId === null) return;
    try {
      const r = await api.renameTrack(renamingId, renameTitle, renameArtist);
      tracks = tracks.map((t) => (t.id === renamingId ? r.track : t));
      if (activeTrack?.id === renamingId) activeTrack = r.track;
    } catch (e) {
      status = `rename failed: ${e}`;
    } finally {
      renamingId = null;
    }
  }

  async function deleteTrack(t: Track) {
    if (!confirm(`Delete "${t.title}" and all its variants from disk?`)) return;
    try {
      await api.deleteTrack(t.id);
      tracks = tracks.filter((x) => x.id !== t.id);
      if (activeTrack?.id === t.id) {
        activeTrack = null;
        variants = [];
        bookmarks = [];
        activeVariant = null;
        peaks = [];
        engine.pause();
        playing = false;
      }
    } catch (e) {
      status = `delete failed: ${e}`;
    }
  }
</script>

<div class="app">
  <header>
    <h1>pitcher</h1>
    <span
      class="engine-badge live"
      title="live preview uses the SoundTouch engine: fader moves pitch at fixed tempo, speed moves tempo at fixed pitch. Files you keep are rendered with Rubber Band"
    >
      live preview: soundtouch · files: rubberband
    </span>
    <div class="import">
      <span class="import-group">
        <input bind:value={pathInput} placeholder="/path/to/audio.wav" />
        <button type="button" onclick={doImportPath} disabled={importingFile}>
          {importingFile ? "importing..." : "import file"}
        </button>
        {#if importFileMsg}<span class="action-msg">{importFileMsg}</span>{/if}
      </span>
      <span class="import-group">
        <input bind:value={urlInput} placeholder="https://youtube.com/..." />
        <button type="button" onclick={doImportUrl} disabled={importingUrl}>
          {importingUrl ? "downloading..." : "import url"}
        </button>
        {#if importUrlMsg}<span class="action-msg">{importUrlMsg}</span>{/if}
      </span>
    </div>
  </header>

  {#if status}<p class="status">{status}</p>{/if}

  <div class="layout">
    <aside class="tracks">
      <h2>shelf</h2>
      {#each tracks as t (t.id)}
        <div class="track-row" class:active={activeTrack?.id === t.id}>
          {#if renamingId === t.id}
            <input
              class="rename"
              bind:value={renameTitle}
              onkeydown={(e) => {
                if (e.key === "Enter") commitRename();
                if (e.key === "Escape") renamingId = null;
              }}
            />
            <input
              class="rename"
              bind:value={renameArtist}
              placeholder="artist"
              onkeydown={(e) => {
                if (e.key === "Enter") commitRename();
                if (e.key === "Escape") renamingId = null;
              }}
            />
            <button type="button" class="mini" onclick={commitRename}>save</button>
          {:else}
            <button type="button" class="open" onclick={() => openTrack(t)}>
              <span class="title">{t.title}</span>
              <span class="count">{t.variant_count}</span>
            </button>
            <button type="button" class="mini" title="rename" onclick={() => startRename(t)}>rename</button>
            <button type="button" class="mini danger" title="delete" onclick={() => deleteTrack(t)}>delete</button>
          {/if}
        </div>
      {/each}
      {#if tracks.length === 0}
        <p class="empty">import an audio file to begin</p>
      {/if}
    </aside>

    <main>
      {#if activeTrack}
        <div class="title-row">
          <h2>{activeTrack.title}</h2>
          {#if activeTrack.artist}
            <span class="artist">{activeTrack.artist}</span>
          {/if}
          <label class="formant">
            <input type="checkbox" bind:checked={formant} />
            preserve formants
          </label>
        </div>

        <Waveform
          {peaks}
          duration={activeTrack.duration_s}
          {loop}
          playhead={time}
          {bookmarks}
          onseek={(t) => {
            engine.seek(t);
            playhead = t;
          }}
          onloop={onLoop}
        />
        <BookmarkStrip
          {bookmarks}
          currentTime={time}
          onadd={addBookmark}
          onseek={(t) => {
            engine.seek(t);
            playhead = t;
          }}
          onrename={renameBookmark}
          onremove={deleteBookmark}
        />

        <Transport
          {playing}
          {tempo}
          tempoEnabled={true}
          time={time}
          duration={activeTrack.duration_s}
          onplay={() => {
            engine.play();
            playing = true;
          }}
          onpause={() => {
            engine.pause();
            playing = false;
          }}
          onseek={(t) => {
            engine.seek(t);
            playhead = t;
          }}
          ontempo={onTempoChange}
        />

        <div class="stage">
          <PitchFader bind:cents onchange={onFaderChange} />
          <div class="right">
            <NoteTuner
              detectedHz={detectedHz}
              bind:manualHz
              bind:targetNote
              bind:manualTargetHz
              faderCents={cents}
              statusText={detectStatus}
              ondetect={detectAtPlayhead}
              onapply={applyIntervalToFader}
            />
            <VariantShelf
              {variants}
              activeId={activeVariant?.id ?? null}
              originalActive={activeVariant === null}
              originalTitle={activeTrack.title}
              originalArtist={activeTrack.artist}
              originalAudioUrl={api.trackAudioUrl(activeTrack.id)}
              exportAllUrl={variants.length > 0 ? api.exportAllUrl(activeTrack.id, outputFormat) : null}
              outputFormat={outputFormat}
              mediaUrlFor={(id) => api.mediaUrl(id, outputFormat)}
              renderSummary={renderSummary}
              renderMsg={renderMsg}
              rendering={rendering}
              onselect={selectVariant}
              onselectOriginal={selectOriginal}
              ondelete={deleteVariant}
              onrename={renameVariant}
              onrender={renderCurrent}
              onformat={(f) => (outputFormat = f)}
            />
          </div>
        </div>
      {:else}
        <p class="empty">no track selected</p>
      {/if}
    </main>
  </div>
</div>

<style>
  :global(body) {
    margin: 0;
    background: #0e0e12;
    color: #e8e8ee;
    font-family: system-ui, -apple-system, sans-serif;
  }
  .app {
    max-width: 1200px;
    margin: 0 auto;
    padding: 1.5rem;
  }
  header {
    display: flex;
    gap: 1rem;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    border-bottom: 1px solid #26262c;
    padding-bottom: 1rem;
  }
  h1 {
    font-size: 1.3rem;
    margin: 0;
    letter-spacing: 0.02em;
  }
  .engine-badge {
    font-size: 0.7rem;
    color: #888;
    border: 1px solid #333;
    border-radius: 1rem;
    padding: 0.15rem 0.6rem;
  }
  .engine-badge.live {
    color: #7ddf9a;
    border-color: #2c5f3f;
  }
  .import {
    display: flex;
    gap: 1rem;
    flex-wrap: wrap;
  }
  .import-group {
    display: flex;
    gap: 0.5rem;
    align-items: center;
    flex-wrap: wrap;
  }
  .action-msg {
    font-size: 0.75rem;
    color: #ffd166;
    max-width: 22rem;
  }
  .import input {
    background: #16161a;
    border: 1px solid #333;
    border-radius: 0.3rem;
    color: #eee;
    padding: 0.3rem 0.5rem;
    width: 11rem;
  }
  .import button {
    background: #1b1b1f;
    color: #6aa9ff;
    border: 1px solid #333;
    border-radius: 0.3rem;
    padding: 0.3rem 0.6rem;
    cursor: pointer;
  }
  button:disabled {
    opacity: 0.5;
    cursor: default;
  }
  .status {
    color: #ffd166;
    font-size: 0.85rem;
  }
  .layout {
    display: grid;
    grid-template-columns: 15rem 1fr;
    gap: 1.5rem;
    margin-top: 1.5rem;
  }
  .tracks {
    display: flex;
    flex-direction: column;
    gap: 0.3rem;
  }
  .tracks h2 {
    font-size: 0.8rem;
    text-transform: uppercase;
    color: #888;
    letter-spacing: 0.05em;
  }
  .track-row {
    display: flex;
    gap: 0.25rem;
    align-items: center;
    border: 1px solid transparent;
    border-radius: 0.3rem;
    padding: 0.2rem;
  }
  .track-row.active {
    background: #16161a;
    border-color: #333;
  }
  .track-row button.open {
    flex: 1;
    display: flex;
    justify-content: space-between;
    background: none;
    border: none;
    border-radius: 0.3rem;
    color: #ccc;
    padding: 0.4rem 0.5rem;
    cursor: pointer;
    text-align: left;
  }
  .track-row button.mini {
    background: none;
    border: 1px solid #333;
    border-radius: 0.3rem;
    color: #777;
    font-size: 0.65rem;
    padding: 0.2rem 0.35rem;
    cursor: pointer;
  }
  .track-row button.mini.danger:hover {
    color: #ff6b6b;
    border-color: #ff6b6b;
  }
  .track-row input.rename {
    background: #0e0e12;
    border: 1px solid #333;
    border-radius: 0.3rem;
    color: #eee;
    padding: 0.25rem 0.4rem;
    width: 100%;
    font-size: 0.8rem;
  }
  .count {
    color: #666;
    font-size: 0.75rem;
  }
  .title-row {
    display: flex;
    gap: 1rem;
    align-items: center;
    flex-wrap: wrap;
    margin-bottom: 0.75rem;
  }
  .title-row h2 {
    margin: 0;
    font-size: 1.05rem;
  }
  .formant {
    display: flex;
    gap: 0.4rem;
    align-items: center;
    font-size: 0.8rem;
    color: #aaa;
  }
  .artist {
    font-size: 0.85rem;
    color: #888;
  }
  .stage {
    display: grid;
    grid-template-columns: auto 1fr;
    gap: 1.5rem;
    margin-top: 1.25rem;
    align-items: start;
  }
  .right {
    display: flex;
    flex-direction: column;
    gap: 1rem;
  }
  .empty {
    color: #666;
    font-size: 0.85rem;
  }
</style>
