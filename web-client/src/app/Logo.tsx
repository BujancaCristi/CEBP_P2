import { BRAND } from './brand';

/** Small logo mark: a rising line in a rounded square. */
export function Logo({ size = 34 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 64 64" aria-label={BRAND.name} role="img">
      <rect width="64" height="64" rx="14" fill="white" fillOpacity="0.18" />
      <path d="M14 44 L28 28 L36 36 L50 20" stroke="white" strokeWidth="6" fill="none" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
