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

  function add() {
    onadd?.(nameInput.trim() || null);
    nameInput = "";
  }
</script>

<div class="marks">
  <div class="add">
    <button type="button" onclick={add} title="bookmark the current spot">
      + mark @ {fmtClock(currentTime)}
    </button>
    <input
      bind:value={nameInput}
      placeholder="name (optional)"
      onkeydown={(e) => {
        if (e.key === "Enter") add();
      }}
    />
  </div>
  {#if bookmarks.length === 0}
    <p class="empty">no bookmarks yet</p>
  {/if}
  <ul>
    {#each bookmarks as b (b.id)}
      <li>
        <button type="button" class="time" title="seek here" onclick={() => onseek?.(b.t)}>
          {fmtClock(b.t)}
        </button>
        {#if editingId === b.id}
          <input
            class="name-edit"
            bind:value={editValue}
            placeholder="name (optional)"
            onkeydown={(e) => {
              if (e.key === "Enter") commitEdit(b);
              if (e.key === "Escape") editingId = null;
            }}
          />
          <button type="button" onclick={() => commitEdit(b)}>save</button>
        {:else}
          <button type="button" class="name" title="rename" onclick={() => startEdit(b)}>
            {b.name?.trim() ? b.name : "(no name)"}
          </button>
          <button type="button" title="delete" onclick={() => onremove?.(b.id)}>×</button>
        {/if}
      </li>
    {/each}
  </ul>
</div>

<style>
  .marks {
    display: flex;
    flex-direction: column;
    gap: 0.4rem;
    font-size: 0.8rem;
  }
  .add {
    display: flex;
    gap: 0.4rem;
    align-items: center;
  }
  .empty {
    color: #666;
    font-size: 0.75rem;
    margin: 0;
  }
  ul {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
  }
  li {
    display: flex;
    gap: 0.4rem;
    align-items: center;
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
  button.time {
    color: #ffd166;
    font-variant-numeric: tabular-nums;
    min-width: 3.2rem;
  }
  button.name {
    border-color: transparent;
    color: #ddd;
    text-align: left;
  }
  input {
    background: #0e0e12;
    border: 1px solid #333;
    border-radius: 0.3rem;
    color: #eee;
    padding: 0.2rem 0.4rem;
    font-size: 0.75rem;
    width: 9rem;
  }
  input.name-edit {
    flex: 1;
  }
</style>
