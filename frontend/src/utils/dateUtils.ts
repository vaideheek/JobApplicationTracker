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
