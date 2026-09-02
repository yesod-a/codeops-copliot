<script setup>
import { computed } from 'vue';
import { filterFindings, severityMeta } from '../reviewState.js';

const props = defineProps({ findings: { type: Array, default: () => [] } });
const activeFilter = defineModel({ default: 'ALL' });
const filters = [
  { key: 'ALL', label: '全部' },
  { key: 'CRITICAL', label: '严重' },
  { key: 'HIGH', label: '高风险' },
  { key: 'MEDIUM', label: '中风险' },
  { key: 'LOW', label: '低风险' }
];
const visibleFindings = computed(() => filterFindings(props.findings, activeFilter.value));

function formatConfidence(value) {
  return `${Math.round((value ?? 0) * 100)}%`;
}
</script>

<template>
  <section class="report-section">
    <div class="section-heading report-heading">
      <div>
        <p class="eyebrow">REVIEW FINDINGS</p>
        <h2>问题清单</h2>
      </div>
      <div class="filter-tabs" role="tablist" aria-label="按严重程度筛选">
        <button v-for="filter in filters" :key="filter.key" type="button" :class="{ selected: activeFilter === filter.key }" @click="activeFilter = filter.key">
          {{ filter.label }}
        </button>
      </div>
    </div>

    <div v-if="visibleFindings.length" class="finding-list">
      <article v-for="(finding, index) in visibleFindings" :key="`${finding.file}-${finding.line}-${index}`" class="finding-card">
        <div class="finding-topline">
          <span class="severity-chip" :class="severityMeta[finding.severity]?.className">{{ severityMeta[finding.severity]?.label || finding.severity }}</span>
          <span class="finding-category">{{ finding.category }}</span>
          <span class="confidence">置信度 {{ formatConfidence(finding.confidence) }}</span>
        </div>
        <h3>{{ finding.message }}</h3>
        <p class="file-reference"><span class="file-icon">ƒ</span>{{ finding.file }}<span class="line-number">L{{ finding.line }}</span></p>
        <div class="evidence-block">
          <span class="evidence-label">EVIDENCE</span>
          <code>{{ finding.evidence }}</code>
        </div>
        <div class="suggestion-block">
          <span class="suggestion-icon">↳</span>
          <p>{{ finding.suggestion }}</p>
        </div>
      </article>
    </div>
    <div v-else class="empty-state">
      <span class="empty-icon">✓</span>
      <strong>这个筛选条件下没有问题</strong>
      <span>当前报告保持清洁</span>
    </div>
  </section>
</template>
