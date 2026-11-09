// Same origin in production (Spring Boot serves the dashboard); Vite proxies /api in development.
const BASE = import.meta.env.VITE_API_URL || '';

async function request(method, path, body) {
  const res = await fetch(BASE + path, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = { error: text };
  }
  if (!res.ok) {
    const msg = data?.error || `Request failed (${res.status})`;
    const err = new Error(msg);
    err.details = data?.details || data?.paypalResponse;
    err.data = data;
    throw err;
  }
  return data;
}

export const api = {
  listDisputes: () => request('GET', '/api/disputes'),
  getDispute: (id) => request('GET', `/api/disputes/${encodeURIComponent(id)}`),
  syncAll: () => request('POST', '/api/disputes/sync'),
  analyze: (id, preview = false) =>
    request('POST', `/api/disputes/${encodeURIComponent(id)}/analyze${preview ? '?preview=true' : ''}`),
  approve: (id, body) => request('POST', `/api/disputes/${encodeURIComponent(id)}/approve`, body),
  listOrders: () => request('GET', '/api/store/orders'),
  createOrder: (body) => request('POST', '/api/store/orders', body),
  captureOrder: (paypalOrderId) =>
    request('POST', `/api/store/orders/${encodeURIComponent(paypalOrderId)}/capture`),
};
