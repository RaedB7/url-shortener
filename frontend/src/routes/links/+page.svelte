<script lang="ts">
	import { onMount } from "svelte";
	import { goto } from "$app/navigation";
	import {
		api,
		ApiError,
		type UrlResponse,
		type PageResult,
		type Me,
	} from "$lib/api";

	let links = $state<UrlResponse[]>([]);
	let total = $state(0);
	let totalPages = $state(0);
	let page = $state(0);
	let search = $state("");
	let loading = $state(true);
	let error = $state("");
	let me = $state<Me | null>(null);
	let deleteTarget = $state<string | null>(null);
	let deleting = $state(false);
	let copied = $state<string | null>(null);
	let debounce: ReturnType<typeof setTimeout>;

	async function load(p = 0) {
		loading = true;
		error = "";
		try {
			const params = new URLSearchParams({ page: String(p), size: "20" });
			if (search) params.set("search", search);
			const data = await api<PageResult<UrlResponse>>(`/links?${params}`);
			links = data.content;
			total = data.totalElements;
			totalPages = data.totalPages;
			page = p;
		} catch (e) {
			if (e instanceof ApiError && e.status === 403) {
				goto("/login");
				return;
			}
			error = e instanceof ApiError ? e.message : "Failed to load links";
		} finally {
			loading = false;
		}
	}

	function onSearch(e: Event) {
		search = (e.target as HTMLInputElement).value;
		clearTimeout(debounce);
		debounce = setTimeout(() => load(0), 300);
	}

	async function copyLink(url: string, code: string) {
		await navigator.clipboard.writeText(url);
		copied = code;
		setTimeout(() => (copied = null), 1500);
	}

	async function confirmDelete() {
		if (!deleteTarget) return;
		deleting = true;
		try {
			await api(`/links/${deleteTarget}`, { method: "DELETE" });
			deleteTarget = null;
			await load(page);
		} catch (e) {
			error = e instanceof ApiError ? e.message : "Delete failed";
		} finally {
			deleting = false;
		}
	}

	function formatDate(d: string): string {
		return new Date(d).toLocaleDateString("en-US", {
			month: "short",
			day: "numeric",
		});
	}

	onMount(async () => {
		try {
			me = await api<Me>("/auth/me");
		} catch {
			goto("/login");
			return;
		}
		await load();
	});
</script>

<div class="page">
	<div class="head">
		<h1>My Links</h1>
		<a href="/" class="btn btn-primary btn-sm">+ New Link</a>
	</div>

	<input
		class="search"
		type="text"
		placeholder="Search links..."
		value={search}
		oninput={onSearch}
	/>

	{#if error}
		<p class="error-text">{error}</p>
	{/if}

	{#if loading}
		<p class="text-secondary" style="text-align:center; padding: 3rem;">
			Loading...
		</p>
	{:else if links.length === 0}
		<div class="card empty">
			<p>{search ? "No links match your search." : "No links yet."}</p>
			{#if !search}
				<a href="/" class="btn btn-primary">Create your first link</a>
			{/if}
		</div>
	{:else}
		<div class="card table-card">
			<table>
				<thead>
					<tr>
						<th>Short link</th>
						<th>Destination</th>
						<th>Created</th>
						<th style="width: 150px;">Actions</th>
					</tr>
				</thead>
				<tbody>
					{#each links as link (link.code)}
						<tr>
							<td>
								<a class="code mono" href="/links/{link.code}"
									>/{link.code}</a
								>
								{#if link.passwordProtected}
									<span class="chip lock">🔒</span>
								{/if}
							</td>
							<td class="dest" title={link.originalUrl}>
								<a
									href={link.originalUrl}
									target="_blank"
									rel="noopener noreferrer"
								>
									{link.originalUrl.length > 45
										? link.originalUrl.slice(0, 45) + "..."
										: link.originalUrl}
								</a>
							</td>
							<td class="text-secondary"
								>{formatDate(link.createdAt)}</td
							>
							<td>
								<div class="actions">
									<button
										class="btn btn-ghost btn-sm"
										onclick={() =>
											copyLink(link.shortUrl, link.code)}
									>
										{copied === link.code ? "✓" : "Copy"}
									</button>
									<a
										class="btn btn-ghost btn-sm"
										href="/links/{link.code}">Stats</a
									>
									{#if deleteTarget === link.code}
										<button
											class="btn btn-danger btn-sm"
											onclick={confirmDelete}
											disabled={deleting}
										>
											{deleting ? "..." : "Sure?"}
										</button>
										<button
											class="btn btn-ghost btn-sm"
											onclick={() =>
												(deleteTarget = null)}
											>No</button
										>
									{:else}
										<button
											class="btn btn-ghost btn-sm danger"
											onclick={() =>
												(deleteTarget = link.code)}
										>
											Delete
										</button>
									{/if}
								</div>
							</td>
						</tr>
					{/each}
				</tbody>
			</table>
		</div>

		<div class="pager">
			<p class="text-secondary">{total} link{total === 1 ? "" : "s"}</p>
			{#if totalPages > 1}
				<div class="pager-btns">
					<button
						class="btn btn-ghost btn-sm"
						disabled={page === 0}
						onclick={() => load(page - 1)}>←</button
					>
					<span class="text-secondary"
						>Page {page + 1} of {totalPages}</span
					>
					<button
						class="btn btn-ghost btn-sm"
						disabled={page >= totalPages - 1}
						onclick={() => load(page + 1)}>→</button
					>
				</div>
			{/if}
		</div>
	{/if}
</div>

<style>
	.page {
		padding: 2.5rem 0 4rem;
	}
	.head {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 1rem;
	}
	h1 {
		margin: 0;
		font-size: 1.75rem;
	}
	.search {
		width: 100%;
		max-width: 340px;
		margin-bottom: 1.25rem;
	}
	.table-card {
		padding: 0;
		overflow-x: auto;
	}
	table {
		width: 100%;
		border-collapse: collapse;
	}
	th,
	td {
		padding: 0.75rem 1rem;
		text-align: left;
		border-bottom: 1px solid var(--border);
	}
	th {
		font-size: 0.78rem;
		text-transform: uppercase;
		letter-spacing: 0.05em;
		color: var(--text-muted);
	}
	tr:last-child td {
		border-bottom: none;
	}
	tbody tr:hover {
		background: var(--surface-2);
	}
	.code {
		color: var(--accent);
		font-weight: 600;
		text-decoration: none;
	}
	.code:hover {
		text-decoration: underline;
	}
	.dest a {
		color: var(--text-muted);
		text-decoration: none;
		font-size: 0.9rem;
	}
	.dest a:hover {
		color: var(--text);
	}
	.actions {
		display: flex;
		gap: 0.4rem;
	}
	.danger {
		color: var(--error);
	}
	.empty {
		text-align: center;
		padding: 3rem;
	}
	.pager {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-top: 1rem;
	}
	.pager-btns {
		display: flex;
		align-items: center;
		gap: 0.75rem;
	}
	.lock {
		margin-left: 0.4rem;
	}
</style>
