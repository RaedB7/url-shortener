<script lang="ts">
	import { onMount } from "svelte";
	import { goto } from "$app/navigation";
	import { api, ApiError, type UrlResponse } from "$lib/api";
	import Chart from "chart.js/auto";

	interface LinkStats {
		code: string;
		totalClicks: number;
		uniqueVisitors: number;
		timeline: { day: string; clicks: number; uniques: number }[];
		referrers: { name: string; count: number }[];
		devices: { name: string; count: number }[];
		browsers: { name: string; count: number }[];
	}

	let link = $state<UrlResponse | null>(null);
	let stats = $state<LinkStats | null>(null);
	let error = $state("");
	let copied = $state(false);
	let showQr = $state(false);
	let charts: Chart[] = [];

	const dark = {
		grid: "#232A3B",
		ticks: "#8A93A6",
	};

	async function load(code: string) {
		try {
			link = await api<UrlResponse>(`/links/${code}`);
			stats = await api<LinkStats>(`/links/${code}/stats`);
			await new Promise((r) => setTimeout(r, 50));
			renderCharts();
		} catch (e) {
			if (
				e instanceof ApiError &&
				(e.status === 403 || e.status === 401)
			) {
				goto("/login");
				return;
			}
			error = e instanceof ApiError ? e.message : "Failed to load link";
		}
	}

	function renderCharts() {
		if (!stats) return;
		charts.forEach((c) => c.destroy());
		charts = [];

		const timelineCanvas = document.getElementById(
			"timeline-chart",
		) as HTMLCanvasElement;
		if (timelineCanvas) {
			charts.push(
				new Chart(timelineCanvas, {
					type: "line",
					data: {
						labels: stats.timeline.map((d) => d.day.slice(5)),
						datasets: [
							{
								label: "Clicks",
								data: stats.timeline.map((d) => d.clicks),
								borderColor: "#6366F1",
								backgroundColor: "rgba(99, 102, 241, 0.15)",
								fill: true,
								tension: 0.3,
							},
							{
								label: "Unique visitors",
								data: stats.timeline.map((d) => d.uniques),
								borderColor: "#34D399",
								backgroundColor: "rgba(52, 211, 153, 0.1)",
								fill: true,
								tension: 0.3,
							},
						],
					},
					options: {
						responsive: true,
						plugins: { legend: { labels: { color: dark.ticks } } },
						scales: {
							x: {
								grid: { color: dark.grid },
								ticks: { color: dark.ticks },
							},
							y: {
								grid: { color: dark.grid },
								ticks: { color: dark.ticks },
								beginAtZero: true,
							},
						},
					},
				}),
			);
		}

		const deviceCanvas = document.getElementById(
			"device-chart",
		) as HTMLCanvasElement;
		if (deviceCanvas) {
			charts.push(
				new Chart(deviceCanvas, {
					type: "doughnut",
					data: {
						labels: stats.devices.map((d) => d.name),
						datasets: [
							{
								data: stats.devices.map((d) => d.count),
								backgroundColor: [
									"#6366F1",
									"#8B5CF6",
									"#34D399",
								],
							},
						],
					},
					options: {
						responsive: true,
						plugins: {
							legend: {
								labels: { color: dark.ticks },
								position: "bottom",
							},
						},
					},
				}),
			);
		}

		const referrerCanvas = document.getElementById(
			"referrer-chart",
		) as HTMLCanvasElement;
		if (referrerCanvas) {
			charts.push(
				new Chart(referrerCanvas, {
					type: "bar",
					data: {
						labels: stats.referrers.map((r) => r.name.slice(0, 22)),
						datasets: [
							{
								label: "Clicks",
								data: stats.referrers.map((r) => r.count),
								backgroundColor: "#6366F1",
								borderRadius: 6,
							},
						],
					},
					options: {
						responsive: true,
						plugins: { legend: { display: false } },
						scales: {
							x: {
								grid: { display: false },
								ticks: { color: dark.ticks },
							},
							y: {
								grid: { color: dark.grid },
								ticks: { color: dark.ticks },
								beginAtZero: true,
							},
						},
					},
				}),
			);
		}

		const browserCanvas = document.getElementById(
			"browser-chart",
		) as HTMLCanvasElement;
		if (browserCanvas) {
			charts.push(
				new Chart(browserCanvas, {
					type: "bar",
					data: {
						labels: stats.browsers.map((b) => b.name),
						datasets: [
							{
								label: "Clicks",
								data: stats.browsers.map((b) => b.count),
								backgroundColor: "#8B5CF6",
								borderRadius: 6,
							},
						],
					},
					options: {
						indexAxis: "y",
						responsive: true,
						plugins: { legend: { display: false } },
						scales: {
							x: {
								grid: { color: dark.grid },
								ticks: { color: dark.ticks },
								beginAtZero: true,
							},
							y: {
								grid: { display: false },
								ticks: { color: dark.ticks },
							},
						},
					},
				}),
			);
		}
	}

	async function copyShort() {
		if (!link) return;
		await navigator.clipboard.writeText(link.shortUrl);
		copied = true;
		setTimeout(() => (copied = false), 1500);
	}

	function shareUrl(): string {
		return link ? encodeURIComponent(link.shortUrl) : "";
	}

	onMount(() => {
		const code = window.location.pathname.split("/").pop() || "";
		load(code);
		return () => charts.forEach((c) => c.destroy());
	});
