import { describe, it, expect, vi, afterEach } from 'vitest'

const baseWf = { id: 'wf1', name: 'Pipeline', ownerId: 'u1', ownerUsername: 'alice', lastRunStatus: null }

function mockFetch(body: unknown, ok = true, status = 200) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
    ok, status,
    json: () => Promise.resolve(body),
  } as Response))
}

describe('api/workflows', () => {
  afterEach(() => vi.restoreAllMocks())

  it('list returns workflows array', async () => {
    mockFetch([baseWf])
    const { workflowApi } = await import('../../api/workflows')
    const result = await workflowApi.list()
    expect(result).toHaveLength(1)
    expect(result[0].name).toBe('Pipeline')
  })

  it('create sends POST and returns created workflow', async () => {
    mockFetch(baseWf, true, 201)
    const { workflowApi } = await import('../../api/workflows')
    const result = await workflowApi.create({ name: 'Pipeline' })
    expect(result.id).toBe('wf1')
  })

  it('delete sends DELETE', async () => {
    mockFetch(undefined, true, 204)
    const { workflowApi } = await import('../../api/workflows')
    await expect(workflowApi.delete('wf1')).resolves.toBeUndefined()
  })

  it('list throws on server error', async () => {
    mockFetch({ title: 'Unauthorized' }, false, 401)
    const { workflowApi } = await import('../../api/workflows')
    await expect(workflowApi.list()).rejects.toThrow()
  })
})
