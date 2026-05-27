import createClient, { type Middleware } from 'openapi-fetch';
import type { paths } from './schema';

const baseUrl = import.meta.env.VITE_API_BASE_URL ?? '';

/** Read a cookie value by name. Returns null when not set. */
export function readCookie(name: string): string | null {
  const escapedName = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const match = document.cookie.match(new RegExp('(?:^|; )' + escapedName + '=([^;]*)'));
  return match ? decodeURIComponent(match[1]) : null;
}

const MUTATING_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

/** Add X-XSRF-TOKEN header (Spring CSRF) for mutating requests, value from XSRF-TOKEN cookie. */
const csrfMiddleware: Middleware = {
  async onRequest({ request }) {
    if (MUTATING_METHODS.has(request.method.toUpperCase())) {
      const token = readCookie('XSRF-TOKEN');
      if (token) {
        request.headers.set('X-XSRF-TOKEN', token);
      }
    }
    return request;
  },
};

/** Redirect to /login on 401, except when already on /login or when calling /api/auth/login. */
const unauthorizedMiddleware: Middleware = {
  async onResponse({ response, request }) {
    if (response.status !== 401) return response;
    const url = new URL(request.url);
    if (url.pathname.startsWith('/api/auth/')) return response;
    if (window.location.pathname.startsWith('/login')) return response;
    window.location.href = '/login';
    return response;
  },
};

export const api = createClient<paths>({
  baseUrl,
  credentials: 'same-origin',
});
api.use(csrfMiddleware);
api.use(unauthorizedMiddleware);

export type ApiPaths = paths;
