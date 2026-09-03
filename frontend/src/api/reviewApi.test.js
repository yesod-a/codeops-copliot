import { afterEach, describe, expect, it, vi } from 'vitest';
import { deleteReview, getAiHealth, getReviewDetails, listReviews, saveReview, scanRepository, submitAiReview } from './reviewApi.js';

describe('local Git review API', () => {
  afterEach(() => vi.restoreAllMocks());

  it('sends scanned file contents directly to the LLM review endpoint', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ files: [] }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'task-1' }), { status: 202 }));

    await scanRepository({ repositoryPath: 'C:/repo', scope: 'WORKTREE', baseRef: null });
    await submitAiReview({
      repositoryPath: 'C:/repo', scope: 'WORKTREE', baseRef: null,
      title: 'Review local changes', files: [{ path: 'src/App.java', content: 'diff --git a/src/App.java', gitStatus: 'MODIFIED', additions: 2, deletions: 1 }]
    });

    expect(fetchMock.mock.calls[0][0]).toBe('/api/repositories/scan');
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({
      repositoryPath: 'C:/repo', scope: 'WORKTREE', baseRef: null
    });
    expect(fetchMock.mock.calls[1][0]).toBe('/api/ai/review');
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({
      repository: 'C:/repo',
      title: 'Review local changes',
      files: [{ path: 'src/App.java', content: 'diff --git a/src/App.java' }]
    });
  });

  it('checks the LLM backend through the ai proxy route', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: 'ready', model: 'gpt-4o-mini' }), { status: 200 }));

    const health = await getAiHealth();

    expect(fetchMock.mock.calls[0][0]).toBe('/api/ai/health');
    expect(health).toEqual({ status: 'ready', model: 'gpt-4o-mini' });
  });

  it('allows the LLM request to run longer than the short API timeout', async () => {
    vi.useFakeTimers();
    let resolveFetch;
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((_url, options) => new Promise((resolve, reject) => {
      resolveFetch = resolve;
      options.signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')));
    }));

    const resultPromise = submitAiReview({
      repositoryPath: 'C:/repo',
      title: 'Slow LLM review',
      files: [{ path: 'src/App.java', content: 'class App {}' }]
    });
    await vi.advanceTimersByTimeAsync(3000);
    resolveFetch(new Response(JSON.stringify({ findings: [] }), { status: 200 }));

    await expect(resultPromise).resolves.toEqual({ findings: [] });
    expect(fetchMock).toHaveBeenCalledOnce();
    vi.useRealTimers();
  });

  it('persists, lists, loads, and deletes database-backed review history', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'review-1' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify([{ id: 'review-1' }]), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'review-1', findings: [] }), { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));

    await saveReview({ requestId: 'request-1', title: 'Review', files: [{ path: 'src/App.java' }], findings: [] });
    await listReviews({ limit: 20, offset: 0 });
    await getReviewDetails('review-1');
    await deleteReview('review-1');

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      '/api/reviews', '/api/reviews?limit=20&offset=0', '/api/reviews/review-1', '/api/reviews/review-1'
    ]);
    expect(fetchMock.mock.calls[0][1].method).toBe('POST');
  });
});
