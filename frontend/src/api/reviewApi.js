const API_BASE = import.meta.env.VITE_API_BASE ?? '';

export function createReviewTaskEventSource(taskId) {
  return new EventSource(`${API_BASE}/api/review-tasks/${encodeURIComponent(taskId)}/events`, {
    withCredentials: true
  });
}

async function request(path, options = {}) {
  const { timeoutMs = 2000, ...fetchOptions } = options;
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(`${API_BASE}${path}`, {
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', ...(fetchOptions.headers ?? {}) },
      signal: controller.signal,
      ...fetchOptions
    });

    if (!response.ok) {
      const errorBody = await response.json().catch(() => ({}));
      throw new Error(errorBody.message ?? `Request failed with ${response.status}`);
    }

    if (response.status === 204) return null;
    return response.json();
  } finally {
    window.clearTimeout(timeout);
  }
}

export function login(username, password) {
  return request('/api/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) });
}

export function logout() {
  return request('/api/auth/logout', { method: 'POST' });
}

export function getCurrentUser() {
  return request('/api/auth/me');
}

export function listManagedUsers() {
  return request('/api/management/users');
}

export function createManagedUser(user) {
  return request('/api/management/users', { method: 'POST', body: JSON.stringify(user) });
}

export function updateManagedUser(userId, user) {
  return request(`/api/management/users/${encodeURIComponent(userId)}`, { method: 'PUT', body: JSON.stringify(user) });
}

export function createManagedAgent(userId, name) {
  return request(`/api/management/users/${encodeURIComponent(userId)}/agents`, {
    method: 'POST', body: JSON.stringify({ name })
  });
}

export function setManagedAgentActive(userId, agentId, active) {
  return request(`/api/management/users/${encodeURIComponent(userId)}/agents/${encodeURIComponent(agentId)}`, {
    method: 'PATCH', body: JSON.stringify({ active })
  });
}

export function updateManagedProjectMembership(projectId, userId, role) {
  return request(`/api/management/projects/${encodeURIComponent(projectId)}/members/${encodeURIComponent(userId)}`, {
    method: 'PUT', body: JSON.stringify({ role })
  });
}

export function removeManagedProjectMembership(projectId, userId) {
  return request(`/api/management/projects/${encodeURIComponent(projectId)}/members/${encodeURIComponent(userId)}`, {
    method: 'DELETE'
  });
}

export function scanRepository(payload) {
  return request('/api/repositories/scan', { method: 'POST', body: JSON.stringify(payload) });
}

export function readSelectedGitFiles(payload) {
  return request('/api/repositories/read-selected', { method: 'POST', body: JSON.stringify(payload) });
}

export function listProjects() {
  return request('/api/projects');
}

export function importProject(repositoryPath) {
  return request('/api/projects/import', {
    method: 'POST',
    body: JSON.stringify({ repositoryPath })
  });
}

export function registerCentralProject(name, remoteUrl) {
  return request('/api/projects/register', {
    method: 'POST',
    body: JSON.stringify({ name, remoteUrl })
  });
}

export function updateProjectPolicy(id, policy) {
  return request(`/api/projects/${id}/policy`, {
    method: 'PUT',
    body: JSON.stringify(policy)
  });
}

export function deleteProject(id) {
  return request(`/api/projects/${id}`, { method: 'DELETE' });
}

export function listGlobalRules() {
  return request('/api/rules/global');
}

export function createGlobalRule(rule) {
  return request('/api/rules/global', { method: 'POST', body: JSON.stringify(rule) });
}

export function updateGlobalRule(id, rule) {
  return request(`/api/rules/global/${id}`, { method: 'PUT', body: JSON.stringify(rule) });
}

export function deleteGlobalRule(id) {
  return request(`/api/rules/global/${id}`, { method: 'DELETE' });
}

export function listProjectRules(projectId) {
  return request(`/api/projects/${projectId}/rules`);
}

export function createProjectRule(projectId, rule) {
  return request(`/api/projects/${projectId}/rules`, { method: 'POST', body: JSON.stringify(rule) });
}

export function updateProjectRule(projectId, ruleId, rule) {
  return request(`/api/projects/${projectId}/rules/${ruleId}`, { method: 'PUT', body: JSON.stringify(rule) });
}

export function deleteProjectRule(projectId, ruleId) {
  return request(`/api/projects/${projectId}/rules/${ruleId}`, { method: 'DELETE' });
}

export function previewProjectRules(projectId, paths) {
  return request(`/api/projects/${projectId}/rules/preview`, { method: 'POST', body: JSON.stringify({ paths }) });
}

export function submitAiReview(payload) {
  return request('/api/ai/review', {
    method: 'POST',
    timeoutMs: 600000,
    body: JSON.stringify({
      repository: payload.repositoryPath ?? payload.repository,
      title: payload.title,
      files: payload.files.map(({ path, content }) => ({ path, content: content ?? '' }))
    })
  });
}

export function listReviewTasks({ page = 0, size = 10 } = {}) {
  return request(`/api/review-tasks?page=${page}&size=${size}`, { timeoutMs: 10000 });
}

export function getReviewTask(taskId) {
  return request(`/api/review-tasks/${encodeURIComponent(taskId)}`, { timeoutMs: 10000 });
}

export function cancelReviewTask(taskId) {
  return request(`/api/review-tasks/${encodeURIComponent(taskId)}/cancel`, { method: 'POST', timeoutMs: 10000 });
}

export function retryReviewTask(taskId) {
  return request(`/api/review-tasks/${encodeURIComponent(taskId)}/retry`, { method: 'POST', timeoutMs: 10000 });
}

export function saveReview(payload) {
  return request('/api/reviews', {
    method: 'POST',
    timeoutMs: 10000,
    body: JSON.stringify(payload)
  });
}

export function listReviews({ limit = 20, offset = 0 } = {}) {
  const query = new URLSearchParams({ limit: String(limit), offset: String(offset) });
  return request(`/api/reviews?${query}`);
}

export function getReviewDetails(id) {
  return request(`/api/reviews/${id}`);
}

export function deleteReview(id) {
  return request(`/api/reviews/${id}`, { method: 'DELETE' });
}

export function getAiHealth() {
  return request('/api/ai/health');
}

export function getObservabilityOverview(params = {}) {
  const query = new URLSearchParams();
  if (params.projectId) query.set('projectId', params.projectId);
  if (params.from) query.set('from', params.from);
  if (params.to) query.set('to', params.to);
  return request(`/api/observability/overview?${query}`, { timeoutMs: 10000 });
}

export function getObservabilityTimeseries(params = {}) {
  const query = new URLSearchParams({ metric: params.metric ?? 'tasks' });
  if (params.projectId) query.set('projectId', params.projectId);
  if (params.from) query.set('from', params.from);
  if (params.to) query.set('to', params.to);
  return request(`/api/observability/timeseries?${query}`, { timeoutMs: 10000 });
}

export function getObservabilityQueue() {
  return request('/api/observability/queue', { timeoutMs: 10000 });
}

export function getTaskExecution(taskId) {
  return request(`/api/review-tasks/${encodeURIComponent(taskId)}/execution`, { timeoutMs: 10000 });
}
