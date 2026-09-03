<script setup>
import { computed, reactive, ref, watch } from 'vue';
import { getGitStatusLabel, getSelectableGitFiles, toggleAllGitFiles, toggleGitFile } from '../reviewState.js';

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
  title: '新增支付接口',
  path: 'src/main/java/com/acme/order/PaymentController.java',
  content: 'String token = request.getHeader("Authorization");\n// TODO add idempotency validation'
};
const form = reactive({ ...demoForm });
const gitForm = reactive({ repositoryPath: '', scope: 'WORKTREE', baseRef: '', title: '评审本地变更' });
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
    validationMessage.value = '请输入本地 Git 仓库的绝对路径。';
    return;
  }
  if (gitForm.scope === 'BASE_COMMIT' && !gitForm.baseRef.trim()) {
    validationMessage.value = '请输入用于对比的基准分支或提交。';
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
    validationMessage.value = '请输入评审标题。';
    return;
  }
  if (!selectedPaths.value.length) {
    validationMessage.value = '请至少选择一个支持评审的变更文件。';
    return;
  }
  validationMessage.value = '';
  const selectedFiles = changedFiles.value
    .filter((file) => selectedPaths.value.includes(file.path))
    .map((file) => ({
      path: file.path,
      content: file.patch || '',
      gitStatus: file.status,
      additions: file.additions ?? 0,
      deletions: file.deletions ?? 0,
      contentHash: null
    }));
  emit('submit', {
    mode: 'git',
    repositoryPath: gitForm.repositoryPath.trim(),
    scope: gitForm.scope,
    baseRef: gitForm.scope === 'BASE_COMMIT' ? gitForm.baseRef.trim() : null,
    title: gitForm.title.trim(),
    files: selectedFiles
  });
}

function submitManualReview() {
  if (!form.repository.trim() || !form.title.trim() || !form.path.trim() || !form.content.trim() || !Number.isInteger(Number(form.pullRequestNumber)) || Number(form.pullRequestNumber) < 1) {
    validationMessage.value = '请填写完整的评审信息后再提交。';
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
        <p class="eyebrow">创建评审</p>
        <h2>评审请求</h2>
      </div>
      <button class="icon-button subtle" type="button" title="载入示例" aria-label="载入示例" @click="loadDemo">+</button>
    </div>

    <div class="mode-switch" role="group" aria-label="评审来源">
      <button data-testid="mode-git" type="button" :class="{ selected: mode === 'git' }" @click="selectMode('git')">本地 Git</button>
      <button data-testid="mode-manual" type="button" :class="{ selected: mode === 'manual' }" @click="selectMode('manual')">手动粘贴</button>
    </div>

    <form v-if="mode === 'git'" class="review-form git-review-form" novalidate @submit.prevent="submitGitReview">
      <div class="git-scan-grid">
        <label class="field git-path-field">
          <span>本地仓库路径</span>
          <input v-model="gitForm.repositoryPath" type="text" placeholder="D:\\development\\project\\repository" autocomplete="off" />
        </label>
        <label class="field">
          <span>变更范围</span>
          <select v-model="gitForm.scope">
            <option value="WORKTREE">工作区变更</option>
            <option value="BASE_COMMIT">基准提交</option>
          </select>
        </label>
        <label v-if="gitForm.scope === 'BASE_COMMIT'" class="field">
          <span>基准分支或提交</span>
          <input v-model="gitForm.baseRef" type="text" placeholder="例如 main 或提交 SHA" autocomplete="off" />
        </label>
        <button class="secondary-button scan-button" type="button" :disabled="props.scanning" @click="scan">
          {{ props.scanning ? '扫描中...' : '扫描变更' }}
        </button>
      </div>

      <label class="field review-title-field">
        <span>评审标题</span>
        <input v-model="gitForm.title" type="text" placeholder="评审本地变更" />
      </label>

      <p v-if="props.scanError" class="form-error">{{ props.scanError }}</p>
      <p v-else-if="props.scanning" class="scan-message">正在读取本地 Git 变更...</p>
      <p v-else-if="props.scanResult && !changedFiles.length" class="scan-message">当前范围内没有发现变更文件。</p>

      <div v-if="changedFiles.length" class="changed-files" aria-label="Changed files">
        <div class="changed-files-toolbar">
          <strong>已选择 {{ selectedPaths.length }} / {{ selectableFiles.length }} 个文件</strong>
          <button type="button" class="text-button" @click="toggleAllFiles">{{ allSelectableSelected ? '清空选择' : '全选文件' }}</button>
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
            <span v-if="file.binary || file.supported === false" class="file-skip-reason">{{ file.skipReason || (file.binary ? '二进制文件' : '不支持的文件类型') }}</span>
          </span>
          <span class="git-status" :class="`git-status-${String(file.status).toLowerCase()}`">{{ getGitStatusLabel(file.status) }}</span>
          <span class="file-stats"><b>+{{ file.additions }}</b><i>-{{ file.deletions }}</i></span>
        </label>
      </div>

      <div v-if="selectedFile" class="patch-preview">
        <div class="patch-preview-heading"><span>补丁预览</span><code>{{ selectedFile.path }}</code></div>
        <pre>{{ selectedFile.patch || '该文件没有可用的文本补丁。' }}</pre>
      </div>

      <p v-if="validationMessage" class="form-error">{{ validationMessage }}</p>
      <button data-testid="git-submit" class="primary-button submit-button" type="submit" :disabled="props.submitting || !selectedPaths.length">
        <span class="button-dot"></span>
        {{ props.submitting ? '提交中...' : '评审选中文件' }}
      </button>
    </form>

    <form v-else class="review-form" novalidate @submit.prevent="submitManualReview">
      <div class="form-grid">
        <label class="field field-wide">
          <span>代码仓库</span>
          <input v-model="form.repository" type="text" placeholder="owner/repository" />
        </label>
        <label class="field">
          <span>拉取请求编号</span>
          <input v-model.number="form.pullRequestNumber" type="number" min="1" />
        </label>
        <label class="field field-wide">
          <span>变更标题</span>
          <input v-model="form.title" type="text" placeholder="描述本次变更" />
        </label>
        <label class="field field-wide">
          <span>文件路径</span>
          <input v-model="form.path" type="text" placeholder="src/main/java/..." />
        </label>
      </div>

      <label class="field">
        <span>变更内容</span>
          <textarea v-model="form.content" rows="7" spellcheck="false" placeholder="请粘贴变更后的 Java 代码"></textarea>
      </label>

      <p v-if="validationMessage" class="form-error">{{ validationMessage }}</p>
      <button class="primary-button submit-button" type="submit" :disabled="props.submitting">
        <span class="button-dot"></span>
        {{ props.submitting ? '提交中...' : '提交代码评审' }}
      </button>
    </form>
  </section>
</template>
