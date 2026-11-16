/**
 * The seams between features/insights and the rest of the web app (owner of the targets: Cezar).
 * Nothing else in this folder imports from outside it.
 */
export { useAuth } from '@/app/auth';

/** Base URL of S2. Through nginx in docker compose; override for local dev with VITE_S2_URL. */
export const S2_BASE_URL: string =
  (import.meta.env?.VITE_S2_URL as string | undefined)?.replace(/\/$/, '') ?? '/api/s2';
