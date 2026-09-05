<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import {
  createGlobalRule, createProjectRule, deleteGlobalRule, deleteProjectRule, listGlobalRules,
  listProjectRules, previewProjectRules, updateGlobalRule, updateProjectRule
} from '../api/reviewApi.js';

const props = defineProps({
  projects: { type: Array, default: () => [] }
});

const activeTab = ref('global');
const globalRules = ref([]);
const globalPage = ref(1);
const globalPageSize = 10;
const showGlobalList = ref(false);
const projectRules = ref([]);
const selectedProjectId = ref('');
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const editingRule = ref(null);
const previewPaths = ref('');
const preview = ref([]);
const categories = ['SECURITY', 'CORRECTNESS', 'CONCURRENCY', 'DATABASE', 'TEST', 'ARCHITECTURE', 'QUALITY'];
const emptyForm = () => ({ name: '', category: 'CORRECTNESS', pathPattern: '**', content: '', priority: 100, enabled: true });
const form = ref(emptyForm());
const selectedProject = computed(() => props.projects.find((project) => String(project.id) === String(selectedProjectId.value)) ?? null);
const visibleGlobalRules = computed(() => globalRules.value.slice((globalPage.value - 1) * globalPageSize, globalPage.value * globalPageSize));
const globalTotalPages = computed(() => Math.max(1, Math.ceil(globalRules.value.length / globalPageSize)));

async function loadGlobal() {
  loading.value = true;
  error.value = '';
  try {
    globalRules.value = await listGlobalRules();
    globalPage.value = 1;
  } catch (requestError) {
    error.value = requestError.message || '无法加载全局规则。';
  } finally {
    loading.value = false;
  }
}

