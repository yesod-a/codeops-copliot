import { describe, expect, it } from 'vitest';
import { getHistoryId, getRoute } from './navigation.js';

describe('navigation', () => {
  it('maps supported hash routes and falls back to review', () => {
    expect(getRoute('#review')).toBe('review');
    expect(getRoute('#projects')).toBe('projects');
    expect(getRoute('#history')).toBe('history');
    expect(getRoute('#settings')).toBe('review');
    expect(getRoute('')).toBe('review');
  });

  it('recognizes history detail routes', () => {
    expect(getRoute('#history/review-1')).toBe('history-detail');
    expect(getRoute('#history/')).toBe('history');
    expect(getHistoryId('#history/review-1')).toBe('review-1');
  });
});
