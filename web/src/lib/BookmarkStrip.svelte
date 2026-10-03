<script lang="ts">
  import type { Bookmark } from "./api";

  let {
    bookmarks = [],
    currentTime = 0,
    onadd,
    onseek,
    onrename,
    onremove,
  } = $props<{
    bookmarks?: Bookmark[];
    currentTime?: number;
    onadd?: (name: string | null) => void;
    onseek?: (t: number) => void;
    onrename?: (id: number, name: string) => void;
    onremove?: (id: number) => void;
  }>();

  let nameInput = $state("");
  let editingId = $state<number | null>(null);
  let editValue = $state("");

  function fmtClock(t: number): string {
    const m = Math.floor(t / 60);
    const s = Math.floor(t % 60);
    return `${m}:${s.toString().padStart(2, "0")}`;
  }

  function label(b: Bookmark): string {
    const n = b.name?.trim();
    return n ? `${n} · ${fmtClock(b.t)}` : fmtClock(b.t);
  }

  function startEdit(b: Bookmark) {
    editingId = b.id;
    editValue = b.name ?? "";
  }

  function commitEdit(b: Bookmark) {
    if (editingId === b.id) {
      onrename?.(b.id, editValue);
      editingId = null;
    }
  }
</script>

<div class="marks">
  <span class="add">
    <button type="button" onclick={() => onadd?.(nameInput.trim() || null)} title="bookmark the current spot">
      + mark @ {fmtClock(currentTime)}
    </button>
    <input
      bind:value={nameInput}
      placeholder="name (optional)"
      onkeydown={(e) => {
        if (e.key === "Enter") onadd?.(nameInput.trim() || null);
      }}
    />
  </span>
  {#each bookmarks as b (b.id)}
    <span class="chip">
      {#if editingId === b.id}
        <input
          bind:value={editValue}
          onkeydown={(e) => {
            if (e.key === "Enter") commitEdit(b);
            if (e.key === "Escape") editingId = null;
          }}
        />
        <button type="button" onclick={() => commitEdit(b)}>save</button>
      {:else}
        <button type="button" class="go" title="seek here" onclick={() => onseek?.(b.t)}>
          {label(b)}
        </button>
        <button type="button" title="rename" onclick={() => startEdit(b)}>edit</button>
        <button type="button" title="delete" onclick={() => onremove?.(b.id)}>×</button>
      {/if}
    </span>
  {/each}
</div>

<style>
  .marks {
    display: flex;
    gap: 0.4rem;
    align-items: center;
    flex-wrap: wrap;
    font-size: 0.75rem;
  }
  .add {
    display: flex;
    gap: 0.3rem;
    align-items: center;
  }
  .chip {
    display: flex;
    gap: 0.15rem;
    align-items: center;
    background: #16161a;
    border: 1px solid #333;
    border-radius: 1rem;
    padding: 0.1rem 0.15rem 0.1rem 0.3rem;
  }
  button {
    background: none;
    border: 1px solid #333;
    border-radius: 0.3rem;
    color: #aaa;
    padding: 0.2rem 0.45rem;
    cursor: pointer;
    font-size: 0.75rem;
  }
  button.go {
    border: none;
    color: #ffd166;
  }
  input {
    background: #0e0e12;
    border: 1px solid #333;
    border-radius: 0.3rem;
    color: #eee;
    padding: 0.2rem 0.4rem;
    font-size: 0.75rem;
    width: 8rem;
  }
</style>
