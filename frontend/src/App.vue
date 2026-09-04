<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { deleteProject, getAiHealth, getReviewDetails, importProject, listProjects, listReviews, readSelectedGitFiles, saveReview, scanRepository, submitAiReview, updateProjectPolicy } from './api/reviewApi.js';
import FindingList from './components/FindingList.vue';
import ReviewForm from './components/ReviewForm.vue';
import ReviewStatus from './components/ReviewStatus.vue';
import { calculateRiskScore, demoTask, getFindingCounts, getFindingText, getStatusMeta } from './reviewState.js';
import { getRoute } from './navigation.js';

const task = ref(structuredClone(demoTask));
const activeFilter = ref('ALL');
const submitting = ref(false);
const scanning = ref(false);
const scanResult = ref(null);
const scanError = ref('');
const connectionMessage = ref('本地示例');
const notice = ref('');
const localGitReview = ref(false);
const route = ref(getRoute(window.location.hash));
const reviewHistory = ref([]);
const historyLoading = ref(false);
const historyError = ref('');
const aiHealth = ref(null);
const projects = ref([]);
const projectsLoading = ref(false);
const projectError = ref('');
const projectPathInput = ref('');
const selectedProjectId = ref('');
const policySaving = ref(false);
let pollingTimer;

const selectedProject = computed(() => projects.value.find((project) => String(project.id) === String(selectedProjectId.value)) ?? null);

async function loadProjects() {
  projectsLoading.value = true;
  projectError.value = '';
  try {
    projects.value = await listProjects();
    if (selectedProjectId.value && !selectedProject.value) selectedProjectId.value = '';
  } catch (error) {
    projectError.value = error.message || '无法加载项目列表。';
  } finally {
    projectsLoading.value = false;
  }
}

async function handleImportProject() {
  if (!projectPathInput.value.trim()) {
    projectError.value = '请输入本机 Git 仓库路径。';
    return;
  }
  projectsLoading.value = true;
  projectError.value = '';
  try {
    const project = await importProject(projectPathInput.value.trim());
    await loadProjects();
    selectedProjectId.value = project.id;
    projectPathInput.value = project.repositoryPath;
  } catch (error) {
    projectError.value = error.message || '项目引入失败。';
  } finally {
    projectsLoading.value = false;
  }
}

async function handlePolicySave(project) {
  policySaving.value = true;
  projectError.value = '';
  try {
    const saved = await updateProjectPolicy(project.id, project.policy);
    projects.value = projects.value.map((item) => item.id === saved.id ? saved : item);
  } catch (error) {
    projectError.value = error.message || '策略保存失败。';
  } finally {
    policySaving.value = false;
  }
}

async function handleProjectDelete(project) {
  if (!window.confirm(`确认删除项目“${project.name}”及其评审历史吗？`)) return;
  try {
    await deleteProject(project.id);
    projects.value = projects.value.filter((item) => item.id !== project.id);
    if (String(selectedProjectId.value) === String(project.id)) selectedProjectId.value = '';
  } catch (error) {
    projectError.value = error.message || '项目删除失败。';
  }
}

function handleProjectChange(projectId) {
  selectedProjectId.value = projectId ?? '';
}

async function loadReviewHistory() {
  historyLoading.value = true;
  historyError.value = '';
  try {
    reviewHistory.value = await listReviews({ limit: 20, offset: 0 });
  } catch (error) {
    historyError.value = error.message || '无法加载评审历史。';
  } finally {
    historyLoading.value = false;
  }
}

function syncRoute() {
  route.value = getRoute(window.location.hash);
  if (route.value === 'history') loadReviewHistory();
}

function goTo(nextRoute) {
  window.location.hash = nextRoute;
}

const counts = computed(() => getFindingCounts(task.value.findings));
const score = computed(() => calculateRiskScore(task.value.findings));
const scoreLabel = computed(() => score.value >= 80 ? '状态良好' : score.value >= 60 ? '需要关注' : '高风险');
const statusMeta = computed(() => getStatusMeta(task.value.status));

function loadDemo() {
  task.value = structuredClone(demoTask);
  activeFilter.value = 'ALL';
  connectionMessage.value = '本地示例';
  notice.value = '';
  localGitReview.value = false;
}

async function handleScan(payload) {
  scanning.value = true;
  scanError.value = '';
  try {
    scanResult.value = await scanRepository(payload);
    connectionMessage.value = 'API 已连接';
  } catch (error) {
    scanResult.value = null;
    scanError.value = error.message || '无法扫描本地仓库。';
    connectionMessage.value = '后端不可用';
  } finally {
    scanning.value = false;
  }
}

