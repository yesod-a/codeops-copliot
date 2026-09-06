<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { cancelReviewTask, deleteProject, getAiHealth, getCurrentUser, getReviewDetails, getReviewTask, importProject, listProjects, listReviewTasks, listReviews, login, logout, readSelectedGitFiles, registerCentralProject, retryReviewTask, saveReview, scanRepository, submitAiReview, updateProjectPolicy } from './api/reviewApi.js';
import FindingList from './components/FindingList.vue';
import ReviewForm from './components/ReviewForm.vue';
import ReviewStatus from './components/ReviewStatus.vue';
import RuleCenter from './components/RuleCenter.vue';
import UserManagement from './components/UserManagement.vue';
import { calculateRiskScore, demoTask, getFindingCounts, getFindingText, getStatusMeta } from './reviewState.js';
import { getHistoryId, getRoute, getTaskId } from './navigation.js';

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
const reviewTasks = ref([]);
const tasksLoading = ref(false);
const tasksError = ref('');
const taskDetail = ref(null);
const taskDetailLoading = ref(false);
const taskDetailError = ref('');
const taskDetailId = ref(null);
const taskPage = ref(0);
const taskTotalPages = ref(1);
const historyDetailLoading = ref(false);
const historyDetailId = ref(null);
const aiHealth = ref(null);
const projects = ref([]);
const projectsLoading = ref(false);
const projectError = ref('');
const projectPathInput = ref('');
const selectedProjectId = ref('');
const policySaving = ref(false);
const projectPage = ref(1);
const historyPage = ref(1);
const pageSize = 10;
const projectPageSize = 3;
let pollingTimer;
const currentUser = ref(null);
const authLoading = ref(true);
const loginLoading = ref(false);
const loginError = ref('');
const loginForm = ref({ username: '', password: '' });
const centralDeployment = import.meta.env.VITE_CODEOPS_DEPLOYMENT_MODE === 'central';

const selectedProject = computed(() => projects.value.find((project) => String(project.id) === String(selectedProjectId.value)) ?? null);
const visibleProjects = computed(() => projects.value.slice((projectPage.value - 1) * projectPageSize, projectPage.value * projectPageSize));
const projectTotalPages = computed(() => Math.max(1, Math.ceil(projects.value.length / projectPageSize)));
const visibleHistory = computed(() => reviewHistory.value.slice((historyPage.value - 1) * pageSize, historyPage.value * pageSize));
const historyTotalPages = computed(() => Math.max(1, Math.ceil(reviewHistory.value.length / pageSize)));
const detailFilesPage = ref(1);
const visibleDetailFiles = computed(() => (task.value.files ?? []).slice((detailFilesPage.value - 1) * pageSize, detailFilesPage.value * pageSize));
const detailFilesTotalPages = computed(() => Math.max(1, Math.ceil((task.value.files ?? []).length / pageSize)));

async function loadProjects() {
  projectsLoading.value = true;
  projectError.value = '';
  try {
    projects.value = await listProjects();
    projectPage.value = Math.min(projectPage.value, projectTotalPages.value);
    if (selectedProjectId.value && !selectedProject.value) selectedProjectId.value = '';
  } catch (error) {
    projectError.value = error.message || '无法加载项目列表。';
  } finally {
    projectsLoading.value = false;
  }
}

async function handleImportProject() {
  if (!projectPathInput.value.trim()) {
    projectError.value = centralDeployment ? '请输入仓库标识。' : '请输入本机 Git 仓库路径。';
    return;
  }
  projectsLoading.value = true;
  projectError.value = '';
  try {
    const value = projectPathInput.value.trim();
    const project = centralDeployment
      ? await registerCentralProject(value.replace(/\.git\/?$/, '').split(/[/:]/).filter(Boolean).at(-1) || value, value)
      : await importProject(value);
    await loadProjects();
    selectedProjectId.value = project.id;
    projectPathInput.value = project.remoteUrl ?? project.repositoryPath;
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
    historyPage.value = Math.min(historyPage.value, historyTotalPages.value);
  } catch (error) {
    historyError.value = error.message || '无法加载评审历史。';
  } finally {
    historyLoading.value = false;
  }
}