</script>

<svelte:head>
	<title>{link ? "/" + link.code : "Link"} - URLShortener</title>
</svelte:head>

<div class="page">
	<a href="/links" class="text-secondary back">← My Links</a>

	{#if error}
		<p class="error-text">{error}</p>
	{/if}

	{#if link && stats}
		<div class="card header-card">
			<div class="header-row">
				<div>
					<h1 class="mono">/{link.code}</h1>
					<p class="text-secondary dest" title={link.originalUrl}>
						{link.originalUrl}
					</p>
				</div>
				<div class="share">
					<button class="btn btn-ghost btn-sm" onclick={copyShort}
						>{copied ? "Copied!" : "Copy"}</button
					>
					<a
						class="btn btn-ghost btn-sm"
						href="https://twitter.com/intent/tweet?url={shareUrl()}"
						target="_blank"
						rel="noopener noreferrer">X</a
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
						onclick={() => (showQr = !showQr)}>QR</button
					>
				</div>
			</div>
			{#if showQr}
				<div class="qr-box">
					<img
						src="/api/v1/links/{link.code}/qr"
						alt="QR code for /{link.code}"
						width="220"
						height="220"
					/>
				</div>
			{/if}
		</div>

		<div class="stat-row">
			<div class="card stat">
				<p class="text-secondary">Total clicks</p>
				<p class="big">{stats.totalClicks}</p>
			</div>
			<div class="card stat">
				<p class="text-secondary">Unique visitors</p>
				<p class="big grad">{stats.uniqueVisitors}</p>
			</div>
			<div class="card stat">
				<p class="text-secondary">Created</p>
				<p class="big">
					{new Date(link.createdAt).toLocaleDateString()}
				</p>
			</div>
			<div class="card stat">
				<p class="text-secondary">Expires</p>
				<p class="big">
					{link.expiresAt
						? new Date(link.expiresAt).toLocaleDateString()
						: "Never"}
				</p>
			</div>
		</div>

		<div class="card chart-card">
			<h2>Clicks over time</h2>
			<canvas id="timeline-chart"></canvas>
		</div>

		<div class="grid-2">
			<div class="card chart-card">
				<h2>Devices</h2>
				<canvas id="device-chart"></canvas>
			</div>
			<div class="card chart-card">
				<h2>Browsers</h2>
				<canvas id="browser-chart"></canvas>
			</div>
		</div>

		<div class="card chart-card">
			<h2>Top referrers</h2>
			<canvas id="referrer-chart"></canvas>
		</div>
	{/if}
</div>

<style>
	.page {
		padding: 2rem 0 4rem;
		max-width: 900px;
		margin: 0 auto;
	}
	.back {
		display: inline-block;
		text-decoration: none;
		margin-bottom: 1rem;
	}
	.header-card {
		margin-bottom: 1.25rem;
	}
	.header-row {
		display: flex;
		justify-content: space-between;
		align-items: flex-start;
		gap: 1rem;
		flex-wrap: wrap;
	}
	h1 {
		margin: 0 0 0.25rem;
		font-size: 1.6rem;
	}
	h2 {
		margin: 0 0 1rem;
		font-size: 1.05rem;
	}
	.dest {
		margin: 0;
		overflow-wrap: anywhere;
		max-width: 480px;
	}
	.share {
		display: flex;
		gap: 0.5rem;
		flex-wrap: wrap;
	}
	.qr-box {
		margin-top: 1rem;
		text-align: center;
	}
	.stat-row {
		display: grid;
		grid-template-columns: repeat(4, 1fr);
		gap: 1rem;
		margin-bottom: 1.25rem;
	}
	.stat .big {
		font-size: 1.6rem;
		font-weight: 700;
		margin: 0.25rem 0 0;
	}
	.grad {
		background: var(--gradient);
		-webkit-background-clip: text;
		background-clip: text;
		-webkit-text-fill-color: transparent;
	}
	.chart-card {
		margin-bottom: 1.25rem;
	}
	.grid-2 {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 1.25rem;
	}
	@media (max-width: 768px) {
		.stat-row {
			grid-template-columns: 1fr 1fr;
		}
		.grid-2 {
			grid-template-columns: 1fr;
		}
	}
</style>
