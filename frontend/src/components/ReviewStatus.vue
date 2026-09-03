<script setup>
import { computed } from 'vue';
import { getStatusMeta } from '../reviewState.js';

const props = defineProps({
  task: { type: Object, required: true },
  localGit: Boolean
});
const status = computed(() => getStatusMeta(props.task.status));
const timeline = [
  { key: 'PENDING', label: '任务已接收' },
  { key: 'PROCESSING', label: 'AI 正在分析' },
  { key: 'COMPLETED', label: '报告已生成' }
];
const statusOrder = { PENDING: 0, PROCESSING: 1, COMPLETED: 2, FAILED: 2 };

function isStepComplete(key) {
  return props.task.status !== 'FAILED' && statusOrder[props.task.status] >= statusOrder[key];
}
</script>

<template>
  <section class="panel status-panel">
    <div class="panel-heading">
      <div>
        <p class="eyebrow">RUN STATUS</p>
        <h2>评审进度</h2>
      </div>
      <span class="status-badge" :class="`tone-${status.tone}`">
        <span class="status-dot"></span>{{ status.label }}
      </span>
    </div>

    <div class="task-reference">
      <span class="repo-mark">GH</span>
      <div>
        <strong>{{ task.repository }}</strong>
        <span>{{ localGit ? 'Local Git' : `PR #${task.pullRequestNumber}` }} · {{ task.title }}</span>
      </div>
    </div>

    <div class="timeline">
      <div v-for="item in timeline" :key="item.key" class="timeline-item" :class="{ active: isStepComplete(item.key), current: task.status === item.key }">
        <span class="timeline-marker">{{ isStepComplete(item.key) ? '✓' : '' }}</span>
        <span>{{ item.label }}</span>
      </div>
    </div>

    <p v-if="task.status === 'FAILED'" class="form-error">{{ task.error || '评审任务执行失败' }}</p>
    <p v-else class="muted-copy">任务 ID · {{ task.id }}</p>
  </section>
</template>
