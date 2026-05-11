/**
 * Base for API requests
 * **/

const BASE = '/api'

enum Method {
  GET = 'GET',
  POST = 'POST',
  PUT = 'PUT',
  PATCH = 'PATCH',
  DELETE = 'DELETE',
}

async function request<T>(
  method: Method,
  path: string,
  body?: unknown,
): Promise<T> {

  const res = await fetch(`${BASE}${path}`, {
    method,
    credentials: 'include',
    headers: body ? { 'Content-Type': 'application/json' } : {},
    body: body ? JSON.stringify(body) : undefined,
  })

  if (res.status === 204) return undefined as T

  const data = await res.json().catch(() => undefined)

  if (!res.ok) {
    const message = data?.message ?? data?.detail ?? res.statusText
    throw new Error(message)
  }

  return data as T
}

export const api = {
  get: <T>(path: string) => request<T>(Method.GET, path),
  post: <T>(path: string, body: unknown) => request<T>(Method.POST, path, body),
  put: <T>(path: string, body: unknown) => request<T>(Method.PUT, path, body),
  patch: <T>(path: string, body: unknown) => request<T>(Method.PATCH, path, body),
  delete: <T>(path: string) => request<T>(Method.DELETE, path),
}

