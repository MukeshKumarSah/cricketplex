export function compareStats(aVal, bVal) {
  const parse = (v) => {
    if (typeof v === 'number') return { t: 'n', n: v, x: 0 };
    if (v == null || v === '') return { t: 'n', n: 0, x: 0 };
    const s = String(v).trim();
    const bb = s.match(/^(\d+)\s*\/\s*(\d+)/);
    if (bb) return { t: 'bb', n: Number(bb[1]), x: Number(bb[2]) };
    const hs = s.match(/^(-?\d+(?:\.\d+)?)\*?$/);
    if (hs) return { t: 'n', n: Number(hs[1]), x: s.endsWith('*') ? 1 : 0 };
    return { t: 's', s };
  };
  const a = parse(aVal);
  const b = parse(bVal);
  if (a.t === 's' || b.t === 's') {
    return String(aVal ?? '').localeCompare(String(bVal ?? ''));
  }
  if (a.t === 'bb' && b.t === 'bb') {
    if (a.n !== b.n) return a.n - b.n;
    return b.x - a.x;
  }
  if (a.n !== b.n) return a.n - b.n;
  return a.x - b.x;
}
