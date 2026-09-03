import { afterEach, describe, expect, it, vi } from 'vitest';
import { scanRepository, submitGitReview } from './reviewApi.js';

describe('local Git review API', () => {
  afterEach(() => vi.restoreAllMocks());

  it('sends scan and selected paths to the local Git endpoints', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ files: [] }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'task-1' }), { status: 202 }));

    await scanRepository({ repositoryPath: 'C:/repo', scope: 'WORKTREE', baseRef: null });
    await submitGitReview({
      repositoryPath: 'C:/repo', scope: 'WORKTREE', baseRef: null,
      title: 'Review local changes', files: ['src/App.java']
    });

    expect(fetchMock.mock.calls[0][0]).toBe('/api/repositories/scan');
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({
      repositoryPath: 'C:/repo', scope: 'WORKTREE', baseRef: null
    });
    expect(fetchMock.mock.calls[1][0]).toBe('/api/reviews/from-git');
    expect(JSON.parse(fetchMock.mock.calls[1][1].body).files).toEqual(['src/App.java']);
  });
});