async function loadProjectRules() {
  projectRules.value = [];
  preview.value = [];
  if (!selectedProjectId.value) return;
  loading.value = true;
  error.value = '';
  try {
    projectRules.value = await listProjectRules(selectedProjectId.value);
  } catch (requestError) {
    error.value = requestError.message || '无法加载项目规则。';
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editingRule.value = null;
  form.value = emptyForm();
}

function openEdit(rule) {
  editingRule.value = rule;
  form.value = { name: rule.name, category: rule.category, pathPattern: rule.pathPattern, content: rule.content, priority: rule.priority, enabled: rule.enabled };
}

function openGlobalEdit(rule) {
  showGlobalList.value = false;
  activeTab.value = 'global';
  openEdit(rule);
}

function cancelEdit() {
  editingRule.value = null;
  form.value = emptyForm();
}

async function saveRule() {
  const isProject = activeTab.value === 'project';
  if (isProject && !selectedProjectId.value) {
    error.value = '请先选择项目。';
    return;
  }
  saving.value = true;
  error.value = '';
  try {
    if (editingRule.value) {
      if (isProject) await updateProjectRule(selectedProjectId.value, editingRule.value.id, form.value);
      else await updateGlobalRule(editingRule.value.id, form.value);
    } else if (isProject) {
      await createProjectRule(selectedProjectId.value, form.value);
    } else {
      await createGlobalRule(form.value);
    }
    cancelEdit();
    if (isProject) await loadProjectRules(); else await loadGlobal();
  } catch (requestError) {
    error.value = requestError.message || '规则保存失败。';
  } finally {
    saving.value = false;
  }
}

async function removeRule(rule) {
  const isProject = activeTab.value === 'project';
  if (!window.confirm(`确认删除规则“${rule.name}”吗？`)) return;
  try {
    if (isProject) await deleteProjectRule(selectedProjectId.value, rule.id);
    else await deleteGlobalRule(rule.id);
    if (isProject) await loadProjectRules(); else await loadGlobal();
  } catch (requestError) {
    error.value = requestError.message || '规则删除失败。';
  }
}

async function removeGlobalRule(rule) {
  if (!window.confirm(`确认删除规则“${rule.name}”吗？`)) return;
  try {
    await deleteGlobalRule(rule.id);
    await loadGlobal();
  } catch (requestError) {
    error.value = requestError.message || '规则删除失败。';
  }
}

async function runPreview() {
  if (!selectedProjectId.value) {
    error.value = '请选择项目后再预览。';
    return;
  }
  const paths = previewPaths.value.split(/\r?\n|,/).map((path) => path.trim()).filter(Boolean);
  if (!paths.length) {
    error.value = '请输入至少一个仓库相对路径。';
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    preview.value = (await previewProjectRules(selectedProjectId.value, paths)).files;
  } catch (requestError) {
    error.value = requestError.message || '规则预览失败。';
  } finally {
    loading.value = false;
  }
}

watch(selectedProjectId, loadProjectRules);
onMounted(loadGlobal);
</script>

<template>
  <section class="rule-center">
    <div class="rule-tabs" role="tablist" aria-label="规则范围">
      <button type="button" :class="{ active: activeTab === 'global' }" @click="activeTab = 'global'; cancelEdit()">全局规则</button>
      <button type="button" :class="{ active: activeTab === 'project' }" @click="activeTab = 'project'; cancelEdit()">项目规则</button>
      <button type="button" :class="{ active: activeTab === 'preview' }" @click="activeTab = 'preview'; cancelEdit()">规则预览</button>
    </div>

    <p v-if="error" class="notice-banner"><span>!</span>{{ error }}</p>

    <template v-if="activeTab === 'global' || activeTab === 'project'">
      <div v-if="activeTab === 'project'" class="rule-project-picker">
        <label class="field"><span>项目</span><select v-model="selectedProjectId" data-test="rule-project-select"><option value="">选择已导入项目</option><option v-for="project in projects" :key="project.id" :value="String(project.id)">{{ project.name }}</option></select></label>
        <p v-if="selectedProject" class="rule-inherit-hint">项目规则会叠加全局规则，不会覆盖已命中的全局规则。</p>
      </div>

      <section v-if="activeTab === 'project' && selectedProject" class="inherited-rules" data-test="inherited-global-rules">
        <div><p class="eyebrow">已继承</p><h3>启用的全局规则</h3></div>
        <ul v-if="globalRules.filter((rule) => rule.enabled).length">
          <li v-for="rule in globalRules.filter((rule) => rule.enabled)" :key="rule.id"><strong>{{ rule.name }}</strong><code>{{ rule.pathPattern }}</code></li>
        </ul>
        <p v-else>当前没有启用的全局规则。</p>
      </section>

      <div class="rule-page-actions">
        <div><p class="eyebrow">{{ activeTab === 'global' ? '本机通用规则' : '项目专有规则' }}</p><h2>{{ activeTab === 'global' ? '全局规则' : selectedProject ? selectedProject.name : '选择项目' }}</h2></div>
        <div class="rule-actions"><button class="secondary-button" type="button" @click="showGlobalList = !showGlobalList">全局规则列表</button><button class="primary-button" type="button" :disabled="activeTab === 'project' && !selectedProjectId" @click="openCreate">新建规则</button></div>
      </div>

      <form class="rule-editor panel" @submit.prevent="saveRule">
        <div class="rule-editor-heading"><strong>{{ editingRule ? '编辑规则' : '创建规则' }}</strong><button v-if="editingRule" class="text-button" type="button" @click="cancelEdit">取消</button></div>
        <div class="rule-form-grid">
          <label class="field"><span>名称</span><input v-model.trim="form.name" maxlength="120" required placeholder="例如：事务完整性" /></label>
          <label class="field"><span>分类</span><select v-model="form.category"><option v-for="category in categories" :key="category" :value="category">{{ category }}</option></select></label>
          <label class="field"><span>适用路径 Glob</span><input v-model.trim="form.pathPattern" maxlength="255" required placeholder="**/*.java" /></label>
          <label class="field"><span>优先级</span><input v-model.number="form.priority" type="number" min="-100000" max="100000" required /></label>
          <label class="field field-wide"><span>规则正文</span><textarea v-model.trim="form.content" required rows="4" placeholder="给评审 Agent 的具体检查要求"></textarea></label>
          <label class="checkbox-field"><input v-model="form.enabled" type="checkbox" />启用此规则</label>
        </div>
        <button class="secondary-button" type="submit" :disabled="saving">{{ saving ? '保存中...' : editingRule ? '保存修改' : '创建规则' }}</button>
      </form>

      <p v-if="loading" class="scan-message">正在加载规则...</p>
      <div v-else class="rule-list">
        <article v-for="rule in activeTab === 'project' ? projectRules.slice(0, 10) : []" :key="rule.id" class="rule-list-item">
          <div class="rule-list-main"><div><strong>{{ rule.name }}</strong><span class="rule-category">{{ rule.category }}</span><span v-if="!rule.enabled" class="rule-disabled">已停用</span></div><code>{{ rule.pathPattern }}</code><p>{{ rule.content }}</p></div>
          <div class="rule-list-meta"><span>优先级 {{ rule.priority }}</span><span>v{{ rule.version }}</span><div><button class="icon-button subtle" type="button" title="编辑规则" @click="openEdit(rule)">✎</button><button class="icon-button subtle danger-icon" type="button" title="删除规则" @click="removeRule(rule)">×</button></div></div>
        </article>
        <p v-if="activeTab === 'project' && projectRules.length === 0" class="empty-rule-state">暂无规则。</p>
      </div>
    </template>

    <template v-else>
      <section class="panel preview-panel">
        <div class="panel-heading"><div><p class="eyebrow">按文件解析</p><h2>规则预览</h2></div></div>
        <div class="rule-preview-form">
          <label class="field"><span>项目</span><select v-model="selectedProjectId" data-test="rule-project-select"><option value="">选择已导入项目</option><option v-for="project in projects" :key="project.id" :value="String(project.id)">{{ project.name }}</option></select></label>
          <label class="field"><span>仓库相对路径</span><textarea v-model="previewPaths" data-test="preview-paths" rows="4" placeholder="backend/src/OrderService.java&#10;frontend/src/App.vue"></textarea></label>
          <button class="primary-button" data-test="preview-rules" type="button" :disabled="loading" @click="runPreview">{{ loading ? '解析中...' : '预览有效规则' }}</button>
        </div>
      </section>
      <section v-for="file in preview" :key="file.path" class="preview-result panel">
        <div class="preview-result-heading"><code>{{ file.path }}</code><span>Hash: <b>{{ file.effectiveRuleHash }}</b></span></div>
        <ol class="resolved-rule-list"><li v-for="rule in file.rules" :key="`${rule.source}-${rule.id ?? rule.name}`"><span>{{ rule.source }}</span><strong>{{ rule.name }}</strong><code v-if="rule.pattern">{{ rule.pattern }}</code></li></ol>
      </section>
      <p v-if="!preview.length && !loading" class="empty-rule-state">选择项目并输入路径后，可查看实际命中的规则。</p>
    </template>
  </section>

  <Teleport to="body">
    <div v-if="showGlobalList" class="rule-dialog-backdrop" role="presentation" @click.self="showGlobalList = false">
      <section class="global-rules-dialog" role="dialog" aria-modal="true" aria-labelledby="global-rules-dialog-title">
        <div class="panel-heading"><div><p class="eyebrow">全局规则</p><h2 id="global-rules-dialog-title">全部通用规则</h2></div><button class="icon-button subtle" type="button" title="关闭全局规则列表" @click="showGlobalList = false">×</button></div>
        <div class="rule-list">
          <article v-for="rule in visibleGlobalRules" :key="rule.id" class="rule-list-item">
            <div class="rule-list-main"><div><strong>{{ rule.name }}</strong><span class="rule-category">{{ rule.category }}</span><span v-if="!rule.enabled" class="rule-disabled">已停用</span></div><code>{{ rule.pathPattern }}</code><p>{{ rule.content }}</p></div>
            <div class="rule-list-meta"><span>优先级 {{ rule.priority }}</span><span>v{{ rule.version }}</span><div><button class="icon-button subtle" type="button" title="编辑规则" @click="openGlobalEdit(rule)">✎</button><button class="icon-button subtle danger-icon" type="button" title="删除规则" @click="removeGlobalRule(rule)">×</button></div></div>
          </article>
          <p v-if="!globalRules.length" class="empty-rule-state">暂无全局规则。</p>
        </div>
        <div v-if="globalRules.length" class="pagination" aria-label="全局规则分页"><button type="button" :disabled="globalPage <= 1" @click="globalPage--">上一页</button><span>第 {{ globalPage }} / {{ globalTotalPages }} 页</span><button type="button" :disabled="globalPage >= globalTotalPages" @click="globalPage++">下一页</button></div>
      </section>
    </div>
  </Teleport>
</template>
