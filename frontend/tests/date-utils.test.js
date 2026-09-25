import assert from 'node:assert';
import { test } from 'node:test';
import { parseUtcDateTime, formatDateSafe, formatUtcDateTime } from '../src/utils/dateUtils.ts';

test('parseUtcDateTime treats timezone-less ISO timestamp as UTC', () => {
  const utcStr = '2026-09-24T21:11:42';
  const parsed = parseUtcDateTime(utcStr);

  assert(parsed instanceof Date, 'Should return a Date object');
  assert.strictEqual(parsed.toISOString(), '2026-09-24T21:11:42.000Z');
  assert.strictEqual(parsed.getUTCFullYear(), 2026);
  assert.strictEqual(parsed.getUTCMonth(), 8); // September is month 8 (0-indexed)
  assert.strictEqual(parsed.getUTCDate(), 24);
  assert.strictEqual(parsed.getUTCHours(), 21);
  assert.strictEqual(parsed.getUTCMinutes(), 11);
  assert.strictEqual(parsed.getUTCSeconds(), 42);
});

test('parseUtcDateTime preserves timestamps that already specify UTC or an offset', () => {
  const withZ = parseUtcDateTime('2026-09-24T21:11:42Z');
  assert.strictEqual(withZ?.toISOString(), '2026-09-24T21:11:42.000Z');

  const withOffset = parseUtcDateTime('2026-09-24T21:11:42+00:00');
  assert.strictEqual(withOffset?.toISOString(), '2026-09-24T21:11:42.000Z');
});

test('parseUtcDateTime handles null, undefined, and empty string', () => {
  assert.strictEqual(parseUtcDateTime(null), null);
  assert.strictEqual(parseUtcDateTime(undefined), null);
  assert.strictEqual(parseUtcDateTime(''), null);
});

test('formatDateSafe formats date-only string without timezone shifting', () => {
  assert.strictEqual(formatDateSafe('2026-09-10'), 'Sep 10, 2026');
  assert.strictEqual(formatDateSafe('2026-01-01'), 'Jan 1, 2026');
  assert.strictEqual(formatDateSafe('2026-12-31'), 'Dec 31, 2026');
  assert.strictEqual(formatDateSafe(null), '—');
  assert.strictEqual(formatDateSafe(undefined), '—');
});

test('formatUtcDateTime formats valid timestamp and handles missing input', () => {
  const result = formatUtcDateTime('2026-09-24T21:11:42', 'yyyy-MM-dd');
  // Formatted date string should not be empty
  assert(result.length > 0 && result !== '—');
  assert.strictEqual(formatUtcDateTime(null), '—');
  assert.strictEqual(formatUtcDateTime(undefined), '—');
});