async function loadReviewTasks() {
  tasksLoading.value = true;
  tasksError.value = '';
  try {
    const result = await listReviewTasks({ page: taskPage.value, size: 10 });
    reviewTasks.value = result.items ?? [];
    taskTotalPages.value = Math.max(1, result.totalPages ?? 1);
  } catch (error) { tasksError.value = error.message || '无法加载评审任务。'; }
  finally { tasksLoading.value = false; }
}

async function cancelTask(taskId) { await cancelReviewTask(taskId); await loadReviewTasks(); }
async function retryTask(taskId) { await retryReviewTask(taskId); await loadReviewTasks(); }

function openTask(taskId) {
  window.location.hash = `tasks/${encodeURIComponent(taskId)}`;
}

function scheduleTaskDetailPoll(id) {
  window.clearTimeout(pollingTimer);
  if (!taskDetail.value || !isTaskActive(taskDetail.value.status)) return;
  pollingTimer = window.setTimeout(() => {
    if (getRoute(window.location.hash) === 'task-detail' && getTaskId(window.location.hash) === id) loadTaskDetail(id);
  }, 2500);
}

async function loadTaskDetail(id) {
  taskDetailId.value = id;
  taskDetailLoading.value = true;
  taskDetailError.value = '';
  try {
    taskDetail.value = await getReviewTask(id);
    scheduleTaskDetailPoll(id);
  } catch (error) {
    taskDetailError.value = error.message || '无法加载评审任务详情。';
  } finally {
    taskDetailLoading.value = false;
  }
}

function syncRoute() {
  const nextRoute = getRoute(window.location.hash);
  if (nextRoute === 'users' && currentUser.value?.role !== 'ADMIN') {
    route.value = 'review';
    if (window.location.hash !== '#review') window.location.hash = '#review';
    return;
  }
  route.value = nextRoute;
  if (route.value !== 'task-detail') window.clearTimeout(pollingTimer);
  if (route.value === 'history') loadReviewHistory();
  if (route.value === 'tasks') loadReviewTasks();
  if (route.value === 'task-detail') {
    const id = getTaskId(window.location.hash);
    if (id && id !== taskDetailId.value) loadTaskDetail(id);
  }
  if (route.value === 'history-detail') {
    const id = getHistoryId(window.location.hash);
    if (id && id !== historyDetailId.value) loadHistoryDetail(id);
  }
}

function redirectUnauthorizedUserRoute() {
  if (getRoute(window.location.hash) === 'users' && currentUser.value?.role !== 'ADMIN') {
    route.value = 'review';
    if (window.location.hash !== '#review') window.location.hash = '#review';
  }
}

function goTo(nextRoute) {
  window.location.hash = nextRoute;
}

const counts = computed(() => getFindingCounts(task.value.findings));
const score = computed(() => calculateRiskScore(task.value.findings));
const scoreLabel = computed(() => score.value >= 80 ? '状态良好' : score.value >= 60 ? '需要关注' : '高风险');
const statusMeta = computed(() => getStatusMeta(task.value.status));
const taskDetailFindings = computed(() => (taskDetail.value?.groups ?? []).flatMap((group) => group.findings ?? []));

function isTaskActive(status) {
  return ['QUEUED', 'RUNNING', 'RETRY_WAIT', 'CANCEL_REQUESTED'].includes(status);
}

function taskStatusLabel(status) {
  return ({ QUEUED: '排队中', RUNNING: '执行中', RETRY_WAIT: '等待重试', CANCEL_REQUESTED: '取消中', CANCELLED: '已取消', COMPLETED: '已完成', FAILED: '失败' })[status] ?? status;
}

function taskGroupStatusLabel(status) {
  return ({ QUEUED: '排队中', RUNNING: '执行中', RETRY_WAIT: '等待重试', CANCELLED: '已取消', COMPLETED: '已完成', FAILED: '失败' })[status] ?? status;
}

