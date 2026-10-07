import assert from 'node:assert/strict'
import test, { beforeEach } from 'node:test'
import { useAnalysisWorkspace, DEFAULT_GOAL } from './useAnalysisWorkspace.js'
import { clearAuthToken, setAuthToken } from './api.js'

const item = { id: 7, filename: 'sample.mp4' }
const json = data => Response.json({ code: 0, message: 'success', data })
const deferred = () => {
  let resolve
  const promise = new Promise(done => { resolve = done })
  return { promise, resolve }
}

beforeEach(() => {
  const storage = new Map()
  globalThis.localStorage = {
    getItem: key => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, value),
    removeItem: key => storage.delete(key)
  }
  setAuthToken('token')
})

function setup(options = {}) {
  const starts = []
  const workspace = useAnalysisWorkspace({
    taskStreams: { has: () => false, start: (...args) => starts.push(args), stopMedia() {} },
    showMessage() {}, refreshMediaList: async () => {}, findMediaItem: () => item,
    presentationStepMs: 0, ...options
  })
  return { ...workspace, starts }
}

test('late history cannot replace a new submission and old metadata is cleared', async () => {
  const history = deferred()
  globalThis.fetch = async url => {
    if (url.startsWith('/analysis/ai?')) return Response.json({ code: 0, message: 'accepted', data: 'accepted' }, { status: 202 })
    if (url.startsWith('/analysis/analysis-status')) return history.promise
    if (url.startsWith('/media/playback')) return json('https://example.com/video.mp4')
    return json(null)
  }
  const workspace = setup()
  const opening = workspace.openAgent(item)
  workspace.sidebar.value.plan = { tasks: ['old plan'] }
  workspace.sidebar.value.evaluation = { criticPassed: true }
  workspace.sidebar.value.feedback = 1
  await workspace.submitAgent()
  history.resolve(json({ state: 'COMPLETED', result: 'old result' }))
  await opening
  assert.equal(workspace.sidebar.value.mode, 'result')
  assert.equal(workspace.sidebar.value.content, '')
  assert.equal(workspace.sidebar.value.evaluation, null)
  assert.equal(workspace.sidebar.value.feedback, null)
  assert.equal(workspace.sidebar.value.plan, null)
  assert.equal(workspace.starts.length, 1)
})

test('logout during submission cannot restart task streams', async () => {
  const submission = deferred()
  globalThis.fetch = async url => url.startsWith('/analysis/ai?')
    ? submission.promise : json({ state: 'NOT_STARTED' })
  const workspace = setup()
  await workspace.openAgent(item)
  const submitting = workspace.submitAgent()
  clearAuthToken()
  workspace.resetWorkspace()
  submission.resolve(json(null))
  await submitting
  assert.equal(workspace.starts.length, 0)
  assert.equal(workspace.sidebar.value.mediaId, null)
})

test('terminal stream errors return the analysis panel to a visible retry state', async () => {
  globalThis.fetch = async () => json({ state: 'NOT_STARTED' })
  const workspace = setup()
  await workspace.openAgent(item)
  await workspace.submitAgent()
  const onError = workspace.starts[0][5]
  onError(new Error('没有权限读取任务'), 1, true)
  assert.equal(workspace.sidebar.value.loading, false)
  assert.equal(workspace.sidebar.value.mode, 'compose')
  assert.equal(workspace.sidebar.value.error, '没有权限读取任务')
})

test('closing an evidence search releases its busy state and discards the late result', async () => {
  const evidence = deferred()
  globalThis.fetch = async url => url.startsWith('/analysis/evidence-search')
    ? evidence.promise : json({ state: 'NOT_STARTED' })
  const workspace = setup()
  await workspace.openAgent(item)
  workspace.sidebar.value.evidenceQuery = 'queue'
  const searching = workspace.searchEvidence()
  workspace.closeSidebar()
  assert.equal(workspace.sidebar.value.evidenceLoading, false)
  evidence.resolve(json([{ snippet: 'late result' }]))
  await searching
  assert.deepEqual(workspace.sidebar.value.evidenceResults, [])
})

test('switching panels while transcription is submitted keeps its background stream', async () => {
  const submission = deferred()
  let submitted
  const started = new Promise(resolve => { submitted = resolve })
  globalThis.fetch = async url => {
    if (url.startsWith('/analysis/transcribe?')) {
      submitted()
      return submission.promise
    }
    return json({ state: 'NOT_STARTED' })
  }
  const workspace = setup()
  const transcribing = workspace.transcribe(item.id)
  await started
  await workspace.openAgent({ id: 8, filename: 'another.mp4' })
  submission.resolve(json(null))
  await transcribing
  assert.equal(workspace.sidebar.value.mediaId, 8)
  assert.equal(workspace.starts.length, 1)
  assert.equal(workspace.starts[0][0], 7)
  assert.equal(workspace.starts[0][1], 'text')
})

test('a late follow-up from the previous plan cannot append to a revision of the same goal', async () => {
  const answer = deferred()
  globalThis.fetch = async url => {
    if (url.startsWith('/analysis/follow-up')) return answer.promise
    if (url.startsWith('/analysis/analysis-status')) return json({ state: 'COMPLETED', result: 'old report' })
    return json(null)
  }
  const workspace = setup()
  await workspace.openAgent(item)
  workspace.sidebar.value.followUp = 'old plan question'
  const asking = workspace.submitFollowUp()
  workspace.sidebar.value.planDraft = ['new task']
  await workspace.rerunWithPlan()
  answer.resolve(json('late old answer'))
  await asking
  assert.equal(workspace.sidebar.value.content, '')
  assert.equal(workspace.sidebar.value.loading, true)
  assert.equal(workspace.sidebar.value.followUpLoading, false)
  assert.equal(workspace.sidebar.value.rerunLoading, false)
})