async function handleSubmit(payload) {
  window.clearTimeout(pollingTimer);
  submitting.value = true;
  notice.value = '';
  localGitReview.value = payload.mode === 'git';
  try {
    let reviewPayload = payload;
    if (payload.mode === 'git') {
      const selectedPaths = payload.files.map((file) => file.path);
      const freshFiles = await readSelectedGitFiles({
        repositoryPath: payload.repositoryPath,
        scope: payload.scope,
        baseRef: payload.baseRef,
        files: selectedPaths
      });
      const freshByPath = new Map(freshFiles.map((file) => [file.path, file]));
      reviewPayload = {
        ...payload,
        files: payload.files.map((file) => ({
          ...file,
          content: freshByPath.get(file.path)?.content ?? ''
        }))
      };
    }
    const aiResponse = await submitAiReview(reviewPayload);
    const aiTask = makeAiTask(payload, aiResponse.findings);
    task.value = aiTask;
    connectionMessage.value = 'LLM 已连接';

    try {
      const saved = await saveReview(buildSavePayload(reviewPayload, aiResponse));
      task.value = saved;
      await loadReviewHistory();
    } catch (saveError) {
      notice.value = `评审已完成，但历史记录保存失败：${saveError.message || '数据库请求失败'}`;
      return;
    }
  } catch (error) {
    connectionMessage.value = '后端不可用';
    notice.value = `大模型评审服务不可用：${error.message || '请求失败'}`;
  } finally {
    submitting.value = false;
  }
}

function buildSavePayload(payload, aiResponse) {
  const files = payload.files.map((file) => ({
    path: file.path,
    gitStatus: file.gitStatus ?? null,
    additions: file.additions ?? 0,
    deletions: file.deletions ?? 0,
    patch: file.content ?? '',
    contentHash: file.contentHash ?? null
  }));
  return {
    requestId: crypto.randomUUID(),
    repositoryPath: selectedProject.value?.repositoryPath ?? payload.repositoryPath ?? null,
    repository: payload.repository ?? null,
    title: payload.title,
    sourceType: payload.mode === 'git' ? 'GIT' : 'MANUAL',
    scope: payload.scope ?? null,
    baseRef: payload.baseRef ?? null,
    branch: payload.mode === 'git' ? scanResult.value?.branch ?? null : null,
    headCommit: payload.mode === 'git' ? scanResult.value?.headCommit ?? null : null,
    modelName: aiHealth.value?.model ?? null,
    files,
    findings: aiResponse.findings ?? []
  };
}

function makeAiTask(payload, findings) {
  const now = new Date().toISOString();
  const repository = payload.repositoryPath ?? payload.repository;
  return {
    ...structuredClone(demoTask),
    id: `ai-${Date.now()}`,
    repository,
    pullRequestNumber: 0,
    title: payload.title,
    status: 'COMPLETED',
    createdAt: now,
    updatedAt: now,
    findings: findings ?? [],
    error: null
  };
}

async function openHistory(id) {
  try {
    const detail = await getReviewDetails(id);
    task.value = detail;
    localGitReview.value = detail.sourceType === 'GIT';
    notice.value = '';
    goTo('review');
  } catch (error) {
    historyError.value = error.message || '无法加载评审详情。';
  }
}

async function checkAiHealth() {
  try {
    aiHealth.value = await getAiHealth();
  } catch {
    aiHealth.value = { status: 'unavailable' };
  }
}

