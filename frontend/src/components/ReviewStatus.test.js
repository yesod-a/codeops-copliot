import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import ReviewStatus from './ReviewStatus.vue';

describe('ReviewStatus', () => {
  it('does not present a local Git run as a pull request', () => {
    const wrapper = mount(ReviewStatus, {
      props: {
        task: {
          repository: 'D:/repo',
          pullRequestNumber: 123,
          title: 'Local changes',
          status: 'COMPLETED',
          id: 'local-task'
        },
        localGit: true
      }
    });

    expect(wrapper.text()).toContain('本地 Git');
    expect(wrapper.text()).not.toContain('PR #123');
  });
});