function taskStatusTone(status) {
  return ({ COMPLETED: 'success', FAILED: 'danger', CANCELLED: 'neutral', RUNNING: 'info', QUEUED: 'neutral', RETRY_WAIT: 'danger', CANCEL_REQUESTED: 'danger' })[status] ?? 'neutral';
}

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
  route.value = 'history-detail';
  goTo(`history/${encodeURIComponent(id)}`);
  await loadHistoryDetail(id);
}

async function loadHistoryDetail(id) {
  historyDetailId.value = id;
  historyDetailLoading.value = true;
  historyError.value = '';
  try {
    const detail = await getReviewDetails(id);
    task.value = detail;
    detailFilesPage.value = 1;
    localGitReview.value = detail.sourceType === 'GIT';
    notice.value = '';
  } catch (error) {
    historyError.value = error.message || '无法加载评审详情。';
  } finally {
    historyDetailLoading.value = false;
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
  initializeAuth();
});
onBeforeUnmount(() => {
  window.clearTimeout(pollingTimer);
  window.removeEventListener('hashchange', syncRoute);
});

async function initializeAuth() {
  authLoading.value = true;
  try {
    currentUser.value = await getCurrentUser();
    redirectUnauthorizedUserRoute();
    checkAiHealth();
    loadProjects();
    loadReviewHistory();
    loadReviewTasks();
    if (route.value === 'history-detail') {
      const id = getHistoryId(window.location.hash);
      if (id) loadHistoryDetail(id);
    }
    if (route.value === 'task-detail') {
      const id = getTaskId(window.location.hash);
      if (id) loadTaskDetail(id);
    }
  } catch {
    currentUser.value = null;
  } finally {
    authLoading.value = false;
  }
}

async function handleLogin() {
  loginLoading.value = true;
  loginError.value = '';
  try {
    currentUser.value = await login(loginForm.value.username.trim(), loginForm.value.password);
    loginForm.value.password = '';
    redirectUnauthorizedUserRoute();
    checkAiHealth();
    loadProjects();
    loadReviewHistory();
    loadReviewTasks();
  } catch (error) {
    loginError.value = error.message || '登录失败，请检查用户名和密码。';
  } finally {
    loginLoading.value = false;
  }
}

async function handleLogout() {
  await logout().catch(() => {});
  currentUser.value = null;
  window.location.hash = '#review';
}
</script>