test('uploading an existing video stays on mode and goal selection without revealing the report', async () => {
  localStorage.setItem('coursetrace:goal:7', 'different old goal')
  localStorage.setItem('coursetrace:mode:7', 'GENERAL')
  const requests = []
  globalThis.fetch = async (url, options) => {
    requests.push({ url, method: options.method || 'GET' })
    if (url.startsWith('/analysis/analysis-status')) return json({ state: 'COMPLETED', result: 'persisted real report' })
    if (url.startsWith('/analysis/process-records')) return json([{ id: 8, stage: 'PLANNER', state: 'PROCESSING', message: 'stored actual event', createdAt: '2026-10-07T08:00:00Z' }])
    return json(null)
  }
  const workspace = setup()
  await workspace.openAgent(item, { useDefaultGoal: true })
  const status = requests.find(request => request.url.startsWith('/analysis/analysis-status'))
  const params = new URL(status.url, 'http://local.invalid').searchParams
  assert.equal(params.get('goal'), DEFAULT_GOAL)
  assert.equal(params.get('mode'), 'LEARNING')
  assert.equal(workspace.sidebar.value.mode, 'compose')
  assert.equal(workspace.sidebar.value.content, '')
  assert.equal(workspace.sidebar.value.hasSavedReport, true)
  assert.equal(workspace.sidebar.value.showProcess, true)
  assert.equal(workspace.sidebar.value.processRecords.length, 0)
  assert.equal(workspace.starts.length, 0)
  assert(requests.every(request => request.method === 'GET'))
  assert(!requests.some(request => request.url.startsWith('/analysis/route') || request.url.startsWith('/analysis/ai?')))
})

test('cached process holds the report until presentation ends and can be skipped', async () => {
  const started = deferred()
  globalThis.fetch = async url => {
    if (url.startsWith('/analysis/analysis-status')) return json({ state: 'COMPLETED', result: 'real saved report' })
    if (url.startsWith('/analysis/agent-plan')) return json({ tasks: ['summarize evidence'] })
    if (url.startsWith('/analysis/agent-evaluation')) return json({ criticPassed: true })
    return json(null)
  }
  const workspace = setup({ presentationWait: (_, signal) => new Promise(resolve => {
    signal.addEventListener('abort', resolve, { once: true }); started.resolve()
  }) })
  await workspace.openAgent(item, { useDefaultGoal: true })
  const submission = workspace.submitAgent()
  await started.promise
  assert.equal(workspace.sidebar.value.presenting, true)
  assert.equal(workspace.sidebar.value.content, '')
  assert.equal(workspace.sidebar.value.presentationSteps[0].key, 'plan')
  workspace.skipPresentation()
  await submission
  assert.equal(workspace.sidebar.value.presenting, false)
  assert.equal(workspace.sidebar.value.content, 'real saved report')
  assert.equal(workspace.starts.length, 0)
})

test('closing a presentation cancels it and cannot put its report in a different video', async () => {
  const started = deferred()
  globalThis.fetch = async url => url.startsWith('/analysis/analysis-status')
    ? json({ state: 'COMPLETED', result: 'old report' }) : json(null)
  const workspace = setup({ presentationWait: (_, signal) => new Promise(resolve => {
    signal.addEventListener('abort', resolve, { once: true }); started.resolve()
  }) })
  await workspace.openAgent(item)
  const submission = workspace.submitAgent()
  await started.promise
  workspace.closeSidebar()
  await workspace.openAgent({ id: 8, filename: 'new.mp4' })
  await submission
  assert.equal(workspace.sidebar.value.mediaId, 8)
  assert.equal(workspace.sidebar.value.mode, 'compose')
  assert.equal(workspace.sidebar.value.content, '')
  assert.equal(workspace.sidebar.value.presenting, false)
})

test('disabling process presentation shows a saved result without waiting', async () => {
  globalThis.fetch = async url => url.startsWith('/analysis/analysis-status')
    ? json({ state: 'COMPLETED', result: 'saved report' }) : json(null)
  const workspace = setup({ presentationWait: () => { throw new Error('should not wait') } })
  await workspace.openAgent(item)
  workspace.sidebar.value.showProcess = false
  await workspace.submitAgent()
  assert.equal(workspace.sidebar.value.content, 'saved report')
  assert.equal(workspace.sidebar.value.presenting, false)
})


test('a 200 cache response reads the persisted report and records without opening an event stream', async () => {
  let submitted = false
  globalThis.fetch = async url => {
    if (url.startsWith('/analysis/ai?')) { submitted = true; return json('复用已有结果') }
    if (url.startsWith('/analysis/analysis-status')) return json(submitted ? { state: 'COMPLETED', result: 'cached report' } : { state: 'NOT_STARTED' })
    if (url.startsWith('/analysis/process-records')) return json([{ id: 9, state: 'RESULT_REUSED', message: '本次未调用模型' }])
    return json(null)
  }
  const workspace = setup()
  await workspace.openAgent(item)
  await workspace.submitAgent()
  assert.equal(workspace.sidebar.value.content, 'cached report')
  assert.equal(workspace.sidebar.value.loading, false)
  assert.equal(workspace.sidebar.value.reusedResult, true)
  assert.equal(workspace.sidebar.value.processRecords[0].state, 'RESULT_REUSED')
  assert.equal(workspace.starts.length, 0)
})
