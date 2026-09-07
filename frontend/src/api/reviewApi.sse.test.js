import { afterEach, describe, expect, it, vi } from 'vitest';
import { createReviewTaskEventSource } from './reviewApi.js';

describe('review task SSE API', () => {
  afterEach(() => vi.restoreAllMocks());

  it('opens a credentialed event stream for a task', () => {
    const source = { close: vi.fn() };
    const eventSource = vi.fn(function EventSource() { return source; });
    vi.stubGlobal('EventSource', eventSource);

    expect(createReviewTaskEventSource('task/1')).toBe(source);
    expect(eventSource).toHaveBeenCalledWith('/api/review-tasks/task%2F1/events', { withCredentials: true });
  });
});
