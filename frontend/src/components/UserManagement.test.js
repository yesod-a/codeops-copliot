import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import UserManagement from './UserManagement.vue';

describe('UserManagement', () => {
  afterEach(() => {
    document.body.innerHTML = '';
    vi.restoreAllMocks();
  });

  it('loads users and displays a newly issued device token only after creation', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (url, options = {}) => {
      if (url === '/api/management/users') return new Response(JSON.stringify([{
        id: 'user-1', username: 'alice', displayName: 'Alice', role: 'USER', active: true,
        createdAt: '2026-09-05T10:00:00', agents: [], memberships: []
      }]), { status: 200 });
      if (url === '/api/management/users/user-1/agents' && options.method === 'POST') {
        return new Response(JSON.stringify({ agentId: 'agent-1', name: 'alice-laptop', token: 'cop_one_time_token' }), { status: 201 });
      }
      throw new Error(`Unexpected request: ${url}`);
    });

    const host = document.createElement('div');
    document.body.appendChild(host);
    const wrapper = mount(UserManagement, { attachTo: host, props: { projects: [] } });
    await flushPromises();

    expect(wrapper.text()).toContain('Alice');
    expect(wrapper.text()).not.toContain('cop_one_time_token');
    await wrapper.get('[data-test="new-agent-user-1"]').trigger('click');
    const agentName = document.body.querySelector('[data-test="agent-name"]');
    const createAgent = document.body.querySelector('[data-test="create-agent"]');
    expect(agentName).not.toBeNull();
    expect(createAgent).not.toBeNull();
    agentName.value = 'alice-laptop';
    agentName.dispatchEvent(new Event('input'));
    await wrapper.vm.$nextTick();
    createAgent.click();
    await flushPromises();

    expect(document.body.querySelector('[data-test="issued-token"]')?.textContent).toContain('cop_one_time_token');
    expect(document.body.textContent).toContain('Token 只显示一次');
  });

  it('uses the shared select control for creation, editing, and project access', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify([{
      id: 'user-1', username: 'alice', displayName: 'Alice', role: 'USER', active: true,
      createdAt: '2026-09-05T10:00:00', agents: [], memberships: []
    }]), { status: 200 }));

    const wrapper = mount(UserManagement, {
      props: { projects: [{ id: 3, name: 'store' }] }
    });
    await flushPromises();

    expect(wrapper.findAll('select')).toHaveLength(4);
    expect(wrapper.findAll('select').every((select) => select.classes('app-select'))).toBe(true);
  });
});
