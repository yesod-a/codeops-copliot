import { describe, expect, it } from 'vitest';
import { getHistoryId, getRoute, getTaskId } from './navigation.js';

describe('navigation', () => {
  it('maps supported hash routes and falls back to review', () => {
    expect(getRoute('#review')).toBe('review');
    expect(getRoute('#projects')).toBe('projects');
    expect(getRoute('#rules')).toBe('rules');
    expect(getRoute('#users')).toBe('users');
    expect(getRoute('#history')).toBe('history');
    expect(getRoute('#tasks/task-1')).toBe('task-detail');
    expect(getRoute('#settings')).toBe('review');
    expect(getRoute('')).toBe('review');
  });

  it('recognizes history detail routes', () => {
    expect(getRoute('#history/review-1')).toBe('history-detail');
    expect(getRoute('#history/')).toBe('history');
    expect(getHistoryId('#history/review-1')).toBe('review-1');
  });

  it('recognizes task detail routes', () => {
    expect(getTaskId('#tasks/task-1')).toBe('task-1');
    expect(getTaskId('#tasks/')).toBeNull();
  });
});
