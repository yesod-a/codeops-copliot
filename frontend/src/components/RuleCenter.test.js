import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import RuleCenter from './RuleCenter.vue';

describe('RuleCenter', () => {
  afterEach(() => vi.restoreAllMocks());

  it('loads global rules and previews effective rules for a selected project path', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) => {
      if (url === '/api/rules/global') return new Response(JSON.stringify([{
        id: 1, scope: 'GLOBAL', name: '事务完整性', category: 'CORRECTNESS', pathPattern: '**/*.java',
        content: '检查事务边界', priority: 100, enabled: true, version: 1
      }]), { status: 200 });
      if (url === '/api/projects/3/rules') return new Response(JSON.stringify([]), { status: 200 });
      if (url === '/api/projects/3/rules/preview') return new Response(JSON.stringify({ files: [{
        path: 'backend/src/App.java', effectiveRuleHash: 'abc123', rules: [{ source: 'BUILTIN', name: 'Java' }]
      }] }), { status: 200 });
      throw new Error(`Unexpected URL: ${url}`);
    });

    const wrapper = mount(RuleCenter, { props: { projects: [{ id: 3, name: 'store' }] } });
    await flushPromises();

    await wrapper.get('.rule-actions .secondary-button').trigger('click');
    expect(document.body.textContent).toContain('事务完整性');
    expect(document.body.textContent).toContain('优先级 100');
    expect(document.body.textContent).toContain('v1');
    expect(document.body.querySelector('[title="编辑规则"]')).not.toBeNull();
    expect(document.body.querySelector('[title="删除规则"]')).not.toBeNull();
    await wrapper.get('.rule-tabs button:nth-child(3)').trigger('click');
    await flushPromises();
    await wrapper.get('[data-test="rule-project-select"]').setValue('3');
    await flushPromises();
    await wrapper.get('[data-test="preview-paths"]').setValue('backend/src/App.java');
    await wrapper.get('[data-test="preview-rules"]').trigger('click');
    await flushPromises();

    expect(fetchMock.mock.calls.map(([url]) => url)).toContain('/api/projects/3/rules/preview');
    expect(wrapper.text()).toContain('abc123');
  });

  it('shows enabled global rules as read-only inherited rules for the selected project', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) => {
      if (url === '/api/rules/global') return new Response(JSON.stringify([{
        id: 1, scope: 'GLOBAL', name: '事务完整性', category: 'CORRECTNESS', pathPattern: '**/*.java',
        content: '检查事务边界', priority: 100, enabled: true, version: 1
      }, {
        id: 2, scope: 'GLOBAL', name: '停用规则', category: 'QUALITY', pathPattern: '**',
        content: '不应显示', priority: 100, enabled: false, version: 1
      }]), { status: 200 });
      if (url === '/api/projects/3/rules') return new Response(JSON.stringify([]), { status: 200 });
      throw new Error(`Unexpected URL: ${url}`);
    });

    const wrapper = mount(RuleCenter, { props: { projects: [{ id: 3, name: 'store' }] } });
    await flushPromises();
    await wrapper.get('.rule-tabs button:nth-child(2)').trigger('click');
    await wrapper.get('[data-test="rule-project-select"]').setValue('3');
    await flushPromises();

    expect(wrapper.get('[data-test="inherited-global-rules"]').text()).toContain('事务完整性');
    expect(wrapper.get('[data-test="inherited-global-rules"]').text()).not.toContain('停用规则');
  });

  it('uses the shared select control for rule category and project choices', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) => {
      if (url === '/api/rules/global') return new Response(JSON.stringify([]), { status: 200 });
      if (url === '/api/projects/3/rules') return new Response(JSON.stringify([]), { status: 200 });
      throw new Error(`Unexpected URL: ${url}`);
    });

    const wrapper = mount(RuleCenter, { props: { projects: [{ id: 3, name: 'store' }] } });
    await flushPromises();
    expect(wrapper.get('select').classes('app-select')).toBe(true);

    await wrapper.get('.rule-tabs button:nth-child(2)').trigger('click');
    expect(wrapper.findAll('select')).toHaveLength(2);
    expect(wrapper.findAll('select').every((select) => select.classes('app-select'))).toBe(true);

    await wrapper.get('.rule-tabs button:nth-child(3)').trigger('click');
    expect(wrapper.get('[data-test="rule-project-select"]').classes('app-select')).toBe(true);
  });
});
