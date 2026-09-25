import { format } from 'date-fns';

/**
 * Formats an ISO date string (YYYY-MM-DD) into a human-readable date (e.g. 'Aug 1, 2026')
 * without local-time drift or UTC-midnight timezone shifting.
 */
export function formatDateSafe(dateStr: string | null | undefined): string {
  if (!dateStr) return '—';
  const match = dateStr.match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (!match) return dateStr;
  const year = match[1];
  const monthIdx = parseInt(match[2], 10) - 1;
  const day = parseInt(match[3], 10);
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  return `${months[monthIdx]} ${day}, ${year}`;
}

/**
 * Parses an ISO timestamp string representing UTC time into a JavaScript Date object.
 * If the string does not have a timezone designator (like 'Z' or '+00:00'),
 * it appends 'Z' so that the Date object treats it as UTC rather than local time.
 */
export function parseUtcDateTime(dateStr: string | null | undefined): Date | null {
  if (!dateStr) return null;
  const normalized = dateStr.endsWith('Z') || /[+-]\d{2}(:\d{2})?$/.test(dateStr)
    ? dateStr
    : `${dateStr}Z`;
  const d = new Date(normalized);
  return isNaN(d.getTime()) ? null : d;
}

/**
 * Formats a UTC ISO timestamp string into a local human-readable string (e.g. 'Sep 24, 2026 · 10:15 PM')
 * in the user's browser timezone.
 */
export function formatUtcDateTime(
  dateStr: string | null | undefined,
  formatPattern = 'MMM d, yyyy · h:mm a'
): string {
  const date = parseUtcDateTime(dateStr);
  if (!date) return '—';
  return format(date, formatPattern);
}
