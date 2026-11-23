import { createTheme } from '@mantine/core';

/** The app's display name and look. Change the name here only. */
export const BRAND = {
  name: 'Bluefin Exchange',
  short: 'BFX',
  tagline: 'Trade fictional stocks with play money',
  gradient: { from: 'indigo.8', to: 'cyan.6', deg: 120 },
};

export const theme = createTheme({
  primaryColor: 'indigo',
  defaultRadius: 'md',
  fontFamily: 'Inter, "Segoe UI", system-ui, -apple-system, sans-serif',
  headings: { fontWeight: '700' },
  components: {
    Paper: { defaultProps: { shadow: 'xs' } },
    Table: { defaultProps: { highlightOnHover: true, verticalSpacing: 'xs' } },
    Button: { defaultProps: { radius: 'md' } },
  },
});
