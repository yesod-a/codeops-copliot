const API_BASE = import.meta.env.VITE_API_BASE ?? '';

async function request(path, options = {}) {
  const { timeoutMs = 2000, ...fetchOptions } = options;
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(`${API_BASE}${path}`, {
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

export function scanRepository(payload) {
  return request('/api/repositories/scan', { method: 'POST', body: JSON.stringify(payload) });
}

export function submitAiReview(payload) {
  return request('/api/ai/review', {
    method: 'POST',
    timeoutMs: 120000,
    body: JSON.stringify({
      repository: payload.repositoryPath ?? payload.repository,
      title: payload.title,
      files: payload.files.map(({ path, content }) => ({ path, content: content ?? '' }))
    })
  });
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