<template>
  <div v-if="authLoading" class="auth-loading">正在验证登录状态...</div>
  <div v-else-if="!currentUser" class="auth-page">
    <form class="auth-card" @submit.prevent="handleLogin">
      <div class="brand-lockup auth-brand"><div class="brand-mark"><span></span><span></span><span></span></div><div><strong>CodeOps</strong><span>Copilot</span></div></div>
      <h1>登录 CodeOps</h1>
      <p class="intro-copy">登录后管理项目、评审规则和历史记录。</p>
      <p v-if="loginError" class="notice-banner"><span>!</span>{{ loginError }}</p>
      <label class="field"><span>用户名</span><input v-model="loginForm.username" autocomplete="username" required /></label>
      <label class="field"><span>密码</span><input v-model="loginForm.password" type="password" autocomplete="current-password" required /></label>
      <button class="primary-button auth-submit" type="submit" :disabled="loginLoading">{{ loginLoading ? '登录中...' : '登录' }}</button>
    </form>
  </div>
  <div v-else class="app-shell">
    <aside class="sidebar">
      <div class="brand-lockup">
        <div class="brand-mark"><span></span><span></span><span></span></div>
        <div><strong>CodeOps</strong><span>Copilot</span></div>
      </div>
      <nav class="side-nav" aria-label="主导航">
        <p class="nav-label">工作区</p>
        <a class="nav-item" :class="{ active: route === 'review' }" href="#review"><span class="nav-icon">◈</span>评审工作台</a>
        <a class="nav-item" :class="{ active: route === 'projects' }" href="#projects"><span class="nav-icon">▦</span>项目</a>
        <a class="nav-item" :class="{ active: route === 'history' || route === 'history-detail' }" href="#history"><span class="nav-icon">◷</span>历史记录</a>
        <a class="nav-item" :class="{ active: route === 'tasks' }" href="#tasks"><span class="nav-icon">◌</span>评审任务</a>
        <a class="nav-item" :class="{ active: route === 'rules' }" href="#rules"><span class="nav-icon">☷</span>评审规则</a>
        <a v-if="currentUser?.role === 'ADMIN'" class="nav-item" :class="{ active: route === 'users' }" href="#users"><span class="nav-icon">♙</span>用户管理</a>
        <p class="nav-label nav-label-spaced">系统</p>
        <span class="nav-item nav-item-disabled"><span class="nav-icon">⚙</span>设置（即将推出）</span>
      </nav>
      <div class="sidebar-footer">
        <div class="model-card">
          <span class="pulse-dot"></span>
          <div><span>评审引擎</span><strong>{{ aiHealth?.status === 'ready' ? 'LangChain 大模型' : 'LLM 服务未启用' }}</strong></div>
        </div>
        <div class="user-card"><span class="avatar">{{ currentUser?.displayName?.slice(0, 2) || 'U' }}</span><div><strong>{{ currentUser?.displayName }}</strong><span>{{ currentUser?.role === 'ADMIN' ? '管理员' : '用户' }}</span></div><button class="more-icon" type="button" title="退出登录" @click="handleLogout">退出</button></div>
      </div>
    </aside>

    <main class="main-content" id="review">
      <header class="topbar">
        <div class="breadcrumb"><span>工作区</span><b>/</b><strong>{{ route === 'review' ? '评审工作台' : route === 'projects' ? '项目' : route === 'rules' ? '评审规则' : route === 'users' ? '用户管理' : route === 'tasks' ? '评审任务' : route === 'history-detail' ? '评审详情' : '历史记录' }}</strong></div>
          <div class="topbar-actions"><span class="connection-pill"><span class="pulse-dot"></span>{{ connectionMessage }}</span><button class="icon-button" title="帮助">?</button><span class="avatar small">{{ currentUser?.displayName?.slice(0, 2) || 'U' }}</span></div>
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
            :central-mode="centralDeployment"
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
          <div><p class="eyebrow">项目管理</p><h1>{{ centralDeployment ? '评审项目' : '本地项目' }}</h1><p class="intro-copy">{{ centralDeployment ? '登记代码仓库标识，并配置代码评审触发策略。' : '引入本机 Git 仓库，并配置代码评审触发策略。' }}</p></div>
          <button class="secondary-button" type="button" @click="goTo('review')"><span>↗</span>开始评审</button>
        </section>
        <p v-if="projectError" class="notice-banner"><span>!</span>{{ projectError }}</p>
        <section class="panel project-import-panel">
          <div class="panel-heading"><div><p class="eyebrow">{{ centralDeployment ? '登记项目' : '引入项目' }}</p><h2>{{ centralDeployment ? '配置评审仓库' : '连接本机 Git 仓库' }}</h2></div><span v-if="projectsLoading" class="scan-message">处理中...</span></div>
          <div class="project-import-form">
            <label class="field"><span>{{ centralDeployment ? '仓库标识' : '仓库路径' }}</span><input v-model="projectPathInput" type="text" :placeholder="centralDeployment ? '例如 acme/order-service' : 'D:\\development\\project\\repository'" /></label>
            <button class="primary-button project-import-button" type="button" :disabled="projectsLoading" @click="handleImportProject">{{ projectsLoading ? '处理中...' : (centralDeployment ? '登记项目' : '引入项目') }}</button>
          </div>
        </section>
        <section v-if="projects.length" class="project-list">
          <article v-for="project in visibleProjects" :key="project.id" class="panel project-item">
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
        <div v-if="projects.length" class="pagination" aria-label="项目分页"><button type="button" :disabled="projectPage <= 1" @click="projectPage--">上一页</button><span>第 {{ projectPage }} / {{ projectTotalPages }} 页</span><button type="button" :disabled="projectPage >= projectTotalPages" @click="projectPage++">下一页</button></div>
        <div v-else-if="!projectsLoading" class="empty-state history-empty"><span class="empty-icon">+</span><strong>还没有引入项目</strong><span>引入仓库后，可以在工作台选择项目并启用 Git Hook 评审。</span></div>
      </div>

      <div v-else-if="route === 'rules'" class="content-wrap">
        <section class="page-intro">
          <div><p class="eyebrow">本机规则配置</p><h1>评审规则中心</h1><p class="intro-copy">为全部本机项目或单独项目配置会送入评审 Agent 的检查规则。</p></div>
          <button class="secondary-button" type="button" @click="goTo('review')"><span>↗</span>开始评审</button>
        </section>
        <RuleCenter :projects="projects" />
      </div>

      <div v-else-if="route === 'users' && currentUser?.role === 'ADMIN'" class="content-wrap">
        <section class="page-intro">
          <div><p class="eyebrow">系统管理</p><h1>用户管理</h1><p class="intro-copy">管理用户访问、Local Client 设备 Token 与项目评审授权。</p></div>
        </section>
        <UserManagement :projects="projects" />
      </div>

      <div v-else-if="route === 'history'" class="content-wrap">
        <section class="page-intro">
          <div><p class="eyebrow">评审记录</p><h1>历史记录</h1><p class="intro-copy">查看数据库保存的评审任务。</p></div>
          <button class="secondary-button" type="button" @click="goTo('review')"><span>↗</span>新建评审</button>
        </section>
        <p v-if="historyError" class="notice-banner"><span>!</span>{{ historyError }}</p>
        <p v-else-if="historyLoading" class="scan-message">正在加载评审历史...</p>
        <section v-else-if="reviewHistory.length" class="history-list">
          <article v-for="item in visibleHistory" :key="item.id" class="history-item" tabindex="0" @click="openHistory(item.id)" @keydown.enter="openHistory(item.id)">
            <div class="history-item-main"><span class="repo-mark">{{ item.sourceType === 'GIT' ? 'GIT' : 'PR' }}</span><div><strong>{{ item.title }}</strong><span>{{ item.sourceType === 'GIT' ? '本地 Git' : '手动评审' }} · {{ item.repository }}</span></div></div>
            <div class="history-item-meta"><span class="status-badge" :class="`tone-${getStatusMeta(item.status).tone}`"><span class="status-dot"></span>{{ getStatusMeta(item.status).label }}</span><time>{{ item.completedAt || item.createdAt ? new Date(item.completedAt || item.createdAt).toLocaleString('zh-CN') : '-' }}</time></div>
          </article>
        </section>
        <div v-if="reviewHistory.length" class="pagination" aria-label="历史记录分页"><button type="button" :disabled="historyPage <= 1" @click="historyPage--">上一页</button><span>第 {{ historyPage }} / {{ historyTotalPages }} 页</span><button type="button" :disabled="historyPage >= historyTotalPages" @click="historyPage++">下一页</button></div>
        <div v-else class="empty-state history-empty"><span class="empty-icon">◷</span><strong>暂无评审记录</strong><span>提交一次本地 Git 或手动评审后，记录会显示在这里。</span><button class="primary-button" type="button" @click="goTo('review')">创建第一条评审</button></div>
      </div>

      <div v-else-if="route === 'tasks'" class="content-wrap">
        <section class="page-intro"><div><p class="eyebrow">异步执行</p><h1>评审任务</h1><p class="intro-copy">查看 Git Hook 和工作台提交的评审进度、失败原因及重试状态。</p></div><button class="secondary-button" type="button" @click="loadReviewTasks">刷新</button></section>
        <p v-if="tasksError" class="notice-banner"><span>!</span>{{ tasksError }}</p>
        <p v-else-if="tasksLoading" class="scan-message">正在加载评审任务...</p>
        <section v-else-if="reviewTasks.length" class="history-list"><article v-for="item in reviewTasks" :key="item.taskId" class="history-item task-list-item" tabindex="0" @click="openTask(item.taskId)" @keydown.enter="openTask(item.taskId)"><div class="history-item-main"><span class="repo-mark">TASK</span><div><strong>{{ item.title }}</strong><span>进度 {{ item.completedGroups }} / {{ item.totalGroups }} 组{{ item.errorMessage ? ` · ${item.errorMessage}` : '' }}</span></div></div><div class="history-item-meta"><span class="status-badge" :class="`tone-${taskStatusTone(item.status)}`"><span class="status-dot"></span>{{ taskStatusLabel(item.status) }}</span><button v-if="!['COMPLETED','FAILED','CANCELLED'].includes(item.status)" class="ghost-button" type="button" @click.stop="cancelTask(item.taskId)">取消</button><button v-if="['FAILED','CANCELLED'].includes(item.status)" class="ghost-button" type="button" @click.stop="retryTask(item.taskId)">重试</button></div></article></section>
        <div v-else class="empty-state history-empty"><strong>暂无异步评审任务</strong></div>
        <div class="pagination"><button type="button" :disabled="taskPage <= 0" @click="taskPage--; loadReviewTasks()">上一页</button><span>第 {{ taskPage + 1 }} / {{ taskTotalPages }} 页</span><button type="button" :disabled="taskPage + 1 >= taskTotalPages" @click="taskPage++; loadReviewTasks()">下一页</button></div>
      </div>

      <div v-else-if="route === 'task-detail'" class="content-wrap task-detail-page">
        <section class="page-intro">
          <div><p class="eyebrow">异步执行详情</p><h1>{{ taskDetail?.title || '评审任务详情' }}</h1><p class="intro-copy">查看分组执行状态、变更文件和已完成的评审问题。</p></div>
          <div class="page-intro-actions"><button class="secondary-button" type="button" @click="goTo('tasks')">返回任务列表</button><button v-if="taskDetail?.reviewId" class="primary-button" type="button" @click="openHistory(taskDetail.reviewId)">查看最终记录</button></div>
        </section>
        <p v-if="taskDetailError" class="notice-banner"><span>!</span>{{ taskDetailError }}</p>
        <p v-else-if="taskDetailLoading && !taskDetail" class="scan-message">正在加载任务详情...</p>
        <template v-else-if="taskDetail">
          <section class="detail-summary panel task-detail-summary">
            <div class="detail-summary-heading"><div><span class="eyebrow">任务 {{ taskDetail.taskId.slice(0, 8) }}</span><h2>{{ taskDetail.repositoryKey }}</h2></div><span class="status-badge" :class="`tone-${taskStatusTone(taskDetail.status)}`"><span class="status-dot"></span>{{ taskStatusLabel(taskDetail.status) }}</span></div>
            <div class="detail-meta-grid"><div><span>整体进度</span><strong>{{ taskDetail.completedGroups }} / {{ taskDetail.totalGroups }} 组</strong></div><div><span>当前分组</span><strong>{{ taskDetail.currentGroup ? `第 ${taskDetail.currentGroup} 组` : '-' }}</strong></div><div><span>重试次数</span><strong>{{ taskDetail.retryCount }}</strong></div><div><span>分支</span><strong>{{ taskDetail.branch || '-' }}</strong></div><div><span>提交</span><strong class="mono-value">{{ taskDetail.headCommit?.slice(0, 12) || '-' }}</strong></div><div><span>更新时间</span><strong>{{ taskDetail.completedAt || taskDetail.startedAt || taskDetail.createdAt ? new Date(taskDetail.completedAt || taskDetail.startedAt || taskDetail.createdAt).toLocaleString('zh-CN') : '-' }}</strong></div></div>
            <div v-if="taskDetail.errorMessage" class="task-error">{{ taskDetail.errorMessage }}</div>
          </section>
          <section class="task-groups"><div class="section-heading"><div><p class="eyebrow">执行拆分</p><h2>评审分组</h2></div><span class="muted-copy">{{ taskDetailFindings.length }} 个已发现问题</span></div><article v-for="group in taskDetail.groups" :key="group.groupNumber" class="task-group panel"><div class="task-group-heading"><div><strong>第 {{ group.groupNumber }} 组</strong><span>{{ group.files.length }} 个文件 · 尝试 {{ group.attemptCount }} 次</span></div><span class="status-badge" :class="`tone-${taskStatusTone(group.status)}`"><span class="status-dot"></span>{{ taskGroupStatusLabel(group.status) }}</span></div><div class="task-group-files"><code v-for="file in group.files" :key="file.path">{{ file.path }}</code></div><p v-if="group.errorMessage" class="task-error">{{ group.errorMessage }}</p><div v-if="group.findings.length && taskDetail.status !== 'COMPLETED'" class="task-group-findings"><strong>本组问题 {{ group.findings.length }} 个</strong><FindingList v-model="activeFilter" :findings="group.findings" /></div><p v-else-if="!group.findings.length && group.status === 'COMPLETED'" class="task-no-findings">本组未发现问题。</p></article></section>
          <FindingList v-if="taskDetailFindings.length" v-model="activeFilter" :findings="taskDetailFindings" />
        </template>
      </div>

      <div v-else class="content-wrap history-detail-page">
        <section class="page-intro">
          <div><p class="eyebrow">评审记录</p><h1>{{ task.title }}</h1><p class="intro-copy">查看这次评审任务的完整结果和代码问题。</p></div>
          <div class="page-intro-actions">
