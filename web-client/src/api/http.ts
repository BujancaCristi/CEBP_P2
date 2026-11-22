/** Shared fetch wrapper. Non-2xx answers throw an Error whose message is the problem's detail (RFC 9457). */
export async function request<T>(url: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(url, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init.headers ?? {}) },
  });
  if (!response.ok) {
    const text = await response.text();
    let message = `${response.status} ${response.statusText}`;
    try {
      const body = JSON.parse(text);
      message = body.detail ?? body.message ?? body.title ?? message;
    } catch {
      if (text) message = text;
    }
    if (response.status === 401 && !url.includes('/api/auth/')) {
      localStorage.removeItem('exchange.token');
      localStorage.removeItem('exchange.user');
      location.assign(`/login?returnTo=${encodeURIComponent(location.pathname)}`);
    }
    throw new Error(`${message} (${response.status})`);
  }
  return response.status === 204 ? (undefined as T) : (response.json() as Promise<T>);
}
