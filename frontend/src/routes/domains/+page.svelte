<script lang="ts">
	import { onMount } from "svelte";
	import { goto } from "$app/navigation";
	import { api, ApiError, type Me } from "$lib/api";

	interface DomainRow {
		id: number;
		domain: string;
		verifyToken: string;
		verified: boolean;
		txtRecord: string;
	}

	let domains = $state<DomainRow[]>([]);
	let me = $state<Me | null>(null);
	let error = $state("");
	let notice = $state("");
	let newDomain = $state("");
	let busy = $state(false);
	let verifyingId = $state<number | null>(null);

	async function load() {
		try {
			domains = await api<DomainRow[]>("/domains");
		} catch (e) {
			if (
				e instanceof ApiError &&
				(e.status === 403 || e.status === 401)
			) {
				goto("/login");
				return;
			}
			error = "Failed to load domains";
		}
	}

	async function register(e: SubmitEvent) {
		e.preventDefault();
		busy = true;
		error = "";
		notice = "";
		try {
			const created = await api<DomainRow>("/domains", {
				method: "POST",
				body: JSON.stringify({ domain: newDomain }),
			});
			domains = [...domains, created];
			newDomain = "";
			notice = `Domain registered. Add the TXT record below, then click Verify.`;
		} catch (e) {
			error = e instanceof ApiError ? e.message : "Registration failed";
		} finally {
			busy = false;
		}
	}

	async function verify(d: DomainRow) {
		verifyingId = d.id;
		error = "";
		notice = "";
		try {
			const updated = await api<DomainRow>(`/domains/${d.id}/verify`, {
				method: "POST",
			});
			domains = domains.map((x) => (x.id === d.id ? updated : x));
			notice = updated.verified
				? `${updated.domain} verified! Create links while visiting from https://${updated.domain} to get branded short URLs.`
				: "Verification failed - DNS may take a few minutes to propagate.";
		} catch (e) {
			error = e instanceof ApiError ? e.message : "Verification failed";
		} finally {
			verifyingId = null;
		}
	}

	onMount(async () => {
		try {
			me = await api<Me>("/auth/me");
			if (!me) {
				goto("/login");
				return;
			}
			await load();
		} catch {
			goto("/login");
		}
	});
</script>

<svelte:head>
	<title>Domains - URLShortener</title>
</svelte:head>

<div class="page">
	<a href="/links" class="back text-secondary">← My Links</a>
	<h1>Custom Domains</h1>
	<p class="text-secondary">
		Verify ownership of a domain, then short links created while visiting
		that domain use it as the base (e.g. <span class="mono"
			>https://go.acme.test/abc123</span
		>).
	</p>

	<form class="card register" onsubmit={register}>
		<label for="domain">New domain</label>
		<div class="row">
			<input
				id="domain"
				type="text"
				bind:value={newDomain}
				placeholder="go.yourdomain.com"
				required
			/>
			<button
				class="btn btn-primary"
				type="submit"
				disabled={busy || !newDomain}
			>
				{busy ? "Registering..." : "Register"}
			</button>
		</div>
	</form>

	{#if error}
		<p class="error-text">{error}</p>
	{/if}
	{#if notice}
		<p class="success-text">{notice}</p>
	{/if}

	{#if domains.length === 0}
		<div class="card empty">
			<p class="text-secondary">No domains registered yet.</p>
		</div>
	{:else}
		<div class="card table-card">
			<table>
				<thead>
					<tr>
						<th>Domain</th>
						<th>TXT record to add</th>
						<th>Status</th>
						<th style="width: 100px;"></th>
					</tr>
				</thead>
				<tbody>
					{#each domains as d (d.id)}
						<tr>
							<td class="mono">{d.domain}</td>
							<td class="mono txt">{d.txtRecord}</td>
							<td>
								{#if d.verified}
									<span class="chip verified">verified</span>
								{:else}
									<span class="chip pending">unverified</span>
								{/if}
							</td>
							<td>
								{#if !d.verified}
									<button
										class="btn btn-ghost btn-sm"
										disabled={verifyingId === d.id}
										onclick={() => verify(d)}
									>
										{verifyingId === d.id
											? "Checking..."
											: "Verify"}
									</button>
								{/if}
							</td>
						</tr>
					{/each}
				</tbody>
			</table>
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
	h1 {
		font-size: 1.75rem;
		margin: 0.75rem 0 0.75rem;
	}
	.register {
		margin-bottom: 1.25rem;
	}
	.row {
		display: flex;
		gap: 0.75rem;
	}
	.row input {
		flex: 1;
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
	.txt {
		font-size: 0.8rem;
		color: var(--text-muted);
		overflow-wrap: anywhere;
	}
	.verified {
		color: var(--success);
		border-color: var(--success);
	}
	.pending {
		color: var(--text-muted);
	}
	.empty {
		text-align: center;
		padding: 2.5rem;
	}
	@media (max-width: 768px) {
		.row {
			flex-direction: column;
		}
	}
</style>
