<script lang="ts">
	import "../app.css";
	import "@fontsource/inter/400.css";
	import "@fontsource/inter/600.css";
	import "@fontsource/inter/700.css";
	import { api } from "$lib/api";
	import type { Me } from "$lib/api";
	import { page as pageStore } from "$app/state";

	let { children } = $props();
	let me = $state<Me | null>(null);

	async function logout() {
		await api("/auth/logout", { method: "POST" }).catch(() => {});
		me = null;
	}

	async function refreshMe() {
		try {
			me = await api<Me>("/auth/me");
		} catch {
			me = null;
		}
	}

	$effect(() => {
		void pageStore.url.pathname;
		refreshMe();
	});
</script>

<svelte:head>
	<title>URLShortener</title>
</svelte:head>

<nav class="topnav">
	<div class="container nav-inner">
		<a href="/" class="logo"
			><span class="gradient-text">URL</span>Shortener</a
		>
		<div class="nav-links">
			{#if me}
				<a href="/links">My Links</a>
				<a href="/domains">Domains</a>
				{#if me.role === "ADMIN"}
					<a href="/admin">Admin</a>
					<a href="/admin/users" class="sub">Users</a>
					<a href="/admin/audit" class="sub">Audit</a>
				{/if}
				<span class="chip">{me.email}</span>
				<button class="btn btn-ghost btn-sm" onclick={logout}
					>Logout</button
				>
			{:else}
				<a href="/login">Login</a>
				<a href="/register" class="btn btn-primary btn-sm">Register</a>
			{/if}
		</div>
	</div>
</nav>

<main class="container">
	{@render children()}
</main>

<style>
	.topnav {
		border-bottom: 1px solid var(--border);
		background: rgba(11, 14, 20, 0.8);
		backdrop-filter: blur(10px);
		position: sticky;
		top: 0;
		z-index: 10;
	}
	.nav-inner {
		display: flex;
		align-items: center;
		justify-content: space-between;
		height: 64px;
	}
	.logo {
		font-size: 1.2rem;
		font-weight: 700;
		text-decoration: none;
		color: var(--text);
	}
	.nav-links {
		display: flex;
		align-items: center;
		gap: 1rem;
	}
	.nav-links a {
		color: var(--text-muted);
		text-decoration: none;
		font-size: 0.95rem;
		transition: color 0.15s;
	}
	.nav-links a:hover {
		color: var(--text);
	}
	.nav-links a.sub {
		font-size: 0.85rem;
		opacity: 0.75;
	}
</style>