function exportMarkdown() {
  const reviewReference = localGitReview.value
    ? `- Source: Local Git\n- Repository path: ${task.value.repository}`
    : `- Repository: ${task.value.repository}\n- Pull Request: #${task.value.pullRequestNumber}`;
  const header = `# ${task.value.title}\n\n${reviewReference}\n- Status: ${statusMeta.value.label}\n- Risk score: ${score.value}/100\n`;
  const body = task.value.findings.length
    ? task.value.findings.map((finding, index) => [
      `## ${index + 1}. [${finding.severity}] ${getFindingText(finding.message)}`,
      `- 分类：${finding.category}`,
      `- 位置：${finding.file}:${finding.line}`,
      `- 置信度：${Math.round((finding.confidence ?? 0) * 100)}%`,
      '',
      `**代码证据**\n\n\`\`\`java\n${finding.evidence}\n\`\`\``,
      '',
      `**修复建议**\n\n${getFindingText(finding.suggestion)}`
    ].join('\n')).join('\n\n')
    : '暂无评审问题。';
  const blob = new Blob([`${header}\n${body}\n`], { type: 'text/markdown;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  const suffix = localGitReview.value ? 'local-git' : `pr-${task.value.pullRequestNumber}`;
  link.download = `${task.value.repository.replace(/[^a-z0-9]+/gi, '-')}-${suffix}-review.md`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

onMounted(() => {
  window.addEventListener('hashchange', syncRoute);
  checkAiHealth();
  loadProjects();
  loadReviewHistory();
});
onBeforeUnmount(() => {
  window.clearTimeout(pollingTimer);
  window.removeEventListener('hashchange', syncRoute);
});
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand-lockup">
        <div class="brand-mark"><span></span><span></span><span></span></div>
        <div><strong>CodeOps</strong><span>Copilot</span></div>
      </div>
      <nav class="side-nav" aria-label="主导航">
        <p class="nav-label">工作区</p>
        <a class="nav-item" :class="{ active: route === 'review' }" href="#review"><span class="nav-icon">◈</span>评审工作台</a>
        <a class="nav-item" :class="{ active: route === 'projects' }" href="#projects"><span class="nav-icon">▦</span>项目</a>
        <a class="nav-item" :class="{ active: route === 'history' }" href="#history"><span class="nav-icon">◷</span>历史记录</a>
        <p class="nav-label nav-label-spaced">系统</p>
        <span class="nav-item nav-item-disabled"><span class="nav-icon">⚙</span>设置（即将推出）</span>
      </nav>
      <div class="sidebar-footer">
        <div class="model-card">
          <span class="pulse-dot"></span>
          <div><span>评审引擎</span><strong>{{ aiHealth?.status === 'ready' ? 'LangChain 大模型' : 'LLM 服务未启用' }}</strong></div>
        </div>
        <div class="user-card"><span class="avatar">YL</span><div><strong>Yu Li</strong><span>开发者</span></div><span class="more-icon">•••</span></div>
      </div>
    </aside>

    <main class="main-content" id="review">
      <header class="topbar">
        <div class="breadcrumb"><span>工作区</span><b>/</b><strong>{{ route === 'review' ? '评审工作台' : route === 'projects' ? '项目' : '历史记录' }}</strong></div>
        <div class="topbar-actions"><span class="connection-pill"><span class="pulse-dot"></span>{{ connectionMessage }}</span><button class="icon-button" title="帮助">?</button><span class="avatar small">YL</span></div>
      </header>

      <div v-if="route === 'review'" class="content-wrap">
        <section class="page-intro">
          <div><p class="eyebrow">2026 年 09 月 02 日</p><h1>代码评审工作台</h1><p class="intro-copy">把每一次代码变更，变成可追踪的工程质量信号。</p></div>
          <button class="secondary-button" type="button" @click="loadDemo"><span>↻</span>载入示例</button>
        </section>

        <p v-if="notice" class="notice-banner"><span>!</span>{{ notice }}</p>

        <section class="metrics-grid">
          <div class="metric-card accent-card"><div class="metric-label">当前风险评分 <span>↗</span></div><strong>{{ score }}</strong><div class="metric-foot"><span class="trend positive">{{ scoreLabel }}</span><span>满分 100</span></div></div>
          <div class="metric-card"><div class="metric-label">发现问题</div><strong>{{ counts.total }}</strong><div class="metric-foot"><span class="trend">{{ counts.high + counts.critical }} 项需优先处理</span><span>本次 PR</span></div></div>
          <div class="metric-card"><div class="metric-label">最高风险</div><strong>{{ counts.critical + counts.high }}</strong><div class="metric-foot"><span class="trend warning">High / Critical</span><span>需要关注</span></div></div>
          <div class="metric-card"><div class="metric-label">AI 平均置信度</div><strong>{{ counts.total ? Math.round(task.findings.reduce((sum, item) => sum + item.confidence * 100, 0) / counts.total) : 0 }}<small>%</small></strong><div class="metric-foot"><span class="trend positive">结构化输出</span><span>评审引擎</span></div></div>
        </section>

        <section class="workspace-grid">
          <ReviewForm
            :submitting="submitting"
            :scanning="scanning"
            :scan-result="scanResult"
            :scan-error="scanError"
            :projects="projects"
            :selected-project-id="selectedProjectId"
            @scan="handleScan"
            @submit="handleSubmit"
            @project-change="handleProjectChange"
            @load-demo="loadDemo"
          />
          <ReviewStatus :task="task" :local-git="localGitReview" />
        </section>

        <div class="report-bar"><div><span class="report-status" :class="`tone-${statusMeta.tone}`"><span class="status-dot"></span>{{ statusMeta.label }}</span><span class="report-updated">最后更新 · {{ task.updatedAt ? new Date(task.updatedAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) : '--:--' }}</span></div><button class="ghost-button" type="button" @click="exportMarkdown">导出 Markdown <span>↓</span></button></div>
        <FindingList v-model="activeFilter" :findings="task.findings" />
      </div>

      <div v-else-if="route === 'projects'" class="content-wrap">
        <section class="page-intro">
          <div><p class="eyebrow">项目管理</p><h1>本地项目</h1><p class="intro-copy">引入本机 Git 仓库，并配置代码评审触发策略。</p></div>
          <button class="secondary-button" type="button" @click="goTo('review')"><span>↗</span>开始评审</button>
        </section>
        <p v-if="projectError" class="notice-banner"><span>!</span>{{ projectError }}</p>
        <section class="panel project-import-panel">
          <div class="panel-heading"><div><p class="eyebrow">引入项目</p><h2>连接本机 Git 仓库</h2></div><span v-if="projectsLoading" class="scan-message">处理中...</span></div>
          <div class="project-import-form">
            <label class="field"><span>仓库路径</span><input v-model="projectPathInput" type="text" placeholder="D:\\development\\project\\repository" /></label>
            <button class="primary-button project-import-button" type="button" :disabled="projectsLoading" @click="handleImportProject">{{ projectsLoading ? '引入中...' : '引入项目' }}</button>
          </div>
        </section>
        <section v-if="projects.length" class="project-list">
          <article v-for="project in projects" :key="project.id" class="panel project-item">
            <div class="panel-heading">
              <div><p class="eyebrow">已引入项目</p><h2>{{ project.name }}</h2><p class="project-path mono-value">{{ project.repositoryPath }}</p></div>
              <button class="ghost-button danger-button" type="button" @click="handleProjectDelete(project)">删除项目</button>
            </div>
            <div class="project-detail-grid">
              <div><span>当前分支</span><strong>{{ project.branch || '未命名分支' }}</strong></div>
              <div><span>HEAD 提交</span><strong class="mono-value">{{ project.headCommit?.slice(0, 8) || '-' }}</strong></div>
              <div><span>状态</span><strong>{{ project.policy.enabled ? '已启用' : '已停用' }}</strong></div>
            </div>
            <div class="policy-grid">
              <label class="checkbox-field"><input v-model="project.policy.enabled" type="checkbox" />启用项目评审</label>
              <label class="checkbox-field"><input v-model="project.policy.preCommitEnabled" type="checkbox" />提交前评审</label>
              <label class="checkbox-field"><input v-model="project.policy.prePushEnabled" type="checkbox" />推送前评审</label>
              <label class="checkbox-field"><input v-model="project.policy.postMergeEnabled" type="checkbox" />合并后评审</label>
              <label class="field"><span>阻断严重级别</span><select v-model="project.policy.failOnSeverity"><option value="LOW">LOW</option><option value="MEDIUM">MEDIUM</option><option value="HIGH">HIGH</option><option value="CRITICAL">CRITICAL</option></select></label>
              <label class="checkbox-field"><input v-model="project.policy.failOpen" type="checkbox" />LLM 异常时放行</label>
            </div>
            <div class="project-item-actions"><button class="secondary-button" type="button" :disabled="policySaving" @click="handlePolicySave(project)">{{ policySaving ? '保存中...' : '保存评审策略' }}</button><button class="primary-button project-review-button" type="button" @click="selectedProjectId = project.id; goTo('review')">使用此项目评审</button></div>
          </article>
        </section>
        <div v-else-if="!projectsLoading" class="empty-state history-empty"><span class="empty-icon">+</span><strong>还没有引入项目</strong><span>引入仓库后，可以在工作台选择项目并启用 Git Hook 评审。</span></div>
      </div>

      <div v-else class="content-wrap">
        <section class="page-intro">
          <div><p class="eyebrow">评审记录</p><h1>历史记录</h1><p class="intro-copy">查看数据库保存的评审任务。</p></div>
          <button class="secondary-button" type="button" @click="goTo('review')"><span>↗</span>新建评审</button>
        </section>
        <p v-if="historyError" class="notice-banner"><span>!</span>{{ historyError }}</p>
        <p v-else-if="historyLoading" class="scan-message">正在加载评审历史...</p>
        <section v-else-if="reviewHistory.length" class="history-list">
          <article v-for="item in reviewHistory" :key="item.id" class="history-item" tabindex="0" @click="openHistory(item.id)" @keydown.enter="openHistory(item.id)">
            <div class="history-item-main"><span class="repo-mark">{{ item.sourceType === 'GIT' ? 'GIT' : 'PR' }}</span><div><strong>{{ item.title }}</strong><span>{{ item.sourceType === 'GIT' ? '本地 Git' : '手动评审' }} · {{ item.repository }}</span></div></div>
            <div class="history-item-meta"><span class="status-badge" :class="`tone-${getStatusMeta(item.status).tone}`"><span class="status-dot"></span>{{ getStatusMeta(item.status).label }}</span><time>{{ item.completedAt || item.createdAt ? new Date(item.completedAt || item.createdAt).toLocaleString('zh-CN') : '-' }}</time></div>
          </article>
        </section>
        <div v-else class="empty-state history-empty"><span class="empty-icon">◷</span><strong>暂无评审记录</strong><span>提交一次本地 Git 或手动评审后，记录会显示在这里。</span><button class="primary-button" type="button" @click="goTo('review')">创建第一条评审</button></div>
      </div>
    </main>
  </div>
</template>
