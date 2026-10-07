import assert from 'node:assert/strict'
import test from 'node:test'
import { savedAnalysisSteps, latestAnalysisRecords } from './analysisPresentation.js'

test('missing records cannot invent ASR, planner or a passed quality check', () => {
  assert.deepEqual(savedAnalysisSteps({ result: 'saved report' }).map(s => s.key), ['report', 'ready'])
  const warning = savedAnalysisSteps({ evaluation: { criticPassed: false } }).find(s => s.key === 'check')
  assert.equal(warning.warning, true)
})

test('saved context, plan, report and evaluation support the full presentation', () => {
  const steps = savedAnalysisSteps({ records: [{ stage: 'VIDEO_CONTEXT' }],
    plan: { tasks: ['goal task'] }, trace: { counters: { retrievalChunks: 3 } },
    evaluation: { criticPassed: true }, result: 'real report' })
  assert.deepEqual(steps.map(s => s.key), ['context', 'material', 'plan', 'report', 'check', 'ready'])
  assert.deepEqual(steps[2].tasks, ['goal task'])
  assert.equal(steps[4].warning, false)
})

test('details show the latest real run without accumulating reuse events', () => {
  const records = [{ id: 1, stage: 'QUEUED' }, { id: 2, stage: 'FAILED' },
    { id: 3, stage: 'QUEUED' }, { id: 4, stage: 'CRITIC_PASSED' },
    { id: 5, stage: 'COMPLETED' }, { id: 6, stage: 'COMPLETED_REUSED' },
    { id: 7, stage: 'COMPLETED_REUSED' }]
  assert.deepEqual(latestAnalysisRecords(records).map(r => r.id), [3, 4, 5])
  assert.equal(records.length, 7)
})
