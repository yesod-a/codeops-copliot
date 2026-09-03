<script setup>
import { computed, reactive, ref, watch } from 'vue';
import { getSelectableGitFiles, toggleAllGitFiles, toggleGitFile } from '../reviewState.js';

const emit = defineEmits(['scan', 'submit', 'load-demo']);
const props = defineProps({
  submitting: Boolean,
  scanning: Boolean,
  scanError: { type: String, default: '' },
  scanResult: { type: Object, default: null }
});

const demoForm = {
  repository: 'acme/order-service',
  pullRequestNumber: 42,
  title: 'Add payment endpoint',
  path: 'src/main/java/com/acme/order/PaymentController.java',
  content: 'String token = request.getHeader("Authorization");\n// TODO add idempotency validation'
};
const form = reactive({ ...demoForm });
const gitForm = reactive({ repositoryPath: '', scope: 'WORKTREE', baseRef: '', title: 'Review local changes' });
const mode = ref('git');
const selectedPaths = ref([]);
const validationMessage = ref('');

const changedFiles = computed(() => props.scanResult?.files ?? []);
const selectableFiles = computed(() => getSelectableGitFiles(changedFiles.value));
const selectedFile = computed(() => changedFiles.value.find((file) => selectedPaths.value.includes(file.path)) ?? null);
const allSelectableSelected = computed(() => selectableFiles.value.length > 0
  && selectableFiles.value.every((file) => selectedPaths.value.includes(file.path)));

watch(() => props.scanResult, () => {
  selectedPaths.value = [];
  validationMessage.value = '';
});

function fileTestId(path) {
  return `file-checkbox-${path.replace(/[^a-zA-Z0-9]+/g, '-').replace(/^-|-$/g, '')}`;
}

function selectMode(nextMode) {
  mode.value = nextMode;
  validationMessage.value = '';
}

function scan() {
  if (!gitForm.repositoryPath.trim()) {
    validationMessage.value = 'Enter the absolute path of a local Git repository.';
    return;
  }
  if (gitForm.scope === 'BASE_COMMIT' && !gitForm.baseRef.trim()) {
    validationMessage.value = 'Enter a base commit or branch to compare.';
    return;
  }
  validationMessage.value = '';
  selectedPaths.value = [];
  emit('scan', {
    repositoryPath: gitForm.repositoryPath.trim(),
    scope: gitForm.scope,
    baseRef: gitForm.scope === 'BASE_COMMIT' ? gitForm.baseRef.trim() : null
  });
}

function toggleFile(path) {
  selectedPaths.value = toggleGitFile(selectedPaths.value, path);
}

function toggleAllFiles() {
  selectedPaths.value = toggleAllGitFiles(changedFiles.value, selectedPaths.value);
}

function submitGitReview() {
  if (!gitForm.title.trim()) {
    validationMessage.value = 'Enter a title for this review.';
    return;
  }
  if (!selectedPaths.value.length) {
    validationMessage.value = 'Select at least one supported changed file.';
    return;
  }
  validationMessage.value = '';
  emit('submit', {
    mode: 'git',
    repositoryPath: gitForm.repositoryPath.trim(),
    scope: gitForm.scope,
    baseRef: gitForm.scope === 'BASE_COMMIT' ? gitForm.baseRef.trim() : null,
    title: gitForm.title.trim(),
    files: selectedPaths.value
  });
}

function submitManualReview() {
  if (!form.repository.trim() || !form.title.trim() || !form.path.trim() || !form.content.trim() || !Number.isInteger(Number(form.pullRequestNumber)) || Number(form.pullRequestNumber) < 1) {
    validationMessage.value = 'Complete the review details before submitting.';
    return;
  }
  validationMessage.value = '';
  emit('submit', {
    repository: form.repository.trim(),
    pullRequestNumber: Number(form.pullRequestNumber),
    title: form.title.trim(),
    files: [{ path: form.path.trim(), content: form.content }]
  });
}

function loadDemo() {
  Object.assign(form, demoForm);
  validationMessage.value = '';
  emit('load-demo');
}
</script>

