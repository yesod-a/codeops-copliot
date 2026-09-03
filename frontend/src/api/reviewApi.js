const API_BASE = import.meta.env.VITE_API_BASE ?? '';

async function request(path, options = {}) {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), 2000);
  try {
    const response = await fetch(`${API_BASE}${path}`, {
      headers: { 'Content-Type': 'application/json', ...(options.headers ?? {}) },
      signal: controller.signal,
      ...options
    });

    if (!response.ok) {
      const errorBody = await response.json().catch(() => ({}));
      throw new Error(errorBody.message ?? `Request failed with ${response.status}`);
    }

    return response.json();
  } finally {
    window.clearTimeout(timeout);
  }
}

export function submitReview(payload) {
  return request('/api/reviews', { method: 'POST', body: JSON.stringify(payload) });
}

export function getReview(id) {
  return request(`/api/reviews/${id}`);
}

export function scanRepository(payload) {
  return request('/api/repositories/scan', { method: 'POST', body: JSON.stringify(payload) });
}

export function submitGitReview(payload) {
  return request('/api/reviews/from-git', { method: 'POST', body: JSON.stringify(payload) });
}
