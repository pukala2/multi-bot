import createClient from 'openapi-fetch';
import type { paths } from './schema';

const baseUrl = import.meta.env.VITE_API_BASE_URL ?? '';

export const api = createClient<paths>({
  baseUrl,
});

export type ApiPaths = paths;
