import { describe, it, expect, vi, afterEach } from 'vitest'

const baseExec = { id: 'e1', type: 'WORKFLOW', status: 'SUCCESS', startedAt: '2026-01-01T09:00:00', finishedAt: null }

function mockFetch(body: unknown, ok = true, status = 200) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
    ok, status,
    json: () => Promise.resolve(body),
  } as Response))
}

describe('api/executions', () => {
  afterEach(() => vi.restoreAllMocks())

  it('getById returns execution', async () => {
    mockFetch(baseExec)
    const { executionApi } = await import('../../api/executions')
    const result = await executionApi.getById('e1')
    expect(result.status).toBe('SUCCESS')
  })

  it('cancel sends POST to cancel endpoint', async () => {
    mockFetch(undefined, true, 204)
    const { executionApi } = await import('../../api/executions')
    await expect(executionApi.cancel('e1')).resolves.toBeUndefined()
  })

  it('getById throws on 404', async () => {
    mockFetch({ title: 'Not found' }, false, 404)
    const { executionApi } = await import('../../api/executions')
    await expect(executionApi.getById('missing')).rejects.toThrow()
  })
})
