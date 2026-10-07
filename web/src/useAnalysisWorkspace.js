import { computed, ref } from 'vue'
import { apiRequest, captureAuthSession } from './api.js'
import { renderMarkdown } from './markdown.js'
import { savedAnalysisSteps, waitForPresentation } from './analysisPresentation.js'

export const SAMPLE_FOLLOW_UP = '视频里为什么反复看笔记会让人误以为自己学会了？请结合主动回忆的方法，给我一个今天可以实际做的练习，并引用视频原文；如果视频没有提到，请说明。'

export const DEFAULT_GOAL = '完整总结这节《学习如何学习》视频的核心方法：每条说明解决什么问题、为什么有效、怎么做，并引用对应时间戳和原文；最后整理一份适合初学者的一周学习行动清单。视频没讲到的内容明确标为建议，不能当作视频结论。'

// 分析模式选项。GENERAL/LEARNING/REVIEW/CREATION 与后端 AnalysisMode 枚举一一对应,value 直接作为 mode 参数;
// AUTO 是纯前端选项:提交前先调 /analysis/route 让 AI 判定出具体模式,再据此发起分析——
// AUTO 本身绝不会作为 mode 发到任何带 key 的后端接口,从根上避免读写端 key 不对称。
const ANALYSIS_MODES = [
  { value: 'AUTO', title: '自动', description: 'AI 按目标智能选择模式' },
  { value: 'GENERAL', title: '通用', description: '结论 · 时间戳证据 · 建议' },
  { value: 'LEARNING', title: '学习', description: '知识点大纲 · 重点难点 · 自测题' },
  { value: 'REVIEW', title: '审查', description: '逻辑漏洞 · 夸大表述 · 遗漏点' },
  { value: 'CREATION', title: '创作', description: '爆点片段 · 标题 · 口播脚本' }
]
const GOAL_PRESETS = [
  {
    title: '总结课程并制定一周计划',
    description: '核心方法、原文证据与一周行动',
    prompt: DEFAULT_GOAL
  },
  {
    title: '会议纪要',
    description: '结论、分歧与待办事项',
    prompt: '生成会议纪要，整理核心议题、明确结论、分歧点和待办事项，并引用对应时间戳'
  },
  {
    title: '操作手册',
    description: '步骤、条件与异常处理',
    prompt: '生成可执行操作手册，提取前置条件、操作步骤、注意事项和异常处理，并引用对应时间戳'
  }
]
const STAGE_LABELS = {
  VIDEO_CONTEXT: '解析语音与画面',
  RETRIEVAL: '检索相关证据',
  PLANNER: '拆解分析任务',
  EXECUTOR: '生成结构化结果',
  CRITIC: '核验结论与证据'
}

function createSidebarState() {
  return {
    visible: false,
    type: 'ai',
    mode: 'compose',
    title: '',
    content: '',
    error: '',
    reusedResult: false,
    showProcess: true,
    hasSavedReport: false,
    requestedAt: null,
    presenting: false,
    presentationSteps: [],
    presentationIndex: -1,
    historyLoading: false,
    loading: false,
    statusMessage: '',
    streamOffline: false,
    streamRetry: 0,
    mediaId: null,
    goal: DEFAULT_GOAL,
    analysisMode: 'LEARNING',
    playbackUrl: '',
    playbackLoading: false,
    playbackError: '',
    followUp: '',
    followUpLoading: false,
    evidenceQuery: '',
    evidenceLoading: false,
    evidenceResults: [],
    evidenceError: '',
    plan: null,
    trace: null,
    processRecords: [],
    processRecordsError: '',
    evaluation: null,
    feedback: null,
    feedbackLoading: false,
    editingPlan: false,
    planDraft: [],
    rerunLoading: false
  }
}

