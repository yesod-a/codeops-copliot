<script setup>
import { computed, onBeforeUnmount, ref } from 'vue';
import { getReview, submitReview } from './api/reviewApi.js';
import FindingList from './components/FindingList.vue';
import ReviewForm from './components/ReviewForm.vue';
import ReviewStatus from './components/ReviewStatus.vue';
import { calculateRiskScore, demoTask, getFindingCounts, getStatusMeta } from './reviewState.js';

const task = ref(structuredClone(demoTask));
const activeFilter = ref('ALL');
const submitting = ref(false);
const connectionMessage = ref('本地示例');
const notice = ref('');
let pollingTimer;

const counts = computed(() => getFindingCounts(task.value.findings));
const score = computed(() => calculateRiskScore(task.value.findings));
const scoreLabel = computed(() => score.value >= 80 ? '状态良好' : score.value >= 60 ? '需要关注' : '高风险');
const statusMeta = computed(() => getStatusMeta(task.value.status));

function loadDemo() {
  task.value = structuredClone(demoTask);
  activeFilter.value = 'ALL';
  connectionMessage.value = '本地示例';
  notice.value = '';
}

async function handleSubmit(payload) {
  window.clearTimeout(pollingTimer);
  submitting.value = true;
  notice.value = '';
  try {
    const created = await submitReview(payload);
    task.value = created;
    connectionMessage.value = 'API 已连接';
    await poll(created.id, 0);
  } catch (error) {
    task.value = makeLocalPreview(payload);
    connectionMessage.value = '本地预览';
    notice.value = '后端暂不可用，当前展示本地预览结果';
  } finally {
    submitting.value = false;
  }
}

async function poll(id, attempt) {
  if (attempt >= 10 || ['COMPLETED', 'FAILED'].includes(task.value.status)) return;
  pollingTimer = window.setTimeout(async () => {
    try {
      task.value = await getReview(id);
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
  const header = `# ${task.value.title}\n\n- Repository: ${task.value.repository}\n- Pull Request: #${task.value.pullRequestNumber}\n- Status: ${statusMeta.value.label}\n- Risk score: ${score.value}/100\n`;
  const body = task.value.findings.length
    ? task.value.findings.map((finding, index) => [
      `## ${index + 1}. [${finding.severity}] ${finding.message}`,
      `- Category: ${finding.category}`,
      `- Location: ${finding.file}:${finding.line}`,
      `- Confidence: ${Math.round((finding.confidence ?? 0) * 100)}%`,
      '',
      `**Evidence**\n\n\`\`\`java\n${finding.evidence}\n\`\`\``,
      '',
      `**Suggestion**\n\n${finding.suggestion}`
    ].join('\n')).join('\n\n')
    : 'No findings were reported.';
  const blob = new Blob([`${header}\n${body}\n`], { type: 'text/markdown;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `${task.value.repository.replace(/[^a-z0-9]+/gi, '-')}-pr-${task.value.pullRequestNumber}-review.md`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

onBeforeUnmount(() => window.clearTimeout(pollingTimer));
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand-lockup">
        <div class="brand-mark"><span></span><span></span><span></span></div>
        <div><strong>CodeOps</strong><span>Copilot</span></div>
      </div>
      <nav class="side-nav" aria-label="主导航">
        <p class="nav-label">WORKSPACE</p>
        <a class="nav-item active" href="#review"><span class="nav-icon">◈</span>评审工作台</a>
        <a class="nav-item" href="#projects"><span class="nav-icon">▦</span>项目</a>
        <a class="nav-item" href="#history"><span class="nav-icon">◷</span>历史记录</a>
        <p class="nav-label nav-label-spaced">SYSTEM</p>
        <a class="nav-item" href="#settings"><span class="nav-icon">⚙</span>设置</a>
      </nav>
      <div class="sidebar-footer">
        <div class="model-card">
          <span class="pulse-dot"></span>
          <div><span>REVIEW ENGINE</span><strong>Simulated AI v0.1</strong></div>
        </div>
        <div class="user-card"><span class="avatar">YL</span><div><strong>Yu Li</strong><span>Developer</span></div><span class="more-icon">•••</span></div>
      </div>
    </aside>

    <main class="main-content" id="review">
      <header class="topbar">
        <div class="breadcrumb"><span>Workspace</span><b>/</b><strong>Review desk</strong></div>
        <div class="topbar-actions"><span class="connection-pill"><span class="pulse-dot"></span>{{ connectionMessage }}</span><button class="icon-button" title="帮助">?</button><span class="avatar small">YL</span></div>
      </header>

      <div class="content-wrap">
        <section class="page-intro">
          <div><p class="eyebrow">WED · 02 SEP 2026</p><h1>代码评审工作台</h1><p class="intro-copy">把每一次 Pull Request，变成可追踪的工程质量信号。</p></div>
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
          <ReviewForm :submitting="submitting" @submit="handleSubmit" @load-demo="loadDemo" />
          <ReviewStatus :task="task" />
        </section>

        <div class="report-bar"><div><span class="report-status" :class="`tone-${statusMeta.tone}`"><span class="status-dot"></span>{{ statusMeta.label }}</span><span class="report-updated">最后更新 · {{ task.updatedAt ? new Date(task.updatedAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) : '--:--' }}</span></div><button class="ghost-button" type="button" @click="exportMarkdown">导出 Markdown <span>↓</span></button></div>
        <FindingList v-model="activeFilter" :findings="task.findings" />
      </div>
    </main>
  </div>
</template>
