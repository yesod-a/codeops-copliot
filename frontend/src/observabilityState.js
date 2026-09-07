export function percentage(value, total) {
  if (!total) return '0%';
  return `${Math.round((Number(value ?? 0) / Number(total)) * 100)}%`;
}

export function formatDuration(value) {
  const ms = Number(value ?? 0);
  if (ms < 1000) return `${Math.round(ms)} ms`;
  return `${(ms / 1000).toFixed(1)} s`;
}

export function formatTokens(value) {
  return Number(value ?? 0).toLocaleString('zh-CN');
}

export function formatCost(value) {
  if (value == null) return '-';
  return `$${Number(value).toFixed(4)}`;
}
