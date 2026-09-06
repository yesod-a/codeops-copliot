const routes = new Set(['review', 'projects', 'rules', 'users', 'history', 'tasks']);

export function getRoute(hash = '') {
  const route = hash.replace(/^#/, '').trim().replace(/\/+$/, '');
  if (/^history\/[^/]+$/.test(route)) return 'history-detail';
  return routes.has(route) ? route : 'review';
}

export function getHistoryId(hash = '') {
  const route = hash.replace(/^#/, '').trim();
  const match = route.match(/^history\/([^/]+)$/);
  return match ? decodeURIComponent(match[1]) : null;
}
