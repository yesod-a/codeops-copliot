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
      '/api/ai/health', '/api/reviews?limit=20&offset=0'
    ]);
    expect(getItem).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('数据库评审');
  });
});