export function useAnalysisWorkspace({
  taskStreams,
  showMessage,
  refreshMediaList,
  findMediaItem,
  onAnswerAppended = () => {},
  presentationStepMs = 1800,
  presentationWait = waitForPresentation
}) {
  const sidebar = ref(createSidebarState())
  let evidenceRequestVersion = 0
  let historyRequestVersion = 0
  let metadataRequestVersion = 0
  // Keep a generation for view reuse: the same video/goal can be opened twice
  // while an earlier request is still in flight.
  let workspaceVersion = 0
  let presentationTask = null
  const stopPresentation = () => {
    presentationTask?.controller.abort()
    presentationTask = null
    sidebar.value.presenting = false
  }
  const skipPresentation = () => presentationTask?.finish()
  const captureWorkspace = () => {
    const version = workspaceVersion
    const isCurrentSession = captureAuthSession()
    return () => version === workspaceVersion && isCurrentSession()
  }
  const traceStages = computed(() => Object.entries(sidebar.value.trace?.stageDurationMs || {})
    .map(([stage, duration]) => [STAGE_LABELS[stage] || stage, formatDuration(duration)]))
  const renderedMarkdown = computed(() => renderMarkdown(sidebar.value.content))
  const isCurrentWorkspace = (id, type, goal = null, analysisMode = null) => sidebar.value.mediaId === id
    && sidebar.value.type === type
    && (goal === null || sidebar.value.goal === goal)
    && (analysisMode === null || sidebar.value.analysisMode === analysisMode)

  const openSidebar = (type, title) => {
    workspaceVersion += 1
    historyRequestVersion += 1
    sidebar.value.visible = true
    sidebar.value.type = type
    sidebar.value.title = title
    sidebar.value.loading = true
    sidebar.value.content = ''
    sidebar.value.error = ''
    sidebar.value.statusMessage = ''
    sidebar.value.streamOffline = false
    sidebar.value.streamRetry = 0
  }

  const closeSidebar = () => {
    stopPresentation()
    workspaceVersion += 1
    if (sidebar.value.type === 'ai' && sidebar.value.mediaId) {
      // goal 与 mode 一起持久化:二者共同决定任务身份,重开时必须成对恢复,
      // 否则会用错模式去查状态,导致"上次的非通用分析结果查不到"。
      saveGoalDraft(sidebar.value.mediaId, sidebar.value.goal)
      saveModeDraft(sidebar.value.mediaId, sidebar.value.analysisMode)
    }
    evidenceRequestVersion += 1
    historyRequestVersion += 1
    sidebar.value.evidenceLoading = false
    sidebar.value.visible = false
  }

  const loadPlayback = async id => {
    const panel = sidebar.value
    const isCurrentSession = captureAuthSession()
    const isCurrent = () => sidebar.value === panel && isCurrentSession()
    sidebar.value.playbackLoading = true
    sidebar.value.playbackError = ''
    try {
      const response = await apiRequest(`/media/playback?id=${id}`)
      const url = await response.text()
      if (!response.ok) throw new Error(url || '视频加载失败')
      if (isCurrent() && sidebar.value.mediaId === id) {
        sidebar.value.playbackUrl = url
        sidebar.value.playbackError = ''
      }
    } catch (error) {
      console.warn('Video preview unavailable', error)
      if (isCurrent() && sidebar.value.mediaId === id) {
        sidebar.value.playbackUrl = ''
        sidebar.value.playbackError = error.message || '原视频暂时无法加载'
      }
    } finally {
      if (isCurrent() && sidebar.value.mediaId === id) sidebar.value.playbackLoading = false
    }
  }

  const refreshAgentMeta = async (
    id,
    goal,
    includeEvaluation,
    analysisMode = sidebar.value.analysisMode || 'GENERAL'
  ) => {
    const isCurrent = captureWorkspace()
    const version = ++metadataRequestVersion
    const params = new URLSearchParams({ id: String(id), goal, mode: analysisMode })
    const requests = [
      apiRequest(`/analysis/agent-plan?${params}`),
      apiRequest(`/analysis/agent-trace?${params}`),
      apiRequest(`/analysis/process-records?${params}`)
    ]
    if (includeEvaluation) requests.push(apiRequest(`/analysis/agent-evaluation?${params}`))

    const settled = await Promise.allSettled(requests)
    const [plan, trace, processRecords, evaluation] = await Promise.all(settled.map(readSettledJson))
    if (!isCurrent() || version !== metadataRequestVersion
      || !isCurrentWorkspace(id, 'ai', goal, analysisMode)) return
    if (plan && !sidebar.value.editingPlan) sidebar.value.plan = plan
    if (trace) sidebar.value.trace = trace
    sidebar.value.processRecords = Array.isArray(processRecords) ? processRecords : []
    sidebar.value.processRecordsError = Array.isArray(processRecords) ? '' : '处理记录暂时无法读取'
    if (includeEvaluation && evaluation) sidebar.value.evaluation = evaluation
  }

  const startTaskStream = (id, type, goal = '', analysisMode = 'GENERAL') => {
    const isCurrentSession = captureAuthSession()
    const resolvedMode = analysisMode || 'GENERAL'
    const scope = type === 'ai' ? analysisScope(goal, resolvedMode) : ''
    const isCurrentTask = () => isCurrentSession() && isCurrentWorkspace(
      id, type, type === 'ai' ? goal : null, type === 'ai' ? resolvedMode : null)
      && (type !== 'ai' || sidebar.value.mode === 'result')
    const taskLabel = type === 'ai' ? 'AI 分析' : '文字提取'
    const finish = async (result, failed = false, signal) => {
      if (!isCurrentSession() || signal?.aborted) return
      const watching = sidebar.value.visible && isCurrentTask()
      if (watching) {
        sidebar.value.content = failed ? '' : result
        sidebar.value.loading = false
        sidebar.value.statusMessage = ''
        sidebar.value.streamOffline = false
        sidebar.value.streamRetry = 0
        sidebar.value.error = failed ? result : ''
        if (failed && type === 'ai') sidebar.value.mode = 'compose'
        if (type === 'ai' && !failed) await refreshAgentMeta(id, goal, true, resolvedMode)
      }
      if (!isCurrentSession() || signal?.aborted) return
      // 用户可能已经关掉面板去看别的视频，带上文件名才知道是哪个任务结束了。
      const filename = watching ? '' : findMediaItem(id)?.filename || ''
      const suffix = filename ? ` · ${filename}` : ''
      showMessage(
        failed
          ? `${taskLabel}失败${suffix}：${result || '请稍后重试'}`
          : `${taskLabel}完成${suffix}`,
        failed
      )
    }

    const params = new URLSearchParams({ id: String(id) })
    if (type === 'ai') {
      params.set('goal', goal)
      params.set('mode', resolvedMode)
    }
    const path = type === 'ai'
      ? `/analysis/analysis-events?${params}`
      : `/analysis/transcription-events?${params}`

    taskStreams.start(id, type, scope, path, async (status, signal) => {
      if (!isCurrentSession() || signal?.aborted) return
      if (isCurrentTask()) {
        // 收到任何事件都说明连接是通的，先把“重连中”提示撤掉。
        sidebar.value.streamOffline = false
        sidebar.value.streamRetry = 0
        if (status.message && (status.state === 'PROCESSING' || status.state === 'QUEUED')) {
          sidebar.value.statusMessage = status.message
        }
      }
      if (type === 'ai' && status.stage && status.state !== 'COMPLETED' && isCurrentTask()) {
        await refreshAgentMeta(id, goal, false, resolvedMode)
      }
      if (!isCurrentSession() || signal?.aborted) return
      if (status.state === 'COMPLETED') {
        await refreshMediaList()
        await finish(status.result || (type === 'ai' ? '分析完成' : ''), false, signal)
      } else if (status.state === 'FAILED') {
        await finish(status.message || '任务执行失败', true, signal)
      }
    }, (error, attempt, terminal = false) => {
      // isCurrentTask 保证用户已切换视频或关闭面板时，旧任务的错误不会写到新页面上。
      if (terminal) {
        console.warn('task event stream stopped', error)
        if (!isCurrentTask()) return
        sidebar.value.streamOffline = false
        sidebar.value.streamRetry = 0
        sidebar.value.loading = false
        sidebar.value.error = error?.message || '任务事件流已断开，请稍后重试'
        sidebar.value.statusMessage = ''
        if (type === 'ai') sidebar.value.mode = 'compose'
        return
      }
      console.warn('task event stream reconnecting', error)
      if (!isCurrentTask()) return
      sidebar.value.streamOffline = true
      sidebar.value.streamRetry = attempt || 1
    })
  }

  const transcribe = async id => {
    const item = findMediaItem(id)
    const panelTitle = item?.filename ? `全量文字提取 · ${item.filename}` : '全量文字提取'
    if (taskStreams.has(id, 'text')) {
      openSidebar('text', panelTitle)
      sidebar.value.mediaId = id
      sidebar.value.statusMessage = '文字提取正在后台继续，进度会自动同步'
      return
    }

    openSidebar('text', panelTitle)
    sidebar.value.mediaId = id
    const isCurrent = captureWorkspace()
    const isCurrentSession = captureAuthSession()
    sidebar.value.statusMessage = '提取任务已提交，正在识别语音'
    try {
      const current = await apiRequest(`/analysis/transcription-status?id=${id}`)
      if (!current.ok) throw new Error(await current.text())
      const currentStatus = await current.json()
      if (!isCurrentSession()) return
      if (currentStatus.state === 'COMPLETED') {
        if (isCurrent() && isCurrentWorkspace(id, 'text')) {
          sidebar.value.content = currentStatus.result || ''
          sidebar.value.statusMessage = ''
          sidebar.value.loading = false
        }
        return
      }
      if (currentStatus.state === 'QUEUED' || currentStatus.state === 'PROCESSING') {
        if (isCurrent() && isCurrentWorkspace(id, 'text') && currentStatus.message) {
          sidebar.value.statusMessage = currentStatus.message
        }
        startTaskStream(id, 'text')
        return
      }
      const response = await apiRequest(`/analysis/transcribe?id=${id}`, { method: 'POST' })
      if (!response.ok) throw new Error(await response.text())
      if (!isCurrentSession()) return
      startTaskStream(id, 'text')
    } catch (error) {
      if (isCurrent() && isCurrentWorkspace(id, 'text')) {
        sidebar.value.content = ''
        sidebar.value.statusMessage = ''
        sidebar.value.error = error.message || '文字提取失败，请稍后重试'
        sidebar.value.loading = false
      }
    }
  }

  const analyze = async (id, goal, mode = 'GENERAL') => {
    const isCurrentSession = captureAuthSession()
    const isCurrent = captureWorkspace()
    const resolvedMode = mode || 'GENERAL'
    const scope = analysisScope(goal, resolvedMode)
    if (taskStreams.has(id, 'ai', scope)) {
      sidebar.value.mode = 'result'
      sidebar.value.loading = true
      sidebar.value.statusMessage = '这个目标已有分析在进行，正在接管进度'
      return
    }

    sidebar.value.loading = true
    sidebar.value.mode = 'result'
    sidebar.value.content = ''
    sidebar.value.statusMessage = '正在提交分析请求'
    sidebar.value.streamOffline = false
    sidebar.value.streamRetry = 0
    try {
      const params = new URLSearchParams({ id: String(id), goal, mode: resolvedMode })
      const response = await apiRequest(`/analysis/ai?${params}`, { method: 'POST' })
      const message = await response.text()
      if (!isCurrentSession()) return
      if (response.status === 409) {
        startTaskStream(id, 'ai', goal, resolvedMode)
        refreshAgentMeta(id, goal, false, resolvedMode)
        return
      }
      if (!response.ok) {
        if (isCurrent() && isCurrentWorkspace(id, 'ai', goal, resolvedMode)) {
          showMessage(message, true)
          sidebar.value.loading = false
          sidebar.value.statusMessage = ''
          sidebar.value.mode = 'compose'
          sidebar.value.error = message
        }
        return
      }
      if (response.status === 200) {
        const cachedResponse = await apiRequest(`/analysis/analysis-status?${params}`)
        if (!cachedResponse.ok) throw new Error(await cachedResponse.text())
        const cachedStatus = await cachedResponse.json()
        if (!isCurrentSession()) return
        if (cachedStatus?.state === 'COMPLETED') {
          if (isCurrent() && isCurrentWorkspace(id, 'ai', goal, resolvedMode)) {
            sidebar.value.reusedResult = true
            await refreshAgentMeta(id, goal, true, resolvedMode)
            if (!isCurrent() || !sidebar.value.visible) return
            const result = cachedStatus.result || ''
            if (sidebar.value.showProcess) {
              const steps = savedAnalysisSteps({ records: sidebar.value.processRecords,
                plan: sidebar.value.plan, trace: sidebar.value.trace, evaluation: sidebar.value.evaluation, result })
              const controller = new AbortController()
              const task = { controller, finish: () => {
                if (presentationTask !== task || !isCurrent()) return
                controller.abort()
                presentationTask = null
                sidebar.value.presenting = false
                sidebar.value.content = result
                sidebar.value.loading = false
                sidebar.value.statusMessage = ''
              } }
              presentationTask = task
              sidebar.value.presentationSteps = steps
              sidebar.value.presentationIndex = 0
              sidebar.value.presenting = true
              for (let index = 0; index < steps.length; index += 1) {
                if (controller.signal.aborted || !isCurrent()) return
                sidebar.value.presentationIndex = index
                await presentationWait(presentationStepMs, controller.signal)
              }
              task.finish()
            } else {
              sidebar.value.content = result
              sidebar.value.loading = false
              sidebar.value.statusMessage = ''
            }
            if (!isCurrent()) return
            showMessage('分析结果已就绪')
          }
          return
        }
      }
      if (isCurrent() && isCurrentWorkspace(id, 'ai', goal, resolvedMode)) sidebar.value.statusMessage = message || '服务端已接受任务'
      startTaskStream(id, 'ai', goal, resolvedMode)
      refreshAgentMeta(id, goal, false, resolvedMode)
    } catch (error) {
      if (isCurrent() && isCurrentWorkspace(id, 'ai', goal, resolvedMode)) {
        sidebar.value.mode = 'compose'
        sidebar.value.error = error.message || String(error)
        sidebar.value.loading = false
        sidebar.value.statusMessage = ''
      }
    }
  }

  const openAgent = async (item, { useDefaultGoal = false } = {}) => {
    stopPresentation()
    workspaceVersion += 1
    const historyVersion = ++historyRequestVersion
    const isCurrent = captureWorkspace()
    evidenceRequestVersion += 1
    const goal = useDefaultGoal ? DEFAULT_GOAL : loadGoalDraft(item.id)
    // 恢复上次使用的模式,与 goal 一起构成任务身份;缺省为通用模式。
    const analysisMode = useDefaultGoal ? 'LEARNING' : loadModeDraft(item.id)
    if (useDefaultGoal) { saveGoalDraft(item.id, goal); saveModeDraft(item.id, analysisMode) }
    sidebar.value = {
      ...createSidebarState(),
      visible: true,
      title: `课迹 Agent · ${item.filename}`,
      mediaId: item.id,
      goal,
      analysisMode,
      historyLoading: true
    }

    loadPlayback(item.id)
    try {
      const params = new URLSearchParams({ id: String(item.id), goal, mode: sidebar.value.analysisMode || 'GENERAL' })
      const response = await apiRequest(`/analysis/analysis-status?${params}`)
      if (!response.ok) {
        const detail = await response.text()
        throw new Error(detail || '历史分析状态加载失败')
      }
      const status = await response.json()
      if (!isCurrent() || historyVersion !== historyRequestVersion || sidebar.value.mediaId !== item.id
        || sidebar.value.goal !== goal
        || sidebar.value.analysisMode !== analysisMode) return

      if (status.state === 'COMPLETED') {
        // Always let the user choose the mode and goal before revealing a saved result.
        sidebar.value.hasSavedReport = true
      } else if (status.state === 'QUEUED' || status.state === 'PROCESSING') {
        sidebar.value.statusMessage = '该目标已有后台任务，点击开始分析可查看实际进度'
      } else if (status.state === 'FAILED') {
        sidebar.value.error = status.message || '上次分析未完成，可以重新提交'
      }
    } catch (error) {
      console.warn('Previous analysis unavailable', error)
      if (isCurrent() && historyVersion === historyRequestVersion && sidebar.value.mediaId === item.id
        && sidebar.value.goal === goal
        && sidebar.value.analysisMode === analysisMode) {
        sidebar.value.error = error.message || '历史分析状态加载失败，请稍后重试'
      }
    } finally {
      if (isCurrent() && historyVersion === historyRequestVersion && sidebar.value.mediaId === item.id) sidebar.value.historyLoading = false
    }
  }

  // AUTO 模式的意图路由:仅凭目标文本让后端 AI 判定出具体模式。apiRequest 已解包信封,
  // response.json() 直接就是 { mode, reason }。失败由调用方兜底,这里只负责发起请求。
  const routeMode = async goal => {
    const response = await apiRequest('/analysis/route', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ goal })
    })
    if (!response.ok) throw new Error(await response.text())
    return response.json()
  }

  const submitAgent = async () => {
    const goal = sidebar.value.goal.trim()
    if (!goal || sidebar.value.loading) return
    stopPresentation()
    historyRequestVersion += 1
    workspaceVersion += 1
    metadataRequestVersion += 1
    const isCurrent = captureWorkspace()
    const mediaId = sidebar.value.mediaId
    // 后端、SSE scope 与异步回调必须使用同一份归一化目标，否则前后空格会让当前任务判定失配。
    sidebar.value.goal = goal
    sidebar.value.requestedAt = new Date().toISOString()
    sidebar.value.error = ''
    sidebar.value.reusedResult = false
    sidebar.value.historyLoading = false
    saveGoalDraft(mediaId, goal)
    Object.assign(sidebar.value, {
      plan: null, trace: null, processRecords: [], processRecordsError: '', evaluation: null, feedback: null,
      followUp: '', followUpLoading: false, feedbackLoading: false,
      editingPlan: false, planDraft: [], rerunLoading: false
    })
    let mode = sidebar.value.analysisMode || 'GENERAL'
    if (mode === 'AUTO') {
      // 先把 AUTO 落定成具体模式,再提交。落定结果写回 sidebar.analysisMode,
      // 之后的状态查询 / 重跑 / 元信息刷新都用它,确保与提交端 key 完全一致。
      sidebar.value.mode = 'result'
      sidebar.value.loading = true
      sidebar.value.content = ''
      sidebar.value.statusMessage = '正在识别分析意图…'
      let decision = null
      try {
        decision = await routeMode(goal)
      } catch (error) {
        console.warn('intent routing failed', error)
      }
      // 路由是异步的,用户可能已切到别的视频或改了目标;仅当仍停留在同一任务时才落定并继续。
      if (!isCurrent() || sidebar.value.mediaId !== mediaId || sidebar.value.goal.trim() !== goal) return
      if (decision && decision.mode) {
        mode = decision.mode
        showMessage(`AI 已识别为「${modeTitle(mode)}」模式：${decision.reason || ''}`.trim())
      } else {
        mode = 'GENERAL'
        showMessage('意图识别暂不可用，已按通用模式分析', true)
      }
      sidebar.value.analysisMode = mode
    }
    // 记住这次实际使用的具体模式,重开面板时据此恢复,保证能查回本次分析结果。
    saveModeDraft(mediaId, mode)
    await analyze(mediaId, goal, mode)
  }

  const startNewAnalysis = () => {
    stopPresentation()
    historyRequestVersion += 1
    workspaceVersion += 1
    sidebar.value.mode = 'compose'
    sidebar.value.loading = false
    sidebar.value.historyLoading = false
    sidebar.value.error = ''
  }

  const startPlanEdit = () => {
    sidebar.value.planDraft = [...(sidebar.value.plan?.tasks || [])]
    sidebar.value.editingPlan = true
  }

  const cancelPlanEdit = () => {
    sidebar.value.editingPlan = false
    sidebar.value.planDraft = []
  }

  const addPlanTask = () => {
    if (sidebar.value.planDraft.length < 5) sidebar.value.planDraft.push('')
  }

  const removePlanTask = index => {
    if (sidebar.value.planDraft.length > 1) sidebar.value.planDraft.splice(index, 1)
  }

  const rerunWithPlan = async () => {
    if (sidebar.value.rerunLoading) return
    const tasks = sidebar.value.planDraft.map(task => task.trim()).filter(Boolean)
    if (!tasks.length || tasks.length > 5) {
      showMessage('计划需保留 1 至 5 个有效任务', true)
      return
    }
    // A revised plan is a new generation even when media, goal and mode are unchanged.
    workspaceVersion += 1
    historyRequestVersion += 1
    metadataRequestVersion += 1
    const isCurrent = captureWorkspace()
    const isCurrentSession = captureAuthSession()
    sidebar.value.followUpLoading = false
    sidebar.value.feedbackLoading = false

    const mediaId = sidebar.value.mediaId
    const goal = sidebar.value.goal
    const analysisMode = sidebar.value.analysisMode || 'GENERAL'
    sidebar.value.rerunLoading = true
    try {
      const reviseParams = new URLSearchParams({ mode: analysisMode })
      const response = await apiRequest(`/analysis/agent-revise?${reviseParams}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          mediaId,
          goal,
          correctedTasks: tasks,
          comment: '用户调整 Planner 任务后重新执行'
        })
      })
      const message = await response.text()
      if (!response.ok) throw new Error(message || '重新提交失败')
      if (!isCurrentSession()) return
      if (isCurrent() && isCurrentWorkspace(mediaId, 'ai', goal, analysisMode)) {
        sidebar.value.plan = { ...sidebar.value.plan, tasks }
        cancelPlanEdit()
        sidebar.value.content = ''
        metadataRequestVersion += 1
        sidebar.value.trace = null
        sidebar.value.evaluation = null
        sidebar.value.feedback = null
        sidebar.value.loading = true
        sidebar.value.statusMessage = '已按新计划重新提交，正在重新执行'
        sidebar.value.streamOffline = false
        sidebar.value.streamRetry = 0
      }
      startTaskStream(mediaId, 'ai', goal, analysisMode)
    } catch (error) {
      if (isCurrent() && isCurrentWorkspace(mediaId, 'ai', goal, analysisMode)) {
        showMessage(error.message || '重新提交失败', true)
      }
    } finally {
      if (isCurrent() && isCurrentWorkspace(mediaId, 'ai', goal, analysisMode)) sidebar.value.rerunLoading = false
    }
  }

  const submitFollowUp = async () => {
    const isCurrent = captureWorkspace()
    const question = sidebar.value.followUp.trim()
    if (!question || sidebar.value.followUpLoading || sidebar.value.loading || sidebar.value.rerunLoading) return

    const mediaId = sidebar.value.mediaId
    const goal = sidebar.value.goal
    const analysisMode = sidebar.value.analysisMode || 'GENERAL'
    sidebar.value.followUpLoading = true
    try {
      const params = new URLSearchParams({
        id: String(mediaId),
        question,
        goal,
        mode: analysisMode
      })
      const response = await apiRequest(`/analysis/follow-up?${params}`, { method: 'POST' })
      const answer = await response.text()
      if (!response.ok) throw new Error(answer || '追问失败')
      if (isCurrent() && isCurrentWorkspace(mediaId, 'ai', goal, analysisMode)) {
        sidebar.value.content += `\n\n## 追问\n${question}\n\n${answer}`
        sidebar.value.followUp = ''
        // 答案追加在长文末尾，主动带用户滚过去，否则会以为“点了没反应”。
        onAnswerAppended()
      }
    } catch (error) {
      if (isCurrent() && isCurrentWorkspace(mediaId, 'ai', goal, analysisMode)) {
        showMessage(`❌ ${error.message}`, true)
      }
    } finally {
      if (isCurrent() && isCurrentWorkspace(mediaId, 'ai', goal, analysisMode)) sidebar.value.followUpLoading = false
    }
  }

  const searchEvidence = async () => {
    const query = sidebar.value.evidenceQuery.trim()
    if (!query || sidebar.value.evidenceLoading) return
    const requestVersion = ++evidenceRequestVersion
    const mediaId = sidebar.value.mediaId
    sidebar.value.evidenceLoading = true
    sidebar.value.evidenceError = ''
    sidebar.value.evidenceResults = []
    try {
      const params = new URLSearchParams({
        id: String(mediaId),
        query
      })
      const response = await apiRequest(`/analysis/evidence-search?${params}`)
      if (!response.ok) {
        const detail = await response.text()
        throw new Error(detail || '视频证据检索失败')
      }
      const results = await response.json()
      if (requestVersion !== evidenceRequestVersion || sidebar.value.mediaId !== mediaId) return
      sidebar.value.evidenceResults = Array.isArray(results) ? results : []
      if (!sidebar.value.evidenceResults.length) {
        sidebar.value.evidenceError = '没有找到匹配的视频证据'
      }
    } catch (error) {
      if (requestVersion !== evidenceRequestVersion || sidebar.value.mediaId !== mediaId) return
      sidebar.value.evidenceResults = []
      sidebar.value.evidenceError = error.message || '视频证据检索失败'
    } finally {
      if (requestVersion === evidenceRequestVersion && sidebar.value.mediaId === mediaId) {
        sidebar.value.evidenceLoading = false
      }
    }
  }

  const sendFeedback = async rating => {
    const isCurrent = captureWorkspace()
    if (sidebar.value.feedbackLoading || sidebar.value.feedback === rating) return
    const mediaId = sidebar.value.mediaId
    const goal = sidebar.value.goal
    const analysisMode = sidebar.value.analysisMode || 'GENERAL'
    sidebar.value.feedbackLoading = true
    try {
      const response = await apiRequest('/analysis/agent-feedback', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ mediaId, goal, mode: analysisMode, rating })
      })
      if (!response.ok) throw new Error(await response.text())
      if (isCurrent() && isCurrentWorkspace(mediaId, 'ai', goal, analysisMode)) {
        sidebar.value.feedback = rating
        showMessage('反馈已记录')
      }
    } catch (error) {
      if (isCurrent() && isCurrentWorkspace(mediaId, 'ai', goal, analysisMode)) {
        showMessage(`❌ ${error.message}`, true)
      }
    } finally {
      if (isCurrent() && isCurrentWorkspace(mediaId, 'ai', goal, analysisMode)) sidebar.value.feedbackLoading = false
    }
  }

  const retryPlayback = () => {
    if (sidebar.value.mediaId && !sidebar.value.playbackLoading) {
      loadPlayback(sidebar.value.mediaId)
    }
  }

  const handlePlaybackError = () => {
    if (!sidebar.value.playbackUrl) return
    sidebar.value.playbackUrl = ''
    sidebar.value.playbackError = '视频无法播放：可能是播放地址不可用，或视频编码不受当前浏览器支持。请重新加载；链接导入的视频可重新导入后再试。'
  }

  const resetWorkspace = () => {
    stopPresentation()
    workspaceVersion += 1
    historyRequestVersion += 1
    evidenceRequestVersion += 1
    sidebar.value = createSidebarState()
  }

  const discardMediaWorkspace = mediaId => {
    taskStreams.stopMedia(mediaId)
    try {
      localStorage.removeItem(goalDraftKey(mediaId))
      localStorage.removeItem(modeDraftKey(mediaId))
    } catch {
      // Storage being unavailable should not block media deletion.
    }
    if (sidebar.value.mediaId === mediaId) resetWorkspace()
  }

  return {
    sidebar,
    goalPresets: GOAL_PRESETS,
    analysisModes: ANALYSIS_MODES,
    traceStages,
    renderedMarkdown,
    transcribe,
    closeSidebar,
    openAgent,
    submitAgent,
    skipPresentation,
    startNewAnalysis,
    startPlanEdit,
    cancelPlanEdit,
    addPlanTask,
    removePlanTask,
    rerunWithPlan,
    submitFollowUp,
    searchEvidence,
    sendFeedback,
    retryPlayback,
    handlePlaybackError,
    resetWorkspace,
    discardMediaWorkspace,
    formatPercent: value => `${Math.round((Number(value) || 0) * 100)}%`
  }
}

