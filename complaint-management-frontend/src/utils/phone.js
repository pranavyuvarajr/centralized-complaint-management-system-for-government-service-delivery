// Mobile-number rule, mirrored from the backend (PhoneUtil.java): 10 digits starting
// with 6-9; a +91 / 91 / 0 prefix and spaces, dashes, dots or brackets are accepted.
// If you change the rule here, change it there too (the server is the authority).

export const PHONE_ERROR = 'Enter a valid 10-digit mobile number (it can start with +91).';

/** Returns the normalized 10-digit number, or null if the input isn't a valid mobile number. */
export function normalizePhone(raw) {
  if (raw == null) return null;
  let s = String(raw).trim().replace(/[\s\-().]/g, '');
  if (s.startsWith('+91')) s = s.slice(3);
  else if (s.startsWith('91') && s.length === 12) s = s.slice(2);
  else if (s.startsWith('0') && s.length === 11) s = s.slice(1);
  return /^[6-9]\d{9}$/.test(s) ? s : null;
}

/** '' when valid, otherwise a message suitable for showing next to the field. */
export function phoneError(raw) {
  if (raw == null || String(raw).trim() === '') return 'A mobile number is required.';
  return normalizePhone(raw) ? '' : PHONE_ERROR;
}
