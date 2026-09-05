import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from './App.vue';

describe('App history persistence', () => {
  let mountedWrappers = [];

  function mountApp(options) {
    const wrapper = mount(App, options);
    mountedWrappers.push(wrapper);
    return wrapper;
  }

  afterEach(() => {
    mountedWrappers.forEach((wrapper) => wrapper.unmount());
    mountedWrappers = [];
    window.location.hash = '#review';
    vi.restoreAllMocks();
  });

  it('shows the login screen when the session is missing', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(new Response('{}', { status: 401 }));
    const wrapper = mountApp({ global: { stubs: { ReviewForm: true, ReviewStatus: true, FindingList: true } } });
    await flushPromises();
    expect(wrapper.text()).toContain('登录 CodeOps');
    expect(wrapper.find('.app-shell').exists()).toBe(false);
  });

  it('redirects a normal user away from the user-management route', async () => {
    window.location.hash = '#users';
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) => {
      if (url === '/api/auth/me') return new Response(JSON.stringify({ id: 'u1', username: 'alice', displayName: 'Alice', role: 'USER' }), { status: 200 });
      if (url === '/api/ai/health') return new Response(JSON.stringify({ status: 'ready', model: 'gpt-4o-mini' }), { status: 200 });
      if (url === '/api/projects') return new Response(JSON.stringify([]), { status: 200 });
      if (url === '/api/reviews?limit=20&offset=0') return new Response(JSON.stringify([]), { status: 200 });
      throw new Error(`Unexpected URL: ${url}`);
    });

    const wrapper = mountApp({
      global: { stubs: { ReviewForm: true, ReviewStatus: true, FindingList: true } }
    });
    await flushPromises();

    expect(window.location.hash).toBe('#review');
    expect(wrapper.text()).toContain('评审工作台');
    expect(wrapper.text()).not.toContain('用户管理');
  });

  it('shows the user-management route to an administrator', async () => {
    window.location.hash = '#users';
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) => {
      if (url === '/api/auth/me') return new Response(JSON.stringify({ id: 'admin-1', username: 'admin', displayName: 'Admin', role: 'ADMIN' }), { status: 200 });
      if (url === '/api/ai/health') return new Response(JSON.stringify({ status: 'ready', model: 'gpt-4o-mini' }), { status: 200 });
      if (url === '/api/projects') return new Response(JSON.stringify([]), { status: 200 });
      if (url === '/api/reviews?limit=20&offset=0') return new Response(JSON.stringify([]), { status: 200 });
      throw new Error(`Unexpected URL: ${url}`);
    });

    const wrapper = mountApp({
      global: {
        stubs: {
          ReviewForm: true,
          ReviewStatus: true,
          FindingList: true,
          UserManagement: { template: '<div data-test="user-management-page">user management page</div>' }
        }
      }
    });
    await flushPromises();

    expect(window.location.hash).toBe('#users');
    expect(wrapper.text()).toContain('用户管理');
    expect(wrapper.find('[data-test="user-management-page"]').exists()).toBe(true);
  });

  it('loads review history from the database API without reading browser storage', async () => {
    window.location.hash = '#history';
    const getItem = vi.spyOn(Storage.prototype, 'getItem');
    const historyResponse = new Response(JSON.stringify([{
        id: 'review-1', title: '数据库评审', repository: 'C:/repo', sourceType: 'GIT',
        status: 'COMPLETED', findingCount: 0, riskScore: 100, createdAt: '2026-09-03T08:00:00Z'
      }]), { status: 200 });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) => {
      if (url === '/api/auth/me') return new Response(JSON.stringify({ id: 'u1', username: 'alice', displayName: 'Alice', role: 'USER' }), { status: 200 });
      if (url === '/api/ai/health') return new Response(JSON.stringify({ status: 'ready', model: 'gpt-4o-mini' }), { status: 200 });
      if (url === '/api/projects') return new Response(JSON.stringify([]), { status: 200 });
      if (url === '/api/reviews?limit=20&offset=0') return historyResponse.clone();
      throw new Error(`Unexpected URL: ${url}`);
    });

    const wrapper = mountApp({
      global: {
        stubs: { ReviewForm: true, ReviewStatus: true, FindingList: true }
      }
    });
    await flushPromises();

    expect(fetchMock.mock.calls.map(([url]) => url).slice(0, 4)).toEqual([
      '/api/auth/me', '/api/ai/health', '/api/projects', '/api/reviews?limit=20&offset=0'
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
      if (url === '/api/auth/me') return new Response(JSON.stringify({ id: 'u1', username: 'alice', displayName: 'Alice', role: 'USER' }), { status: 200 });
      if (url === '/api/ai/health') return new Response(JSON.stringify({ status: 'ready', model: 'gpt-4o-mini' }), { status: 200 });
      if (url === '/api/projects') return new Response(JSON.stringify([]), { status: 200 });
      if (url === '/api/reviews?limit=20&offset=0') return new Response(JSON.stringify([historyItem]), { status: 200 });
      if (url === '/api/reviews/review-1') return new Response(JSON.stringify(detail), { status: 200 });
      throw new Error(`Unexpected URL: ${url}`);
    });

    const wrapper = mountApp({
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
