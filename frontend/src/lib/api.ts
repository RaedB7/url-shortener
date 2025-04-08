export class ApiError extends Error {
	status: number;
	title?: string;

	constructor(status: number, message: string, title?: string) {
		super(message);
		this.status = status;
		this.title = title;
	}
}

async function parse<T>(res: Response): Promise<T> {
	if (res.status === 204) return undefined as T;
	const body = await res.json().catch(() => null);
	if (!res.ok) {
		const detail = body?.detail || body?.message || `Request failed (${res.status})`;
		throw new ApiError(res.status, detail, body?.title);
	}
	return body as T;
}

export async function api<T>(path: string, init?: RequestInit): Promise<T> {
	const res = await fetch(`/api/v1${path}`, {
		...init,
		credentials: 'include',
		headers: { 'Content-Type': 'application/json', ...init?.headers }
	});
	return parse<T>(res);
}

export interface UrlResponse {
	code: string;
	originalUrl: string;
	shortUrl: string;
	createdAt: string;
	expiresAt: string | null;
	passwordProtected: boolean;
}

export interface PageResult<T> {
	content: T[];
	page: number;
	size: number;
	totalElements: number;
	totalPages: number;
}

export interface Me {
	email: string;
	role: 'USER' | 'ADMIN';
}
