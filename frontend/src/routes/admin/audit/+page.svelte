<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { api, ApiError, type Me } from '$lib/api';

	interface AuditEntry {
		actorId: number | null;
		actorEmail: string | null;
		action: string;
		target: string | null;
		detail: string | null;
		createdAt: string;
	}

	let entries = $state<AuditEntry[]>([]);
	let me = $state<Me | null>(null);
	let error = $state('');
	let loading = $state(true);

	function formatAction(action: string): string {
		return action.replaceAll('_', ' ').toLowerCase();
	}

	onMount(async () => {
		try {
			me = await api<Me>('/auth/me');
			if (me.role !== 'ADMIN') {
				goto('/links');
				return;
			}
			entries = await api<AuditEntry[]>('/admin/audit?limit=100');
		} catch (e) {
			if (e instanceof ApiError && (e.status === 403 || e.status === 401)) {
				goto('/links');
				return;
			}
			error = 'Failed to load audit log';
		} finally {
			loading = false;
		}
	});
</script>

<div class="page">
	<a href="/admin" class="back text-secondary">← Admin</a>
	<div class="head">
		<h1>Audit Log</h1>
		<a class="btn btn-ghost btn-sm" href="/api/v1/admin/audit/export">Export CSV</a>
	</div>

	{#if error}
		<p class="error-text">{error}</p>
	{/if}

	{#if loading}
		<p class="text-secondary" style="text-align:center; padding: 3rem;">Loading...</p>
	{:else if entries.length === 0}
		<div class="card empty">
			<p class="text-secondary">No audit entries yet.</p>
		</div>
	{:else}
		<div class="timeline">
			{#each entries as e (e.createdAt + e.action + (e.target || ''))}
				<div class="card entry">
					<span class="chip">{formatAction(e.action)}</span>
					<span class="target mono">{e.target}</span>
					<span class="text-secondary actor">{e.actorEmail || 'system'}</span>
					<span class="text-secondary time">{new Date(e.createdAt).toLocaleString()}</span>
				</div>
			{/each}
		</div>
	{/if}
</div>

<style>
	.page {
		padding: 2.5rem 0 4rem;
		max-width: 900px;
	}
	.back {
		text-decoration: none;
	}
	.head {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin: 0.75rem 0 1.25rem;
	}
	h1 {
		font-size: 1.75rem;
		margin: 0;
	}
	.timeline {
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
	}
	.entry {
		display: flex;
		align-items: center;
		gap: 1rem;
		padding: 0.75rem 1rem;
	}
	.target {
		flex: 1;
		overflow-wrap: anywhere;
		font-size: 0.9rem;
	}
	.actor {
		font-size: 0.85rem;
	}
	.time {
		font-size: 0.8rem;
		white-space: nowrap;
	}
	.empty {
		text-align: center;
		padding: 3rem;
	}
</style>