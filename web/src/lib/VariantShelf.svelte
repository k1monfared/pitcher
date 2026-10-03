<script lang="ts">
  import type { Variant } from "../lib/api";
  import { formatCents } from "../lib/notes";

  let {
    variants = [],
    activeId = null,
    renderSummary = "",
    onselect,
    onstar,
    ondelete,
    onrender,
  } = $props<{
    variants?: Variant[];
    activeId?: number | null;
    renderSummary?: string;
    onselect?: (v: Variant) => void;
    onstar?: (v: Variant, favorite: boolean) => void;
    ondelete?: (v: Variant) => void;
    onrender?: () => void;
  }>();
</script>

<div class="shelf">
  <div class="head">
    <span>variants ({variants.length})</span>
  </div>
  <div class="keep">
    <button type="button" onclick={onrender}>keep this pitch as variant</button>
    {#if renderSummary}
      <span class="summary" title="exactly what the server will render and store">{renderSummary}</span>
    {/if}
  </div>
  <div class="grid">
    {#each variants as v (v.id)}
      <div class="card" class:active={v.id === activeId}>
        <button class="pick" type="button" onclick={() => onselect?.(v)}>
          <span class="cents">{formatCents(v.cents)}c</span>
          {#if v.target_note}
            <span class="note">{v.src_note ?? "?"} to {v.target_note}</span>
          {/if}
          {#if v.section_start !== null}
            <span class="section">{v.section_start.toFixed(2)}-{v.section_end?.toFixed(2)}s</span>
          {/if}
        </button>
        <div class="actions">
          <button
            type="button"
            class:starred={v.favorite}
            title="keep"
            onclick={() => onstar?.(v, !v.favorite)}
          >
            {v.favorite ? "kept" : "keep"}
          </button>
          <button type="button" title="delete" onclick={() => ondelete?.(v)}>
            delete
          </button>
        </div>
      </div>
    {/each}
    {#if variants.length === 0}
      <p class="empty">no variants yet</p>
    {/if}
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
  .actions button.starred {
    color: #ffd166;
  }
  .empty {
    color: #666;
    font-size: 0.8rem;
  }
</style>
