import assert from 'node:assert/strict'
import test, { beforeEach } from 'node:test'
import { requestLocalSession } from './localSession.js'

beforeEach(() => {
  globalThis.localStorage = { getItem: () => null }
})

test('local session unwraps the backend envelope without making media requests', async () => {
  const requests = []
  globalThis.fetch = async (url, options) => {
    requests.push([url, options.method])
    return Response.json({ code: 0, message: 'success', data: { token: 'session-test-value', userInfo: { id: 1 } } })
  }
  const session = await requestLocalSession()
  assert.equal(session.userInfo.id, 1)
  assert.deepEqual(requests, [['/user/local-session', 'POST']])
})

test('disabled local sessions and invalid payloads fail with service messages', async () => {
  globalThis.fetch = async () => Response.json({ code: 403, message: '本机模式未启用', data: null }, { status: 403 })
  await assert.rejects(requestLocalSession(), /服务连接失败：本机模式未启用/)
  globalThis.fetch = async () => Response.json({ code: 0, message: 'success', data: { userInfo: { id: 1 } } })
  await assert.rejects(requestLocalSession(), /本机会话响应不完整/)
})