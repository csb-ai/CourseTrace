import { apiRequest } from './api.js'

/** Only request a local session; media calls start after the caller installs its token. */
export async function requestLocalSession(signal) {
  const response = await apiRequest('/user/local-session', { method: 'POST', signal })
  if (!response.ok) throw new Error(`服务连接失败：${(await response.text()) || `HTTP ${response.status}`}`)
  const data = await response.json().catch(() => null)
  if (!data?.token || !data?.userInfo?.id) throw new Error('服务连接失败：本机会话响应不完整')
  return data
}