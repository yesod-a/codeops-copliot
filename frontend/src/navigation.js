const routes = new Set(['review', 'projects', 'history']);

export function getRoute(hash = '') {
  const route = hash.replace(/^#/, '').trim();
  return routes.has(route) ? route : 'review';
}