<template>
  <section class="panel form-panel">
    <div class="panel-heading">
      <div>
        <p class="eyebrow">START A REVIEW</p>
        <h2>Code review request</h2>
      </div>
      <button class="icon-button subtle" type="button" title="Load demo" aria-label="Load demo" @click="loadDemo">+</button>
    </div>

    <div class="mode-switch" role="group" aria-label="Review source">
      <button data-testid="mode-git" type="button" :class="{ selected: mode === 'git' }" @click="selectMode('git')">Local Git</button>
      <button data-testid="mode-manual" type="button" :class="{ selected: mode === 'manual' }" @click="selectMode('manual')">Manual paste</button>
    </div>

    <form v-if="mode === 'git'" class="review-form git-review-form" novalidate @submit.prevent="submitGitReview">
      <div class="git-scan-grid">
        <label class="field git-path-field">
          <span>Local repository path</span>
          <input v-model="gitForm.repositoryPath" type="text" placeholder="D:\\development\\project\\repository" autocomplete="off" />
        </label>
        <label class="field">
          <span>Change scope</span>
          <select v-model="gitForm.scope">
            <option value="WORKTREE">Working tree</option>
            <option value="BASE_COMMIT">Base commit</option>
          </select>
        </label>
        <label v-if="gitForm.scope === 'BASE_COMMIT'" class="field">
          <span>Base ref</span>
          <input v-model="gitForm.baseRef" type="text" placeholder="main or commit SHA" autocomplete="off" />
        </label>
        <button class="secondary-button scan-button" type="button" :disabled="props.scanning" @click="scan">
          {{ props.scanning ? 'Scanning...' : 'Scan changes' }}
        </button>
      </div>

      <label class="field review-title-field">
        <span>Review title</span>
        <input v-model="gitForm.title" type="text" placeholder="Review local changes" />
      </label>

      <p v-if="props.scanError" class="form-error">{{ props.scanError }}</p>
      <p v-else-if="props.scanning" class="scan-message">Reading local Git changes...</p>
      <p v-else-if="props.scanResult && !changedFiles.length" class="scan-message">No changed files were found for this scope.</p>

      <div v-if="changedFiles.length" class="changed-files" aria-label="Changed files">
        <div class="changed-files-toolbar">
          <strong>{{ selectedPaths.length }} of {{ selectableFiles.length }} files selected</strong>
          <button type="button" class="text-button" @click="toggleAllFiles">{{ allSelectableSelected ? 'Clear all' : 'Select all' }}</button>
        </div>
        <label v-for="file in changedFiles" :key="file.path" class="changed-file-row" :class="{ selected: selectedPaths.includes(file.path), unavailable: file.binary || file.supported === false }">
          <input
            :data-testid="fileTestId(file.path)"
            type="checkbox"
            :checked="selectedPaths.includes(file.path)"
            :disabled="file.binary || file.supported === false"
            @change="toggleFile(file.path)"
          />
          <span class="file-row-main">
            <code>{{ file.path }}</code>
            <span v-if="file.binary || file.supported === false" class="file-skip-reason">{{ file.skipReason || (file.binary ? 'Binary file' : 'Unsupported file type') }}</span>
          </span>
          <span class="git-status" :class="`git-status-${String(file.status).toLowerCase()}`">{{ file.status }}</span>
          <span class="file-stats"><b>+{{ file.additions }}</b><i>-{{ file.deletions }}</i></span>
        </label>
      </div>

      <div v-if="selectedFile" class="patch-preview">
        <div class="patch-preview-heading"><span>Patch preview</span><code>{{ selectedFile.path }}</code></div>
        <pre>{{ selectedFile.patch || 'No textual patch is available for this file.' }}</pre>
      </div>

      <p v-if="validationMessage" class="form-error">{{ validationMessage }}</p>
      <button data-testid="git-submit" class="primary-button submit-button" type="submit" :disabled="props.submitting || !selectedPaths.length">
        <span class="button-dot"></span>
        {{ props.submitting ? 'Submitting review...' : 'Review selected files' }}
      </button>
    </form>

    <form v-else class="review-form" novalidate @submit.prevent="submitManualReview">
      <div class="form-grid">
        <label class="field field-wide">
          <span>Repository</span>
          <input v-model="form.repository" type="text" placeholder="owner/repository" />
        </label>
        <label class="field">
          <span>Pull Request</span>
          <input v-model.number="form.pullRequestNumber" type="number" min="1" />
        </label>
        <label class="field field-wide">
          <span>Change title</span>
          <input v-model="form.title" type="text" placeholder="Describe this change" />
        </label>
        <label class="field field-wide">
          <span>File path</span>
          <input v-model="form.path" type="text" placeholder="src/main/java/..." />
        </label>
      </div>

      <label class="field">
        <span>Changed content</span>
        <textarea v-model="form.content" rows="7" spellcheck="false" placeholder="Paste changed Java code here"></textarea>
      </label>

      <p v-if="validationMessage" class="form-error">{{ validationMessage }}</p>
      <button class="primary-button submit-button" type="submit" :disabled="props.submitting">
        <span class="button-dot"></span>
        {{ props.submitting ? 'Submitting...' : 'Submit code review' }}
      </button>
    </form>
  </section>
</template>
