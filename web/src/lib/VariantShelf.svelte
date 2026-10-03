<script lang="ts">
  import type { Variant } from "../lib/api";
  import { formatCents } from "../lib/notes";

  let {
    variants = [],
    activeId = null,
    originalActive = false,
    originalTitle = "original",
    originalAudioUrl = null,
    exportAllUrl = null,
    mediaUrlFor = null,
    renderSummary = "",
    renderMsg = "",
    rendering = false,
    onselect,
    onselectOriginal,
    ondelete,
    onrename,
    onrender,
  } = $props<{
    variants?: Variant[];
    activeId?: number | null;
    originalActive?: boolean;
    originalTitle?: string;
    originalAudioUrl?: string | null;
    exportAllUrl?: string | null;
    mediaUrlFor?: ((id: number) => string) | null;
    renderSummary?: string;
    renderMsg?: string;
    rendering?: boolean;
    onselect?: (v: Variant) => void;
    onselectOriginal?: () => void;
    ondelete?: (v: Variant) => void;
    onrename?: (v: Variant, name: string) => void;
    onrender?: () => void;
  }>();

  let renamingId = $state<number | null>(null);
  let renameValue = $state("");

  const below = $derived(variants.filter((v: Variant) => v.cents < 0));
  const atOrAbove = $derived(variants.filter((v: Variant) => v.cents >= 0));

  function label(v: Variant): string {
    const name = v.name?.trim();
    if (name) return name;
    return `${formatCents(v.cents)}c`;
  }

  function startRename(v: Variant) {
    renamingId = v.id;
    renameValue = v.name ?? "";
  }

  function commitRename(v: Variant) {
    if (renamingId === v.id) {
      onrename?.(v, renameValue);
      renamingId = null;
    }
  }
</script>

<div class="shelf">
  <div class="head">
    <span>pitches ({variants.length + 1})</span>
  </div>
  <div class="keep">
    <button type="button" onclick={onrender} disabled={rendering}>
      {rendering ? "rendering..." : "keep this pitch as variant"}
    </button>
    {#if renderMsg}
      <span class="summary" class:error={renderMsg.includes("failed")}>{renderMsg}</span>
    {:else if renderSummary}
      <span class="summary" title="exactly what the server will render and store">{renderSummary}</span>
    {/if}
    {#if exportAllUrl && variants.length > 0}
      <a class="export" href={exportAllUrl} download title="download every kept pitch as one zip, in the format above">
        export all {variants.length} pitches (.zip)
      </a>
    {/if}
  </div>
  {#snippet variantCard(v: Variant)}
    <div class="card" class:active={v.id === activeId}>
      <button class="pick" type="button" onclick={() => onselect?.(v)}>
        <span class="cents">{label(v)}</span>
        {#if v.name?.trim()}
          <span class="note">{formatCents(v.cents)}c</span>
        {:else if v.target_note}
          <span class="note">{v.src_note ?? "?"} to {v.target_note}</span>
        {/if}
        {#if v.section_start !== null}
          <span class="section">{v.section_start.toFixed(2)}-{v.section_end?.toFixed(2)}s</span>
        {/if}
      </button>
      <div class="actions">
        <button type="button" title="rename" onclick={() => startRename(v)}>
          name
        </button>
          <button type="button" title="delete" onclick={() => ondelete?.(v)}>
            delete
          </button>
          {#if mediaUrlFor}
            <a class="dl" href={mediaUrlFor(v.id)} download title="download this pitch">dl</a>
          {/if}
        </div>
      {#if renamingId === v.id}
        <div class="rename-row">
          <input
            bind:value={renameValue}
            placeholder="name this pitch"
            onkeydown={(e) => {
              if (e.key === "Enter") commitRename(v);
              if (e.key === "Escape") renamingId = null;
            }}
          />
          <button type="button" onclick={() => commitRename(v)}>save</button>
        </div>
      {/if}
    </div>
  {/snippet}

  <div class="grid">
    {#each below as v (v.id)}
      {@render variantCard(v)}
    {/each}

    <div class="card original" class:active={originalActive}>
      <button class="pick" type="button" onclick={() => onselectOriginal?.()}>
        <span class="cents">original</span>
        <span class="note">{originalTitle}</span>
        <span class="note">+0c</span>
      </button>
      <div class="actions">
        {#if originalAudioUrl}
          <a class="dl" href={originalAudioUrl} download>download</a>
        {:else}
          <span class="note">no audio</span>
        {/if}
      </div>
    </div>

    {#each atOrAbove as v (v.id)}
      {@render variantCard(v)}
    {/each}
  </div>
</div>

<style>
  .shelf {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
  }
  .head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 0.85rem;
    color: #aaa;
  }
  .export {
    color: #6aa9ff;
    font-size: 0.75rem;
    text-decoration: none;
    border: 1px solid #333;
    border-radius: 0.3rem;
    padding: 0.25rem 0.5rem;
  }
  .keep {
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
    align-items: flex-start;
  }
  .keep button {
    background: #1b1b1f;
    color: #6aa9ff;
    border: 1px solid #333;
    border-radius: 0.3rem;
    padding: 0.35rem 0.7rem;
    cursor: pointer;
  }
  .summary {
    font-size: 0.72rem;
    color: #888;
    font-variant-numeric: tabular-nums;
  }
  .summary.error {
    color: #ff9b9b;
  }
  .grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(9rem, 1fr));
    gap: 0.5rem;
  }
  .card {
    background: #16161a;
    border: 1px solid #333;
    border-radius: 0.5rem;
    overflow: hidden;
    display: flex;
    flex-direction: column;
  }
  .card.active {
    border-color: #6aa9ff;
  }
  .card.original {
    border-style: dashed;
  }
  .pick {
    background: none;
    border: none;
    color: #eee;
    text-align: left;
    padding: 0.5rem;
    cursor: pointer;
    display: flex;
    flex-direction: column;
    gap: 0.15rem;
  }
  .cents {
    font-size: 1.1rem;
    font-variant-numeric: tabular-nums;
  }
  .note,
  .section {
    font-size: 0.7rem;
    color: #888;
  }
  .actions {
    display: flex;
    border-top: 1px solid #26262c;
  }
  .actions button {
    flex: 1;
    background: none;
    border: none;
    color: #888;
    font-size: 0.7rem;
    padding: 0.3rem;
    cursor: pointer;
  }
  .actions a.dl {
    flex: 1;
    color: #888;
    font-size: 0.7rem;
    padding: 0.3rem;
    text-align: center;
    text-decoration: none;
  }
  .rename-row {
    display: flex;
    gap: 0.25rem;
    padding: 0.35rem;
    border-top: 1px solid #26262c;
  }
  .rename-row input {
    flex: 1;
    min-width: 0;
    background: #0e0e12;
    border: 1px solid #333;
    border-radius: 0.3rem;
    color: #eee;
    padding: 0.2rem 0.35rem;
    font-size: 0.75rem;
  }
  .rename-row button {
    background: none;
    border: 1px solid #333;
    border-radius: 0.3rem;
    color: #6aa9ff;
    font-size: 0.7rem;
    padding: 0.2rem 0.4rem;
    cursor: pointer;
  }
</style>
