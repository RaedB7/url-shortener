<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { api, ApiError, type Me } from '$lib/api';

	interface Overview {
		totalUsers: number;
		totalLinks: number;
		totalClicks: number;
		topUrl: string;
		topUrlClicks: number;
		rateLimitDenies: number;
		ssrfBlocks: number;
	}

	let overview = $state<Overview | null>(null);
	let error = $state('');
	let me = $state<Me | null>(null);

	onMount(async () => {
		try {
			me = await api<Me>('/auth/me');
			if (me.role !== 'ADMIN') {
				goto('/links');
				return;
			}
			overview = await api<Overview>('/admin/overview');
		} catch {
			goto('/login');
		}
	});
</script>

<div class="page">
	<h1>Admin Overview</h1>

	{#if error}
		<p class="error-text">{error}</p>
	{/if}

	{#if overview}
		<div class="stat-row">
			<div class="card stat">
				<p class="text-secondary">Users</p>
				<p class="big">{overview.totalUsers}</p>
			</div>
			<div class="card stat">
				<p class="text-secondary">Links</p>
				<p class="big">{overview.totalLinks}</p>
			</div>
			<div class="card stat">
				<p class="text-secondary">Clicks</p>
				<p class="big">{overview.totalClicks}</p>
			</div>
			<div class="card stat">
				<p class="text-secondary">Rate-limit denies</p>
				<p class="big">{overview.rateLimitDenies}</p>
			</div>
			<div class="card stat">
				<p class="text-secondary">SSRF blocks</p>
				<p class="big">{overview.ssrfBlocks}</p>
			</div>
		</div>

		<div class="card">
			<h2>Top link</h2>
			{#if overview.topUrl !== 'N/A'}
				<p class="mono">/{overview.topUrl} <span class="text-secondary">with {overview.topUrlClicks} clicks</span></p>
			{:else}
				<p class="text-secondary">No clicks yet.</p>
			{/if}
		</div>

		<nav class="admin-nav">
			<a href="/admin/users">Users</a>
			<a href="/admin/links">All Links</a>
			<a href="/admin/audit">Audit Log</a>
		</nav>
	{/if}
</div>

<style>
	.page {
		padding: 2.5rem 0 4rem;
	}
	h1 {
		font-size: 1.75rem;
		margin: 0 0 1.5rem;
	}
	h2 {
		font-size: 1rem;
		margin: 0 0 0.5rem;
	}
	.stat-row {
		display: grid;
		grid-template-columns: repeat(5, 1fr);
		gap: 1rem;
		margin-bottom: 1.25rem;
	}
	.stat .big {
		font-size: 1.5rem;
		font-weight: 700;
		margin: 0.25rem 0 0;
	}
	.admin-nav {
		display: flex;
		gap: 1rem;
		margin-top: 1.5rem;
	}
	.admin-nav a {
		color: var(--accent);
		text-decoration: none;
		font-weight: 600;
	}
	@media (max-width: 900px) {
		.stat-row {
			grid-template-columns: repeat(2, 1fr);
		}
	}
</style>