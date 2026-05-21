import { describe, it, expect, vi, afterEach } from 'vitest'

function mockFetch(body: unknown, ok = true, status = 200) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
    ok, status,
    json: () => Promise.resolve(body),
  }))
}

describe('api/auth', () => {
  afterEach(() => vi.restoreAllMocks())

  it('register returns created user', async () => {
    const user = { id: 'u1', username: 'alice', role: 'READER' }
    mockFetch(user, true, 201)
    const { authApi } = await import('../../api/auth')
    const result = await authApi.register({ username: 'alice', password: 'Secret1!' })
    expect(result.username).toBe('alice')
    expect(result.role).toBe('READER')
  })

  it('login resolves on 200', async () => {
    mockFetch(undefined, true, 200)
    const { authApi } = await import('../../api/auth')
    await expect(authApi.login({ username: 'alice', password: 'Secret1!' })).resolves.not.toThrow()
  })

  it('logout resolves on 204', async () => {
    mockFetch(undefined, true, 204)
    const { authApi } = await import('../../api/auth')
    await expect(authApi.logout()).resolves.toBeUndefined()
  })

  it('profile returns current user', async () => {
    const user = { id: 'u1', username: 'alice', role: 'ADMIN' }
    mockFetch(user)
    const { authApi } = await import('../../api/auth')
    const result = await authApi.profile()
    expect(result.role).toBe('ADMIN')
  })

  it('register throws on 409 conflict', async () => {
    mockFetch({ title: 'Username already taken' }, false, 409)
    const { authApi } = await import('../../api/auth')
    await expect(authApi.register({ username: 'alice', password: 'Secret1!' })).rejects.toThrow()
  })

  it('login throws on 401 invalid credentials', async () => {
    mockFetch({ title: 'Invalid credentials' }, false, 401)
    const { authApi } = await import('../../api/auth')
    await expect(authApi.login({ username: 'alice', password: 'wrong' })).rejects.toThrow()
  })
})
