const routes = new Set(['review', 'projects', 'rules', 'users', 'history', 'tasks', 'observability']);

export function getRoute(hash = '') {
  const route = hash.replace(/^#/, '').trim().replace(/\/+$/, '');
  if (/^history\/[^/]+$/.test(route)) return 'history-detail';
  if (/^tasks\/[^/]+$/.test(route)) return 'task-detail';
  return routes.has(route) ? route : 'review';
}

export function getHistoryId(hash = '') {
  const route = hash.replace(/^#/, '').trim();
  const match = route.match(/^history\/([^/]+)$/);
  return match ? decodeURIComponent(match[1]) : null;
}

export function getTaskId(hash = '') {
  const route = hash.replace(/^#/, '').trim();
  const match = route.match(/^tasks\/([^/]+)$/);
  return match ? decodeURIComponent(match[1]) : null;
}
