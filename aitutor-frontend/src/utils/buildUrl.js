/**
 * Build an absolute URL for public assets regardless of base path.
 * Accepts paths with or without leading slash.
 */
export function buildUrl(path) {
  if (!path) return '';
  const normalized = path.startsWith('/') ? path : `/${path}`;
  const base = import.meta.env.BASE_URL || '/';
  const baseNormalized = base.endsWith('/') ? base.slice(0, -1) : base;
  return `${baseNormalized}${normalized}`;
}