<!--            <button class="secondary-button" type="button" @click="goTo('history')">返回历史记录</button>-->
            <button class="primary-button" type="button" @click="goTo('review')">新建评审</button>
          </div>
        </section>
        <p v-if="historyError" class="notice-banner"><span>!</span>{{ historyError }}</p>
        <p v-else-if="historyDetailLoading" class="scan-message">正在加载评审详情...</p>
        <template v-else>
          <section class="detail-summary panel">
            <div class="detail-summary-heading"><div><span class="eyebrow">{{ localGitReview ? '本地 Git' : '手动评审' }}</span><h2>{{ task.repository }}</h2></div><span class="status-badge" :class="`tone-${statusMeta.tone}`"><span class="status-dot"></span>{{ statusMeta.label }}</span></div>
            <div class="detail-meta-grid">
              <div><span>风险评分</span><strong>{{ score }}/100</strong></div>
              <div><span>发现问题</span><strong>{{ counts.total }}</strong></div>
              <div><span>高风险问题</span><strong>{{ counts.high + counts.critical }}</strong></div>
              <div><span>评审模型</span><strong>{{ task.modelName || '未知' }}</strong></div>
              <div><span>分支</span><strong>{{ task.branch || '-' }}</strong></div>
              <div><span>提交</span><strong class="mono-value">{{ task.headCommit?.slice(0, 12) || '-' }}</strong></div>
            </div>
          </section>
          <div class="report-bar"><div><span class="report-status" :class="`tone-${statusMeta.tone}`"><span class="status-dot"></span>{{ statusMeta.label }}</span><span class="report-updated">完成于 · {{ task.completedAt || task.createdAt ? new Date(task.completedAt || task.createdAt).toLocaleString('zh-CN') : '-' }}</span></div><button class="ghost-button" type="button" @click="exportMarkdown">导出 Markdown <span>↓</span></button></div>
          <section v-if="task.files?.length" class="detail-files panel"><div class="panel-heading"><div><p class="eyebrow">变更范围</p><h2>评审变更文件</h2></div><span>{{ task.files.length }} 个文件</span></div><div class="detail-file-list"><div v-for="file in visibleDetailFiles" :key="file.path" class="detail-file-row"><code>{{ file.path }}</code><span class="git-status">{{ file.gitStatus || 'CHANGED' }}</span><span class="file-stats"><b>+{{ file.additions ?? 0 }}</b><i>-{{ file.deletions ?? 0 }}</i></span></div></div><div class="pagination" aria-label="变更文件分页"><button type="button" :disabled="detailFilesPage <= 1" @click="detailFilesPage--">上一页</button><span>第 {{ detailFilesPage }} / {{ detailFilesTotalPages }} 页</span><button type="button" :disabled="detailFilesPage >= detailFilesTotalPages" @click="detailFilesPage++">下一页</button></div></section>
          <FindingList v-model="activeFilter" :findings="task.findings" />
        </template>
      </div>
    </main>
  </div>
</template>
