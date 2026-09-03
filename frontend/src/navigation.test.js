import { describe, expect, it } from 'vitest';
import { getRoute } from './navigation.js';

describe('navigation', () => {
  it('maps supported hash routes and falls back to review', () => {
    expect(getRoute('#review')).toBe('review');
    expect(getRoute('#projects')).toBe('projects');
    expect(getRoute('#history')).toBe('history');
    expect(getRoute('#settings')).toBe('review');
    expect(getRoute('')).toBe('review');
  });
});
