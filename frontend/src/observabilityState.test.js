import { describe, expect, it } from 'vitest';
import { formatCost, formatDuration, formatTokens, percentage } from './observabilityState.js';

describe('observability formatting', () => {
  it('formats ratios, durations, tokens and costs', () => {
    expect(percentage(3, 4)).toBe('75%');
    expect(formatDuration(1250)).toBe('1.3 s');
    expect(formatTokens(1200)).toContain('1,200');
    expect(formatCost(0.12)).toBe('$0.1200');
  });
  it('handles empty metrics', () => {
    expect(percentage(0, 0)).toBe('0%');
    expect(formatDuration(0)).toBe('0 ms');
    expect(formatCost(null)).toBe('-');
  });
});
