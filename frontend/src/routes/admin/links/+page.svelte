<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { api, ApiError, type Me, type UrlResponse, type PageResult } from '$lib/api';

	let links = $state<UrlResponse[]>([]);
	let total = $state(0);
	let totalPages = $state(0);
	let page = $state(0);
	let search = $state('');
	let error = $state('');
	let loading = $state(true);
	let me = $state<Me | null>(null);
	let deleteTarget = $state<string | null>(null);
	let deleting = $state(false);
	let debounce: ReturnType<typeof setTimeout>;

	async function load(p = 0) {
		loading = true;
		try {
			const params = new URLSearchParams({ page: String(p), size: '20' });
			if (search) params.set('search', search);
			const data = await api<PageResult<UrlResponse>>(`/admin/links?${params}`);
			links = data.content;
			total = data.totalElements;
			totalPages = data.totalPages;
			page = p;
		} catch (e) {
			if (e instanceof ApiError && (e.status === 403 || e.status === 401)) {
				goto('/links');
				return;
			}
			error = e instanceof ApiError ? e.message : 'Failed to load';
		} finally {
			loading = false;
		}
	}

	function onSearch(e: Event) {
		search = (e.target as HTMLInputElement).value;
		clearTimeout(debounce);
		debounce = setTimeout(() => load(0), 300);
	}

	async function confirmDelete() {
		if (!deleteTarget) return;
		deleting = true;
		try {
			await api(`/admin/links/${deleteTarget}`, { method: 'DELETE' });
			deleteTarget = null;
			await load(page);
		} catch (e) {
			error = e instanceof ApiError ? e.message : 'Delete failed';
		} finally {
			deleting = false;
		}
	}

	onMount(async () => {
		try {
			me = await api<Me>('/auth/me');
			if (me.role !== 'ADMIN') {
				goto('/links');
				return;
			}
			await load();
		} catch {
			goto('/login');
		}
	});
</script>

<div class="page">
	<a href="/admin" class="back text-secondary">← Admin</a>
	<h1>All Links</h1>

	<input class="search" type="text" placeholder="Search all links..." value={search} oninput={onSearch} />

	{#if error}
		<p class="error-text">{error}</p>
	{/if}

	{#if loading}
		<p class="text-secondary" style="text-align:center; padding: 3rem;">Loading...</p>
	{:else}
		<div class="card table-card">
			<table>
				<thead>
					<tr>
						<th>Short link</th>
						<th>Destination</th>
						<th style="width:110px;"></th>
					</tr>
				</thead>
				<tbody>
					{#each links as link (link.code)}
						<tr>
							<td><span class="mono code">/{link.code}</span></td>
							<td class="dest" title={link.originalUrl}>
								{link.originalUrl.length > 60 ? link.originalUrl.slice(0, 60) + '...' : link.originalUrl}
							</td>
							<td>
								{#if deleteTarget === link.code}
									<button class="btn btn-danger btn-sm" onclick={confirmDelete} disabled={deleting}>
										{deleting ? '...' : 'Sure?'}
									</button>
									<button class="btn btn-ghost btn-sm" onclick={() => (deleteTarget = null)}>No</button>
								{:else}
									<button class="btn btn-ghost btn-sm danger" onclick={() => (deleteTarget = link.code)}>
										Delete
									</button>
								{/if}
							</td>
						</tr>
					{/each}
				</tbody>
			</table>
		</div>

		<div class="pager">
			<p class="text-secondary">{total} link{total === 1 ? '' : 's'}</p>
			{#if totalPages > 1}
				<div class="pager-btns">
					<button class="btn btn-ghost btn-sm" disabled={page === 0} onclick={() => load(page - 1)}>←</button>
					<span class="text-secondary">Page {page + 1} of {totalPages}</span>
					<button class="btn btn-ghost btn-sm" disabled={page >= totalPages - 1} onclick={() => load(page + 1)}>→</button>
				</div>
			{/if}
		</div>
	{/if}
</div>

<style>
	.page {
		padding: 2.5rem 0 4rem;
	}
	.back {
		text-decoration: none;
	}
	h1 {
		font-size: 1.75rem;
		margin: 0.75rem 0 1.25rem;
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
	th, td {
		padding: 0.7rem 1rem;
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
	}
	.dest {
		color: var(--text-muted);
		font-size: 0.9rem;
		overflow-wrap: anywhere;
	}
	.danger {
		color: var(--error);
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
</style>