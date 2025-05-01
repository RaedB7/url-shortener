<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { api, ApiError, type Me } from '$lib/api';

	interface UserRow {
		id: number;
		email: string;
		role: string;
		suspended: boolean;
		createdAt: string;
		linkCount: number;
	}

	let users = $state<UserRow[]>([]);
	let me = $state<Me | null>(null);
	let error = $state('');
	let busyId = $state<number | null>(null);

	async function toggleSuspend(u: UserRow) {
		busyId = u.id;
		error = '';
		try {
			await api(`/admin/users/${u.id}/suspend?suspended=${!u.suspended}`, { method: 'POST' });
			u.suspended = !u.suspended;
		} catch (e) {
			error = e instanceof ApiError ? e.message : 'Failed';
		} finally {
			busyId = null;
		}
	}

	onMount(async () => {
		try {
			me = await api<Me>('/auth/me');
			if (me.role !== 'ADMIN') {
				goto('/links');
				return;
			}
			users = await api<UserRow[]>('/admin/users');
		} catch {
			goto('/login');
		}
	});
</script>

<div class="page">
	<a href="/admin" class="back text-secondary">← Admin</a>
	<h1>Users</h1>

	{#if error}
		<p class="error-text">{error}</p>
	{/if}

	<div class="card table-card">
		<table>
			<thead>
				<tr>
					<th>Email</th>
					<th>Role</th>
					<th>Links</th>
					<th>Registered</th>
					<th>Status</th>
					<th style="width:110px;"></th>
				</tr>
			</thead>
			<tbody>
				{#each users as u (u.id)}
					<tr>
						<td>{u.email}</td>
						<td><span class="chip" class:chip-admin={u.role === 'ADMIN'}>{u.role}</span></td>
						<td>{u.linkCount}</td>
						<td class="text-secondary">{new Date(u.createdAt).toLocaleDateString()}</td>
						<td>
							{#if u.suspended}
								<span class="chip suspended">suspended</span>
							{:else}
								<span class="chip active">active</span>
							{/if}
						</td>
						<td>
							<button class="btn btn-ghost btn-sm" disabled={busyId === u.id} onclick={() => toggleSuspend(u)}>
								{u.suspended ? 'Unsuspend' : 'Suspend'}
							</button>
						</td>
					</tr>
				{/each}
			</tbody>
		</table>
	</div>
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
	.suspended {
		color: var(--error);
		border-color: var(--error);
	}
	.active {
		color: var(--success);
		border-color: var(--success);
	}
</style>