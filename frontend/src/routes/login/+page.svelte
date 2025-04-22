<script lang="ts">
	import { goto } from "$app/navigation";
	import { api, ApiError } from "$lib/api";

	let email = $state("");
	let password = $state("");
	let error = $state("");
	let busy = $state(false);

	async function submit(e: SubmitEvent) {
		e.preventDefault();
		busy = true;
		error = "";
		try {
			await api("/auth/login", {
				method: "POST",
				body: JSON.stringify({ email, password }),
			});
			goto("/links");
		} catch (e) {
			error = e instanceof ApiError ? e.message : "Login failed";
		} finally {
			busy = false;
		}
	}
</script>

<div class="auth-wrap">
	<form class="card auth-card" onsubmit={submit}>
		<h1>Welcome back</h1>
		<p class="text-secondary">Log in to manage your links.</p>

		<label for="email">Email</label>
		<input
			id="email"
			type="email"
			bind:value={email}
			required
			autocomplete="email"
		/>

		<label for="password">Password</label>
		<input
			id="password"
			type="password"
			bind:value={password}
			required
			autocomplete="current-password"
		/>

		{#if error}
			<p class="error-text">{error}</p>
		{/if}

		<button class="btn btn-primary" type="submit" disabled={busy}>
			{busy ? "Logging in..." : "Log in"}
		</button>

		<p class="text-secondary">
			No account? <a href="/register">Register</a>
		</p>
	</form>
</div>

<style>
	.auth-wrap {
		display: flex;
		justify-content: center;
		padding: 5rem 0;
	}
	.auth-card {
		width: 100%;
		max-width: 400px;
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
	}
	h1 {
		margin: 0 0 0.25rem;
		font-size: 1.6rem;
	}
	.auth-card .btn {
		margin-top: 1rem;
	}
	a {
		color: var(--accent);
	}
</style>
