const price = new Intl.NumberFormat(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 4 });
const money = new Intl.NumberFormat(undefined, { style: 'currency', currency: 'EUR', minimumFractionDigits: 2 });
const integer = new Intl.NumberFormat(undefined, { maximumFractionDigits: 0 });

export const fmt = {
  price: (v: number | null | undefined) => (v == null ? '—' : price.format(v)),
  money: (v: number | null | undefined) => (v == null ? '—' : money.format(v)),
  qty: (v: number | null | undefined) => (v == null ? '—' : integer.format(v)),
  time: (iso: string) => new Date(iso).toLocaleTimeString(),
};
