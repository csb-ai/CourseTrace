// Present stages supported by saved artifacts. These are presentation steps, not new model executions.
export function savedAnalysisSteps({ records = [], plan, trace, evaluation, result = '' }) {
  const stages = new Set(records.map(record => record.stage))
  const steps = []
  if (stages.has('VIDEO_CONTEXT') || trace?.counters?.contextCheckpointHits) {
    steps.push({ key: 'context', title: '语音与画面解析', detail: '读取已保存的语音原文、画面文字和时间位置。' })
  }
  if (trace?.counters?.retrievalChunks || stages.has('AGENT_LOOP')) {
    steps.push({ key: 'material', title: '整理知识与证据', detail: trace?.counters?.retrievalChunks
      ? `结合当前目标，展示已选取的 ${trace.counters.retrievalChunks} 个知识块。`
      : '读取已保存的知识材料，保留与原视频的对应关系。' })
  }
  if (plan?.tasks?.length) {
    steps.push({ key: 'plan', title: 'Planner · 拆解任务', detail: `当前目标已拆成 ${plan.tasks.length} 项任务。`, tasks: plan.tasks })
  }
  if (result) steps.push({ key: 'report', title: 'Executor · 生成报告', detail: '展开已生成的结构化报告，关联结论、原文与行动建议。' })
  if (evaluation) {
    steps.push({ key: 'check', title: 'Critic · 检查证据',
      detail: evaluation.criticPassed ? '已保存的检查结果：目标与证据检查通过。' : '已保存的检查结果仍有待核验项，报告会保留原提示。',
      warning: !evaluation.criticPassed })
  }
  steps.push({ key: 'ready', title: '整理完成', detail: '展示报告，可以回看时间戳、检索原文并继续追问。' })
  return steps
}

export function waitForPresentation(milliseconds, signal) {
  return new Promise(resolve => {
    if (signal.aborted) { resolve(); return }
    const done = () => { clearTimeout(timer); signal.removeEventListener('abort', done); resolve() }
    const timer = setTimeout(done, milliseconds)
    signal.addEventListener('abort', done, { once: true })
  })
}

export function latestAnalysisRecords(records = []) {
  const executions = records.filter(record => !['RESULT_REUSED', 'COMPLETED_REUSED'].includes(record.stage))
  const start = executions.findLastIndex(record => record.stage === 'QUEUED')
  return start < 0 ? executions : executions.slice(start)
}
