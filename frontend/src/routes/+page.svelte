<script lang="ts">
	import { onMount } from "svelte";
	import { api, ApiError, type UrlResponse, type Me } from "$lib/api";

	let url = $state("");
	let alias = $state("");
	let expiresAt = $state("");
	let password = $state("");
	let showOptions = $state(false);
	let creating = $state(false);
	let error = $state("");
	let result = $state<UrlResponse | null>(null);
	let copied = $state(false);
	let me = $state<Me | null>(null);

	function normalize(raw: string): string {
		const trimmed = raw.trim();
		if (!trimmed || trimmed.includes("://")) return trimmed;
		return "https://" + trimmed;
	}

	onMount(async () => {
		try {
			me = await api<Me>("/auth/me");
		} catch {
			me = null;
		}
	});

	async function create(e: SubmitEvent) {
		e.preventDefault();
		creating = true;
		error = "";
		try {
			const body: Record<string, string> = { url: normalize(url) };
			if (alias) body.alias = alias;
			if (expiresAt) body.expiresAt = new Date(expiresAt).toISOString();
			if (password) body.password = password;
			result = await api<UrlResponse>("/links", {
				method: "POST",
				body: JSON.stringify(body),
			});
		} catch (e) {
			error = e instanceof ApiError ? e.message : "Something went wrong";
		} finally {
			creating = false;
		}
	}

	async function copyShort() {
		if (!result) return;
		await navigator.clipboard.writeText(result.shortUrl);
		copied = true;
		setTimeout(() => (copied = false), 2000);
	}

	function shareUrl(): string {
		return result ? encodeURIComponent(result.shortUrl) : "";
	}
</script>

<section class="hero">
	<h1>Short links, <span class="gradient-text">done right</span></h1>
	<p class="text-secondary">
		Create branded links, track analytics, custom password protection and
		more.
	</p>

	<form onsubmit={create} class="create-card card">
		<div class="row">
			<input
				type="text"
				bind:value={url}
				placeholder="example.com or https://paste-your-long-url-here.com/..."
				required
			/>
			<button
				class="btn btn-primary"
				type="submit"
				disabled={creating || !url}
			>
				{creating ? "Creating..." : "Shorten"}
			</button>
		</div>
		<button
			type="button"
			class="options-toggle"
			onclick={() => (showOptions = !showOptions)}
		>
			{showOptions
				? "Hide options"
				: me
					? "Options (alias, expiry, password)"
					: "Options (expiry)"}
		</button>
		{#if showOptions}
			<div class="options-grid">
				{#if me}
					<div>
						<label for="alias">Custom alias</label>
						<input
							id="alias"
							type="text"
							bind:value={alias}
							placeholder="my-link"
						/>
					</div>
				{/if}
				<div>
					<label for="expires"
						>Expires at{me ? "" : " (required, max 7 days)"}</label
					>
					<input
						id="expires"
						type="datetime-local"
						bind:value={expiresAt}
					/>
				</div>
				{#if me}
					<div>
						<label for="password">Password</label>
						<input
							id="password"
							type="password"
							bind:value={password}
							placeholder="Optional"
						/>
					</div>
				{/if}
			</div>
			{#if !me}
				<p class="text-secondary hint">
					<a href="/register">Create an account</a> for custom aliases,
					passwords and permanent links.
				</p>
			{/if}
		{/if}
	</form>

	{#if error}
		<p class="error-text">{error}</p>
	{/if}

	{#if result}
		<div class="card result-card">
			<div class="result-row">
				<a
					href={result.shortUrl}
					target="_blank"
					rel="noopener noreferrer"
					class="mono short-url"
				>
					{result.shortUrl}
				</a>
				<button class="btn btn-ghost btn-sm" onclick={copyShort}>
					{copied ? "Copied!" : "Copy"}
				</button>
			</div>
			<div class="share-row">
				<a
					class="btn btn-ghost btn-sm"
					href="https://twitter.com/intent/tweet?url={shareUrl()}"
					target="_blank"
					rel="noopener noreferrer">Share on X</a
				>
				<a
					class="btn btn-ghost btn-sm"
					href="https://www.facebook.com/sharer/sharer.php?u={shareUrl()}"
					target="_blank"
					rel="noopener noreferrer">Facebook</a
				>
				<a
					class="btn btn-ghost btn-sm"
					href="https://wa.me/?text={shareUrl()}"
					target="_blank"
					rel="noopener noreferrer">WhatsApp</a
				>
				<button
					class="btn btn-ghost btn-sm"
					onclick={() =>
						window.open(`/api/v1/links/${result?.code}/qr`)}
				>
					QR code
				</button>
			</div>
		</div>
	{/if}
</section>

<style>
	.hero {
		padding: 6rem 0 4rem;
		max-width: 720px;
		margin: 0 auto;
		text-align: center;
	}
	h1 {
		font-size: clamp(2.5rem, 6vw, 4rem);
		line-height: 1.15;
		margin: 0 0 1rem;
	}
	.hero > p {
		font-size: 1.1rem;
		margin: 0 0 2.5rem;
	}
	.create-card {
		text-align: left;
	}
	.row {
		display: flex;
		gap: 0.75rem;
	}
	.row input {
		flex: 1;
	}
	.options-toggle {
		background: none;
		border: none;
		color: var(--accent);
		font-size: 0.85rem;
		cursor: pointer;
		padding: 0.75rem 0 0;
		font-family: inherit;
	}
	.options-grid {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
		gap: 1rem;
		padding-top: 1rem;
	}
	.options-grid input {
		width: 100%;
		box-sizing: border-box;
	}
	.hint {
		padding-top: 0.75rem;
		margin: 0;
		font-size: 0.8rem;
	}
	.result-card {
		margin-top: 1.5rem;
	}
	.result-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 1rem;
	}
	.short-url {
		color: var(--text);
		font-size: 1.15rem;
		font-weight: 600;
		text-decoration: none;
		overflow-wrap: anywhere;
	}
	.share-row {
		display: flex;
		gap: 0.5rem;
		flex-wrap: wrap;
		margin-top: 1rem;
	}
	.error-text {
		margin-top: 1rem;
	}
	@media (max-width: 768px) {
		.row {
			flex-direction: column;
		}
		.options-grid {
			grid-template-columns: 1fr;
		}
	}
</style>
