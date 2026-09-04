import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from './App.vue';

describe('App history persistence', () => {
  afterEach(() => {
    window.location.hash = '#review';
    vi.restoreAllMocks();
  });

  it('loads review history from the database API without reading browser storage', async () => {
    window.location.hash = '#history';
    const getItem = vi.spyOn(Storage.prototype, 'getItem');
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: 'ready', model: 'gpt-4o-mini' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify([]), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify([{
        id: 'review-1', title: '数据库评审', repository: 'C:/repo', sourceType: 'GIT',
        status: 'COMPLETED', findingCount: 0, riskScore: 100, createdAt: '2026-09-03T08:00:00Z'
      }]), { status: 200 }));

    const wrapper = mount(App, {
      global: {
        stubs: { ReviewForm: true, ReviewStatus: true, FindingList: true }
      }
    });
    await flushPromises();

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      '/api/ai/health', '/api/projects', '/api/reviews?limit=20&offset=0'
    ]);
    expect(getItem).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('数据库评审');
  });

  it('opens a history item in its own detail route instead of the review workspace', async () => {
    window.location.hash = '#history';
    const historyItem = {
      id: 'review-1', title: '数据库评审', repository: 'C:/repo', sourceType: 'GIT',
      status: 'COMPLETED', findingCount: 1, riskScore: 85, createdAt: '2026-09-03T08:00:00Z'
    };
    const detail = {
      id: 'review-1', title: '数据库评审详情', repository: 'C:/repo', sourceType: 'GIT',
      scope: 'BASE_COMMIT', baseRef: 'main', branch: 'feature/review', headCommit: 'abcdef123456',
      status: 'COMPLETED', modelName: 'gpt-4o-mini', riskScore: 85, findingCount: 1,
      createdAt: '2026-09-03T08:00:00Z', completedAt: '2026-09-03T08:01:00Z', findings: [], files: []
    };
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) => {
      if (url === '/api/ai/health') return new Response(JSON.stringify({ status: 'ready', model: 'gpt-4o-mini' }), { status: 200 });
      if (url === '/api/projects') return new Response(JSON.stringify([]), { status: 200 });
      if (url === '/api/reviews?limit=20&offset=0') return new Response(JSON.stringify([historyItem]), { status: 200 });
      if (url === '/api/reviews/review-1') return new Response(JSON.stringify(detail), { status: 200 });
      throw new Error(`Unexpected URL: ${url}`);
    });

    const wrapper = mount(App, {
      global: {
        stubs: { ReviewForm: true, ReviewStatus: true, FindingList: true }
      }
    });
    await flushPromises();

    await wrapper.get('.history-item').trigger('click');
    await flushPromises();

    expect(window.location.hash).toBe('#history/review-1');
    expect(wrapper.text()).toContain('数据库评审详情');
    expect(wrapper.text()).toContain('评审详情');
    expect(wrapper.findComponent({ name: 'ReviewForm' }).exists()).toBe(false);
    expect(fetchMock.mock.calls.at(-1)[0]).toBe('/api/reviews/review-1');
  });
});
