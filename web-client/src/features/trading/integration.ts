/**
 * The seams between this feature and the rest of the web app. Nothing else in
 * features/trading imports from outside the folder, so a renamed hook on Cezar's or Cristi's
 * side is a one-line fix here.
 */
export { useAuth } from '@/app/auth';
export { useStomp } from '@/app/stomp';
export { PriceChart } from '@/features/insights';

/** Base URL of S1. Through nginx in docker compose; override for local dev with VITE_S1_URL. */
export const S1_BASE_URL: string =
  (import.meta.env?.VITE_S1_URL as string | undefined)?.replace(/\/$/, '') ?? '/api/s1';
