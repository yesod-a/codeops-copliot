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
});