async function readSettledJson(result) {
  if (result.status !== 'fulfilled' || !result.value.ok) return null
  try {
    return await result.value.json()
  } catch (error) {
    console.warn('Agent metadata response is invalid', error)
    return null
  }
}

function modeTitle(value) {
  return ANALYSIS_MODES.find(m => m.value === value)?.title || value
}

function analysisScope(goal, mode) {
  return `${mode || 'GENERAL'}:${goal}`
}

function formatDuration(value) {
  const milliseconds = Number(value) || 0
  if (milliseconds < 1000) return `${Math.round(milliseconds)} 毫秒`
  return `${(milliseconds / 1000).toFixed(milliseconds < 10_000 ? 1 : 0)} 秒`
}

function goalDraftKey(mediaId) {
  return `coursetrace:goal:${mediaId}`
}

function loadGoalDraft(mediaId) {
  try {
    return localStorage.getItem(goalDraftKey(mediaId)) || DEFAULT_GOAL
  } catch {
    return DEFAULT_GOAL
  }
}

function saveGoalDraft(mediaId, goal) {
  if (!mediaId || !goal?.trim()) return
  try {
    localStorage.setItem(goalDraftKey(mediaId), goal.trim())
  } catch {
    // Private browsing can disable storage; the current session still works.
  }
}

function modeDraftKey(mediaId) {
  return `coursetrace:mode:${mediaId}`
}

function loadModeDraft(mediaId) {
  try {
    return localStorage.getItem(modeDraftKey(mediaId)) || 'LEARNING'
  } catch {
    return 'LEARNING'
  }
}

// 只持久化“具体模式”。AUTO 是临时选择,落定后才是真实模式;绝不把 AUTO 写进草稿,
// 否则重开时会用 AUTO 去查状态(后端按 GENERAL 解析),反而查不回真正的结果。
function saveModeDraft(mediaId, mode) {
  if (!mediaId || !mode || mode === 'AUTO') return
  try {
    localStorage.setItem(modeDraftKey(mediaId), mode)
  } catch {
    // Private browsing can disable storage; the current session still works.
  }
}
