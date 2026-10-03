<script lang="ts">
  import { onMount } from "svelte";
  import PitchFader from "./lib/PitchFader.svelte";
  import Waveform from "./lib/Waveform.svelte";
  import NoteTuner from "./lib/NoteTuner.svelte";
  import Transport from "./lib/Transport.svelte";
  import VariantShelf from "./lib/VariantShelf.svelte";
  import { ApiClient, type Track, type Variant } from "./lib/api";
  import { computePeaks } from "./lib/audio";
  import { PitchAudioEngine } from "./lib/engine";
  import { centsBetweenHz, midiToHz, noteToMidi } from "./lib/notes";

  const api = new ApiClient();
  let livePitch = $state(false);
  const engine = new PitchAudioEngine({
    onPosition: (t) => {
      time = t;
    },
    onLiveChange: (live) => {
      livePitch = live;
    },
  });

  let tracks: Track[] = $state([]);
  let activeTrack: Track | null = $state(null);
  let variants: Variant[] = $state([]);
  let activeVariant: Variant | null = $state(null);
  let cents = $state(0);
  let snap = $state(0);
  let formant = $state(true);
  let outputFormat = $state("opus");
  let targetNote = $state("");
  let manualHz = $state<number | null>(null);
  let detectedHz = $state<number | null>(null);
  let loop = $state<[number, number] | null>(null);
  let playhead = $state(0);
  let time = $state(0);
  let playing = $state(false);
  let tempo = $state(1);
  let peaks: number[] = $state([]);
  let pathInput = $state("");
  let urlInput = $state("");
  let busy = $state(false);
  let status = $state("");

  onMount(async () => {
    await refreshTracks();
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
    } catch (e) {
      status = `cannot open track: ${e}`;
      return;
    }
    activeVariant = null;
    engine.clearShifted();
    cents = 0;
    engine.setBaseCents(0);
    engine.setPitchRatio(1);
    peaks = [];
    time = 0;
    playhead = 0;
    try {
      const buf = await (await fetch(api.trackAudioUrl(track.id))).arrayBuffer();
      await engine.loadOriginal(buf);
      const ctx = new AudioContext();
      const audio = await ctx.decodeAudioData(buf.slice(0));
      peaks = computePeaks(audio.getChannelData(0), 400);
      await ctx.close();
      engine.select("original");
    } catch (e) {
      status = `cannot load audio: ${e}`;
    }
  }

  async function doImportPath() {
    if (!pathInput) return;
    busy = true;
    status = "importing...";
    try {
      const r = await api.importPath(pathInput);
      await refreshTracks();
      await openTrack(r.track);
      status = "";
    } catch (e) {
      status = `import failed: ${e}`;
    } finally {
      busy = false;
    }
  }

  async function doImportUrl() {
    if (!urlInput) return;
    busy = true;
    status = "downloading...";
    try {
      const r = await api.importUrl(urlInput);
      await refreshTracks();
      await openTrack(r.track);
      status = "";
    } catch (e) {
      status = `download failed: ${e}`;
    } finally {
      busy = false;
    }
  }

  async function renderCurrent() {
    if (!activeTrack) return;
    busy = true;
    status = "rendering...";
    try {
      const body: Record<string, unknown> = {
        cents,
        formant,
        format: outputFormat,
      };
      if (manualHz && targetNote) {
        body.from_hz = manualHz;
        body.target_note = targetNote;
      } else if (targetNote && detectedHz) {
        body.from_hz = detectedHz;
        body.target_note = targetNote;
      }
      if (loop) body.section = loop;
      const v = await api.shift(activeTrack.id, body);
      const detail = await api.getTrack(activeTrack.id);
      variants = detail.variants;
      activeVariant = v;
      await selectVariant(v);
      status = "";
    } catch (e) {
      status = `render failed: ${e}`;
    } finally {
      busy = false;
    }
  }

  async function selectOriginal() {
    activeVariant = null;
    cents = 0;
    engine.setBaseCents(0);
    engine.setPitchRatio(1);
    engine.select("original");
    await showOriginalPeaks();
  }

  async function showOriginalPeaks() {
    if (!activeTrack) return;
    try {
      const buf = await (await fetch(api.trackAudioUrl(activeTrack.id))).arrayBuffer();
      const ctx = new AudioContext();
      const audio = await ctx.decodeAudioData(buf);
      peaks = computePeaks(audio.getChannelData(0), 400);
      await ctx.close();
    } catch {
      peaks = [];
    }
  }

  async function selectVariant(v: Variant) {
    activeVariant = v;
    cents = v.cents;
    formant = v.formant;
    try {
      const buf = await (await fetch(api.mediaUrl(v.id))).arrayBuffer();
      await engine.loadShifted(buf);
      const ctx = new AudioContext();
      const audio = await ctx.decodeAudioData(buf.slice(0));
      peaks = computePeaks(audio.getChannelData(0), 400);
      await ctx.close();
      // The file already carries the variant's shift, so the live engine
      // compensates: fader stays absolute from the original.
      engine.setBaseCents(v.cents);
      engine.setPitchRatio(Math.pow(2, v.cents / 1200));
      engine.select("variant");
    } catch (e) {
      status = `cannot load variant: ${e}`;
    }
  }

  async function detectAtPlayhead() {
    if (!activeTrack) return;
    try {
      const r = await api.detect(activeTrack.id, playhead);
      detectedHz = r.hz > 0 ? r.hz : null;
      if (!targetNote && r.note) {
        targetNote = "";
      }
    } catch (e) {
      status = `detect failed: ${e}`;
    }
  }

  function onFaderChange(value: number) {
    cents = value;
    engine.setPitchRatio(Math.pow(2, value / 1200));
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

  async function starVariant(v: Variant, favorite: boolean) {
    await api.star(v.id, favorite);
    variants = variants.map((x) => (x.id === v.id ? { ...x, favorite } : x));
  }

  async function renameVariant(v: Variant, name: string) {
    try {
      const updated = await api.renameVariant(v.id, name);
      variants = variants.map((x) => (x.id === v.id ? updated : x));
    } catch (e) {
      status = `rename failed: ${e}`;
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
      class="engine-badge"
      class:live={livePitch}
      title={livePitch
        ? "live preview uses the Rubber Band pitch engine: tempo stays fixed while you move the fader"
        : "live pitch engine unavailable: preview falls back to tape-style speed change"}
    >
      {livePitch ? "live pitch: rubberband" : "live pitch: basic"}
    </span>
    <div class="import">
      <input bind:value={pathInput} placeholder="/path/to/audio.wav" />
      <button type="button" onclick={doImportPath} disabled={busy}>import file</button>
      <input bind:value={urlInput} placeholder="https://youtube.com/..." />
      <button type="button" onclick={doImportUrl} disabled={busy}>import url</button>
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
          <label class="format">
            format
            <select bind:value={outputFormat}>
              <option value="opus">opus</option>
              <option value="flac">flac</option>
              <option value="mp3">mp3</option>
              <option value="ogg">ogg</option>
              <option value="m4a">m4a</option>
              <option value="wav">wav</option>
            </select>
          </label>
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
          onseek={(t) => {
            engine.seek(t);
            playhead = t;
          }}
          onloop={onLoop}
        />

        <Transport
          {playing}
          {tempo}
          tempoEnabled={livePitch}
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
          <PitchFader
            bind:cents
            bind:snap
            onchange={onFaderChange}
          />
          <div class="right">
            <NoteTuner
              detectedHz={detectedHz}
              bind:manualHz
              bind:targetNote
              ondetect={detectAtPlayhead}
            />
            <VariantShelf
              {variants}
              activeId={activeVariant?.id ?? null}
              originalActive={activeVariant === null}
              originalTitle={activeTrack.title}
              originalAudioUrl={api.trackAudioUrl(activeTrack.id)}
              exportAllUrl={variants.length > 0 ? api.exportAllUrl(activeTrack.id) : null}
              mediaUrlFor={(id) => api.mediaUrl(id)}
              renderSummary={renderSummary}
              onselect={selectVariant}
              onselectOriginal={selectOriginal}
              onstar={starVariant}
              ondelete={deleteVariant}
              onrename={renameVariant}
              onrender={renderCurrent}
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
    gap: 0.5rem;
    flex-wrap: wrap;
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
  .format,
  .formant {
    display: flex;
    gap: 0.4rem;
    align-items: center;
    font-size: 0.8rem;
    color: #aaa;
  }
  .format select {
    background: #16161a;
    color: #eee;
    border: 1px solid #333;
    border-radius: 0.3rem;
    padding: 0.2rem 0.4rem;
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
