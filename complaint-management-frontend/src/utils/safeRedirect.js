// Where to send a user after login when they were bounced from a protected page (e.g. an email link).
// Only a plain internal path is ever accepted: it must start with a single "/", never "//", a backslash,
// or a URL scheme, and role-prefixed pages must match the user's role. Anything else returns null so the
// caller falls back to the role dashboard.

const ROLE_PREFIX = { citizen: 'CITIZEN', official: 'OFFICIAL', admin: 'ADMIN' };

export function safeRedirectPath(from, role) {
  if (!from) return null;
  const path = typeof from === 'string'
    ? from
    : `${from.pathname || ''}${from.search || ''}${from.hash || ''}`;

  if (typeof path !== 'string' || !path.startsWith('/') || path.startsWith('//')) return null;
  if (path.includes('\\') || /[\u0000-\u001f]/.test(path) || /^\/[a-z][a-z0-9+.-]*:/i.test(path)) return null;

  const first = path.split(/[/?#]/)[1] || '';
  if (first === 'login' || first === 'register') return null;
  const requiredRole = ROLE_PREFIX[first];
  if (requiredRole && requiredRole !== role) return null;
  return path;
}
