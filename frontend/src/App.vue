<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { getReview, scanRepository, submitGitReview, submitReview } from './api/reviewApi.js';
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
const reviewHistory = ref(loadReviewHistory());
let pollingTimer;

function loadReviewHistory() {
  try {
    return JSON.parse(window.localStorage.getItem('codeops-review-history') || '[]');
  } catch {
    return [];
  }
}

function saveReviewHistory() {
  window.localStorage.setItem('codeops-review-history', JSON.stringify(reviewHistory.value.slice(0, 20)));
}

function rememberReview(review) {
  const summary = {
    id: review.id,
    title: review.title,
    repository: review.repository,
    pullRequestNumber: review.pullRequestNumber,
    status: review.status,
    updatedAt: review.updatedAt,
    localGit: localGitReview.value
  };
  reviewHistory.value = [summary, ...reviewHistory.value.filter((item) => item.id !== summary.id)].slice(0, 20);
  saveReviewHistory();
}

function syncRoute() {
  route.value = getRoute(window.location.hash);
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
    const created = payload.mode === 'git'
      ? await submitGitReview(payload)
      : await submitReview(payload);
    task.value = created;
    rememberReview(created);
    connectionMessage.value = 'API 已连接';
    await poll(created.id, 0);
  } catch (error) {
    if (payload.mode === 'git') {
      connectionMessage.value = '后端不可用';
      notice.value = `本地 Git 评审需要 Java 后端：${error.message || '请求失败'}`;
    } else {
      task.value = makeLocalPreview(payload);
      connectionMessage.value = '本地预览';
      notice.value = '后端暂不可用，当前展示本地预览结果';
    }
  } finally {
    submitting.value = false;
  }
}

async function poll(id, attempt) {
  if (attempt >= 10 || ['COMPLETED', 'FAILED'].includes(task.value.status)) return;
  pollingTimer = window.setTimeout(async () => {
    try {
      task.value = await getReview(id);
      rememberReview(task.value);
      await poll(id, attempt + 1);
    } catch (error) {
      notice.value = '获取评审进度失败，请稍后重试';
    }
  }, 700);
}

function makeLocalPreview(payload) {
  const source = payload.files[0];
  const findings = [];
  if (/authorization|password|secret|api[_-]?key/i.test(source.content)) {
    findings.push({ category: 'SECURITY', severity: 'HIGH', file: source.path, line: 1, message: '代码中可能直接处理敏感认证信息。', suggestion: '使用 Spring Security 的认证主体和安全配置，避免在业务代码中传递原始凭据。', evidence: source.content.split('\n')[0], confidence: 0.96 });
  }
  if (/TODO|FIXME/.test(source.content)) {
    findings.push({ category: 'MAINTAINABILITY', severity: 'LOW', file: source.path, line: 1, message: '变更中包含未完成的 TODO 或 FIXME 标记。', suggestion: '在合并前完成实现，或创建可追踪的 Issue。', evidence: source.content.split('\n').find((line) => /TODO|FIXME/.test(line)) || '', confidence: 0.94 });
  }
  return {
    ...structuredClone(demoTask),
    id: `local-${Date.now()}`,
    repository: payload.repository,
    pullRequestNumber: payload.pullRequestNumber,
    title: payload.title,
    status: 'COMPLETED',
    updatedAt: new Date().toISOString(),
    findings
  };
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

onMounted(() => window.addEventListener('hashchange', syncRoute));
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
          <div><span>评审引擎</span><strong>模拟 AI v0.1</strong></div>
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
            @scan="handleScan"
            @submit="handleSubmit"
            @load-demo="loadDemo"
          />
          <ReviewStatus :task="task" :local-git="localGitReview" />
        </section>

        <div class="report-bar"><div><span class="report-status" :class="`tone-${statusMeta.tone}`"><span class="status-dot"></span>{{ statusMeta.label }}</span><span class="report-updated">最后更新 · {{ task.updatedAt ? new Date(task.updatedAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) : '--:--' }}</span></div><button class="ghost-button" type="button" @click="exportMarkdown">导出 Markdown <span>↓</span></button></div>
        <FindingList v-model="activeFilter" :findings="task.findings" />
      </div>

      <div v-else-if="route === 'projects'" class="content-wrap">
        <section class="page-intro">
          <div><p class="eyebrow">项目管理</p><h1>本地项目</h1><p class="intro-copy">管理最近扫描过的本地 Git 仓库。</p></div>
          <button class="secondary-button" type="button" @click="goTo('review')"><span>↗</span>开始评审</button>
        </section>
        <section class="projects-layout">
          <div class="panel project-summary-panel">
            <div class="panel-heading"><div><p class="eyebrow">当前项目</p><h2>{{ scanResult?.repositoryPath || '尚未扫描项目' }}</h2></div><span v-if="scanResult" class="status-badge tone-success"><span class="status-dot"></span>已连接</span></div>
            <div v-if="scanResult" class="project-detail-grid">
              <div><span>当前分支</span><strong>{{ scanResult.branch || '未命名分支' }}</strong></div>
              <div><span>HEAD 提交</span><strong class="mono-value">{{ scanResult.headCommit?.slice(0, 8) || '-' }}</strong></div>
              <div><span>变更文件</span><strong>{{ scanResult.files?.length || 0 }}</strong></div>
            </div>
            <div v-else class="empty-state"><span class="empty-icon">+</span><strong>还没有扫描本地项目</strong><span>前往评审工作台输入仓库路径并扫描变更。</span></div>
          </div>
          <div class="panel"><div class="panel-heading"><div><p class="eyebrow">使用说明</p><h2>从本地仓库开始</h2></div></div><ol class="project-steps"><li>输入本机上的 Git 仓库路径</li><li>扫描工作区或基准提交的变更</li><li>选择文件并提交代码评审</li></ol><button class="primary-button" type="button" @click="goTo('review')">前往评审工作台</button></div>
        </section>
      </div>

      <div v-else class="content-wrap">
        <section class="page-intro">
          <div><p class="eyebrow">评审记录</p><h1>历史记录</h1><p class="intro-copy">查看当前浏览器保存的评审任务。</p></div>
          <button class="secondary-button" type="button" @click="goTo('review')"><span>↗</span>新建评审</button>
        </section>
        <section v-if="reviewHistory.length" class="history-list">
          <article v-for="item in reviewHistory" :key="item.id" class="history-item">
            <div class="history-item-main"><span class="repo-mark">{{ item.localGit ? 'GIT' : 'PR' }}</span><div><strong>{{ item.title }}</strong><span>{{ item.localGit ? '本地 Git' : `拉取请求 #${item.pullRequestNumber}` }} · {{ item.repository }}</span></div></div>
            <div class="history-item-meta"><span class="status-badge" :class="`tone-${getStatusMeta(item.status).tone}`"><span class="status-dot"></span>{{ getStatusMeta(item.status).label }}</span><time>{{ item.updatedAt ? new Date(item.updatedAt).toLocaleString('zh-CN') : '-' }}</time></div>
          </article>
        </section>
        <div v-else class="empty-state history-empty"><span class="empty-icon">◷</span><strong>暂无评审记录</strong><span>提交一次本地 Git 或手动评审后，记录会显示在这里。</span><button class="primary-button" type="button" @click="goTo('review')">创建第一条评审</button></div>
      </div>
    </main>
  </div>
</template>
