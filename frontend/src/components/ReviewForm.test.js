import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import ReviewForm from './ReviewForm.vue';

describe('ReviewForm', () => {
  it('keeps git submission disabled until one supported file is selected', async () => {
    const wrapper = mount(ReviewForm, {
      props: {
        submitting: false,
        scanning: false,
        scanError: '',
        scanResult: {
          files: [{
            path: 'src/App.java',
            status: 'MODIFIED',
            additions: 2,
            deletions: 1,
            patch: '@@',
            supported: true,
            binary: false
          }]
        }
      }
    });

    await wrapper.get('[data-testid="mode-git"]').trigger('click');

    expect(wrapper.get('[data-testid="git-submit"]').element.disabled).toBe(true);

    await wrapper.get('[data-testid="file-checkbox-src-App-java"]').setValue(true);

    expect(wrapper.get('[data-testid="git-submit"]').element.disabled).toBe(false);
  });

  it('emits selected patch content for direct AI review', async () => {
    const wrapper = mount(ReviewForm, {
      props: {
        submitting: false,
        scanning: false,
        scanError: '',
        scanResult: {
          files: [{
            path: 'src/App.java',
            status: 'MODIFIED',
            additions: 2,
            deletions: 1,
            patch: 'diff --git a/src/App.java b/src/App.java',
            supported: true,
            binary: false
          }]
        }
      }
    });

    await wrapper.get('[data-testid="file-checkbox-src-App-java"]').setValue(true);
    await wrapper.get('[data-testid="git-submit"]').trigger('submit');

    expect(wrapper.emitted('submit')[0][0].files).toEqual([{
      path: 'src/App.java',
      content: 'diff --git a/src/App.java b/src/App.java',
      gitStatus: 'MODIFIED',
      additions: 2,
      deletions: 1,
      contentHash: null
    }]);
  });
});
