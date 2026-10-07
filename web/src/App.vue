<template>
  <div class="app-stage" :data-theme="theme">
    <header class="navbar">
      <div class="nav-content">
        <div class="brand">
          <span class="brand-mark" aria-hidden="true">迹</span><span class="brand-name">课迹</span>
          <span class="brand-agent">Agent</span>
          <span class="brand-caption">CourseTrace</span>
        </div>

        <div class="nav-controls">
          <button type="button" class="theme-toggle" :aria-pressed="theme === 'dark'" :aria-label="theme === 'dark' ? '切换为浅色主题' : '切换为深色主题'" :title="theme === 'dark' ? '切换为浅色主题' : '切换为深色主题'" @click="toggleTheme">
            <svg v-if="theme === 'light'" aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M20.9 13A9 9 0 0 1 11 3.1 9 9 0 1 0 20.9 13Z" /></svg>
            <svg v-else aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5"/></svg>
            <span>{{ theme === 'dark' ? '浅色' : '深色' }}</span>
          </button>

          <div class="status-pill" :class="{ 'is-active': uploading }" role="status" aria-live="polite">
            <div class="status-dot"></div>
            <span class="status-text">{{ systemStatusText }}</span>
          </div>
        </div>
      </div>
    </header>
    <main class="main-container">
      <div v-if="sessionLoading || sessionError" class="service-notice" :class="{ error: sessionError }" :role="sessionError ? 'alert' : 'status'">
        <p>{{ sessionLoading ? '正在连接本机服务…' : sessionError }}</p>
        <button v-if="sessionError" type="button" @click="initializeSession">重新连接</button>
      </div>
      <section class="hero-section">
        <div class="hero-eyebrow"><span></span> 每一份笔记，都能找到出处</div>
        <h1 class="slogan-main">把课程变成<br /><span>可追问的笔记</span></h1>
        <p class="slogan-sub">上传一节课，把重点留下来。用时间戳找回讲解，带着问题继续学。</p>

        <div class="upload-wrapper">
          <input
              type="file"
              id="file-input"
              @change="handleFileChange"
              accept=".mp4,.mov,.mkv,.avi,.webm,.m4v"
              :disabled="sessionLoading || !currentUser"
              hidden
          />

          <div
              class="upload-magnet"
              :class="{ 'processing': uploading, 'is-dragover': isDragOver }"
              @dragenter.prevent="handleDragEnter"
              @dragover.prevent="isDragOver = true"
              @dragleave.prevent="handleDragLeave"
              @drop.prevent="handleDrop"
          >
            <div class="split-container" v-if="!uploading">

              <label for="file-input" class="upload-pane pane-local" :aria-disabled="sessionLoading || !currentUser" :tabindex="sessionLoading || !currentUser ? -1 : 0" role="button" aria-label="选择本地课程视频" @keydown.enter.prevent="chooseVideoFile" @keydown.space.prevent="chooseVideoFile">
                <div class="pane-content">
                  <div class="magnet-icon">
                    <svg width="42" height="42" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
                  </div>
                  <span class="magnet-title">上传课程视频</span>
                  <span class="magnet-desc">{{ isDragOver ? '松手上传' : '点击选择文件，或拖拽到这里' }}</span>
                </div>
              </label>

              <div class="split-gap"></div>

              <div class="upload-pane pane-url">
                <div class="pane-content">
                  <div class="magnet-icon">
                    <svg width="42" height="42" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="2" y1="12" x2="22" y2="12"></line><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1 4-10z"></path></svg>
                  </div>
                  <span class="magnet-title">导入视频链接</span>
                  <span class="magnet-desc">粘贴视频地址，交给课迹整理</span>

                  <div class="url-input-box" @click.stop>
                    <input
                        v-model="videoUrl"
                        type="text"
                        inputmode="url"
                        autocomplete="off"
                        spellcheck="false"
                        placeholder="粘贴视频链接..."
                        aria-label="视频链接"
                        :disabled="uploading || sessionLoading || !currentUser"
                        @keyup.enter="handleUrlUpload"
                    />
                    <button class="url-go-btn" :disabled="uploading" @click="handleUrlUpload" aria-label="解析视频链接">
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"></polyline></svg>
                    </button>
                  </div>
                </div>
              </div>

            </div>

            <div class="magnet-content busy" v-else>
              <div class="quantum-loader"></div>
              <span class="busy-text">{{ uploadProgress.label }}</span>
              <span v-if="uploadProgress.filename" class="busy-file">{{ uploadProgress.filename }}</span>
              <div
                  v-if="uploadProgress.percent !== null"
                  class="upload-progress"
                  role="progressbar"
                  aria-label="视频上传进度"
                  aria-valuemin="0"
                  aria-valuemax="100"
                  :aria-valuenow="uploadProgress.percent"
              >
                <span :style="{ width: `${uploadProgress.percent}%` }"></span>
              </div>
              <span v-if="uploadProgress.detail" class="busy-stat" aria-live="polite">{{ uploadProgress.detail }}</span>
              <span v-if="uploadProgress.warning" class="busy-warning" role="status">{{ uploadProgress.warning }}</span>
              <div v-if="uploadAbort" class="busy-actions">
                <button type="button" @click="cancelUpload">取消上传</button>
              </div>
            </div>

            <div class="border-glow"></div>
          </div>

          <p class="upload-footnote">支持 MP4、MOV、MKV 等常见格式 · 原视频与笔记一起保存</p>
          <div v-if="resumableFile && !uploading" class="upload-resume" role="status">
            <span>{{ resumeHint }}</span>
            <button type="button" @click="resumeUpload">继续上传</button>
            <button type="button" @click="discardResumableUpload">重新开始</button>
          </div>
        </div>
        <transition name="toast-pop">
          <div
              v-if="message"
              class="notification-bar"
              :class="{ 'error': messageIsError }"
              :role="messageIsError ? 'alert' : 'status'"
              :aria-live="messageIsError ? 'assertive' : 'polite'"
              :title="messageIsError ? '点击关闭这条提示' : null"
              @click="dismissMessage"
          >
            {{ message }}
          </div>
        </transition>
      </section>

      <div class="learning-steps" aria-label="使用步骤">
        <div><span class="step-number">01</span><div><strong>留住课程</strong><p>上传视频或导入链接</p></div></div>
        <div><span class="step-number">02</span><div><strong>整理重点</strong><p>生成笔记，标注证据出处</p></div></div>
        <div><span class="step-number">03</span><div><strong>继续理解</strong><p>定位原文，围绕课程追问</p></div></div>
      </div>
      <section v-if="list.length > 0" class="workspace-section">
        <div class="section-header">
          <div class="library-title">
            <h3>视频资料库</h3>
            <div class="count-chip">{{ list.length }} 个视频</div>
          </div>
          <label class="library-search">
            <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>
            <input v-model="searchQuery" type="search" placeholder="搜索视频名称" aria-label="搜索视频名称" />
          </label>
        </div>
        <div class="card-grid">
          <div v-for="item in visibleList" :key="item.id" class="project-card">

            <button
                class="delete-btn"
                :disabled="deletingId === item.id"
                :title="deletingId === item.id ? '正在删除…' : '删除视频'"
                :aria-label="`删除 ${item.filename}`"
                @click.stop="deleteItem(item)"
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="3 6 5 6 21 6"></polyline><path d="M19 6l-1 14H6L5 6"></path><path d="M8 6V4h8v2"></path>
              </svg>
            </button>
            <div class="card-meta">
              <div class="meta-icon">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><polygon points="23 7 16 12 23 17 23 7"></polygon><rect x="1" y="5" width="15" height="14" rx="2" ry="2"></rect></svg>
              </div>
              <div class="meta-info">
                <div class="filename-mask" :title="item.filename">{{ item.filename }}</div>
                <div class="meta-tags">
                  <span class="time-tag" title="最近上传时间">{{ formatTime(item.uploadTime) }}</span>
                  <span
                      class="status-indicator"
                      :class="cardStatusClass(item)"
                      :title="cardStatusTitle(item)"
                  >
                    {{ cardStatusLabel(item) }}
                  </span>
                </div>
              </div>
            </div>

            <div class="action-dock">
              <button
                  class="dock-item"
                  :disabled="item.status !== 'COMPLETED'"
                  :title="actionTitle(item, '下载音频')"
                  @click="downloadAudio(item)"
              >
                <span class="item-icon">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18V5l12-2v13"></path><circle cx="6" cy="18" r="3"></circle><circle cx="18" cy="16" r="3"></circle></svg>
                </span>
                <span class="item-label">下载音频</span>
              </button>

              <button
                  class="dock-item"
                  :disabled="item.status !== 'COMPLETED'"
                  :title="actionTitle(item, '提取文字')"
                  @click="transcribe(item.id)"
              >
                <span class="item-icon">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>
                </span>
                <span class="item-label">提取文字</span>
              </button>

              <button
                  class="dock-item ai-core"
                  :disabled="item.status !== 'COMPLETED'"
                  :title="actionTitle(item, '打开 课迹 Agent')"
                  @click="openAgent(item)"
              >
                <span class="item-icon">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="4" width="16" height="16" rx="2" ry="2"></rect><rect x="9" y="9" width="6" height="6"></rect><line x1="9" y1="1" x2="9" y2="4"></line><line x1="15" y1="1" x2="15" y2="4"></line><line x1="9" y1="20" x2="9" y2="23"></line><line x1="15" y1="20" x2="15" y2="23"></line><line x1="20" y1="9" x2="23" y2="9"></line><line x1="20" y1="14" x2="23" y2="14"></line><line x1="1" y1="9" x2="4" y2="9"></line><line x1="1" y1="14" x2="4" y2="14"></line></svg>
                </span>
                <div class="label-group">
                  <span class="item-label">课迹 Agent</span>
                </div>
                <div class="shimmer"></div>
              </button>
            </div>
          </div>
        </div>
        <div v-if="visibleList.length === 0" class="library-empty">
          <p>没有找到“{{ searchQuery }}”</p>
          <button type="button" @click="searchQuery = ''">清除搜索</button>
        </div>
      </section>

      <div v-if="currentUser && (listError || !list.length)" class="library-empty" role="status">
        <p>{{ listLoading ? '正在加载视频资料库…' : listError || '还没有视频，上传本地文件或粘贴视频链接即可开始分析。' }}</p>
        <button v-if="listError" type="button" :disabled="listLoading" @click="fetchList({ notify: true })">重新加载</button>
      </div>

      <div class="sidebar-backdrop" v-if="sidebar.visible" @click="closeSidebar"></div>
      <div
          ref="sidebarPanel"
          class="sidebar-panel"
          :class="{ 'is-open': sidebar.visible }"
          :inert="!sidebar.visible"
          role="dialog"
          aria-modal="true"
          tabindex="-1"
          :aria-label="sidebar.title || '任务详情'"
          @keydown="trapPanelFocus($event, sidebarPanel)"
      >
        <div class="sidebar-header">
          <div class="sidebar-title">
            <span class="icon" v-if="sidebar.type === 'ai'">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M2 12h2"></path><path d="M20 12h2"></path><path d="M12 2v2"></path><path d="M12 20v2"></path><path d="M20.2 6.47l-1.4 1.4"></path><path d="M15.9 5.35l-1.4-1.4"></path><path d="M9 11a3 3 0 1 0 6 0a3 3 0 0 0-6 0"></path></svg>
            </span>
            <span class="icon" v-else>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>
            </span>
            {{ sidebar.title }}
          </div>
          <button class="close-btn" @click="closeSidebar" aria-label="关闭分析面板">×</button>
        </div>
        <div ref="sidebarBody" class="sidebar-body">
          <div v-if="sidebar.type === 'ai'" v-show="!sidebar.presenting" class="video-evidence" :class="{ 'video-evidence-compact': sidebar.mode === 'compose' }">
            <video
                v-if="sidebar.playbackUrl"
                ref="videoPlayer"
                :src="sidebar.playbackUrl"
                controls
                playsinline
                preload="metadata"
                @error="handlePlaybackError"
            ></video>
            <div v-else-if="sidebar.playbackLoading" class="video-evidence-loading">正在载入原视频...</div>
            <div v-else-if="sidebar.playbackError" class="video-evidence-error" role="alert">
              <span>{{ sidebar.playbackError }}</span>
              <button type="button" @click="retryPlayback">重新加载</button>
            </div>
            <p v-if="sidebar.playbackUrl">点击时间戳回到原视频的识别片段；当前按分钟定位，并非逐句字幕时间</p>
          </div>
          <div v-if="sidebar.type === 'ai' && sidebar.mode === 'compose'" class="agent-composer">
            <p class="agent-caption">选择分析模式（决定产物形态）</p>
            <div class="goal-presets agent-mode-row">
              <button
                  v-for="m in analysisModes"
                  :key="m.value"
                  :class="{ active: sidebar.analysisMode === m.value }"
                  @click="sidebar.analysisMode = m.value"
              >
                <strong>{{ m.title }}</strong>
                <span>{{ m.description }}</span>
              </button>
            </div>
            <p class="agent-caption">告诉 Agent 你希望从视频中得到什么产物</p>
            <p v-if="sidebar.error" class="inline-error" role="alert">{{ sidebar.error }}</p>
            <textarea
                v-model="sidebar.goal"
                aria-label="分析目标"
                maxlength="500"
                placeholder="例如：梳理核心观点，给出带时间戳的证据和可执行建议（Ctrl / ⌘ + Enter 提交）"
                @keydown.ctrl.enter.prevent="submitAgent"
                @keydown.meta.enter.prevent="submitAgent"
            ></textarea>
            <p v-if="sidebar.goal.length > 400" class="field-counter">已输入 {{ sidebar.goal.length }} / 500 字</p>
            <div class="goal-presets">
              <button
                  v-for="preset in goalPresets"
                  :key="preset.title"
                  :class="{ active: sidebar.goal === preset.prompt }"
                  @click="sidebar.goal = preset.prompt"
              >
                <strong>{{ preset.title }}</strong>
                <span>{{ preset.description }}</span>
              </button>
            </div>
            <label class="process-choice"><input type="checkbox" v-model="sidebar.showProcess" />展示处理过程</label>
            <p class="process-choice-note">已有结果也会逐步展示处理阶段，再打开报告。关闭此选项可直接查看。</p>
            <button class="agent-run-btn" :disabled="sidebar.historyLoading || !sidebar.goal.trim()" @click="submitAgent">
              {{ sidebar.historyLoading ? '正在检查已有报告…' : sidebar.error ? '提交分析' : '开始分析' }}
            </button>
          </div>

          <AnalysisProgress v-else-if="sidebar.presenting" :steps="sidebar.presentationSteps" :index="sidebar.presentationIndex" :requested-at="sidebar.requestedAt" @skip="skipPresentation" />

          <div v-else-if="sidebar.loading" class="agent-running">
            <div class="loading-state">
              <div class="quantum-loader small"></div>
              <p aria-live="polite">{{ loadingHeadline }}</p>
              <p v-if="sidebar.streamOffline" class="stream-offline" role="status">
                连接中断，正在自动重连（第 {{ sidebar.streamRetry }} 次）· 任务仍在服务端继续
              </p>
              <p class="loading-hint">可以关闭本面板，任务会在后台继续，完成后会通知你</p>
            </div>
            <div v-if="sidebar.plan?.tasks?.length" class="agent-meta-block">
              <span class="meta-label">任务计划</span>
              <ol><li v-for="task in sidebar.plan.tasks" :key="task">{{ task }}</li></ol>
            </div>
            <div v-if="traceStages.length" class="agent-meta-block">
              <span class="meta-label">已完成阶段</span>
              <div class="stage-list"><span v-for="stage in traceStages" :key="stage[0]">{{ stage[0] }} · {{ stage[1] }}</span></div>
            </div>
          </div>

          <div v-else>
            <div v-if="sidebar.type === 'ai'">
              <p v-if="sidebar.error" class="inline-error" role="alert">{{ sidebar.error }}</p>
              <div class="result-actions">
                <button type="button" @click="startNewAnalysis">重新选择目标</button>
                <button type="button" :disabled="!sidebar.content" @click="copyResult">复制结果</button>
                <button type="button" :disabled="!sidebar.content" @click="downloadResult">导出 Markdown</button>
              </div>
              <div class="evidence-search">
                <div class="evidence-search-form">
                  <input
                      v-model="sidebar.evidenceQuery"
                      aria-label="视频证据检索"
                      maxlength="500"
                      placeholder="定位 PPT、字幕、代码或某段讲解"
                      @keyup.enter="searchEvidence"
                  />
                  <button type="button" :disabled="sidebar.evidenceLoading || !sidebar.evidenceQuery.trim()" @click="searchEvidence">
                    {{ sidebar.evidenceLoading ? '检索中' : '定位证据' }}
                  </button>
                </div>
                <p v-if="sidebar.evidenceError" class="evidence-search-error" aria-live="polite">{{ sidebar.evidenceError }}</p>
                <div v-if="sidebar.evidenceResults.length" class="evidence-search-results" aria-live="polite">
                  <button
                      v-for="hit in sidebar.evidenceResults"
                      :key="`${hit.startMs}-${hit.endMs}`"
                      type="button"
                      :title="hit.snippet || '该时间段暂无可展示文本'"
                      @click="seekToEvidence(hit.startMs)"
                  >
                    <strong>{{ formatEvidenceTime(hit.startMs) }}</strong>
                    <small>{{ hit.source || '视频证据' }}</small>
                    <span>{{ hit.snippet || '该时间段暂无可展示文本' }}</span>
                  </button>
                </div>
              </div>
              <div v-if="sidebar.requestedAt" class="reuse-notice" role="status"><p>本次请求：{{ formatRecordTime(sidebar.requestedAt) }}</p><p v-if="sidebar.reusedResult" class="reuse-event">结果来源：已保存的分析记录</p></div>
              <div class="markdown-content" v-html="renderedMarkdown" @click="seekEvidence"></div>
              <details class="agent-inspector">
                <summary>分析详情</summary>
                <div class="agent-inspector-content">
                <div class="agent-meta-block">
                  <span class="meta-label">最近一次分析记录</span>
                  <p class="process-record-note">以下为该结果对应的实际执行时间与阶段。</p>
                  <p v-if="sidebar.processRecordsError" class="process-record-note" role="status">{{ sidebar.processRecordsError }}</p>
                  <ol v-else-if="originalProcessRecords.length" class="process-record-list">
                    <li v-for="record in originalProcessRecords" :key="record.id">
                      <div><time :datetime="record.createdAt">{{ formatRecordTime(record.createdAt) }}</time><span>{{ record.stage || '任务状态' }} · {{ record.state }}</span></div>
                      <p>{{ record.message }}</p>
                    </li>
                  </ol>
                  <p v-else class="process-record-note">没有保存历史过程记录；已保存的材料、分析计划和报告仍可查看。</p>
                </div>
                <div v-if="sidebar.plan?.tasks?.length" class="agent-meta-block">
                  <span class="meta-label">分析计划</span>
                  <ol><li v-for="task in sidebar.plan.tasks" :key="task">{{ task }}</li></ol>
                </div>
                <div v-if="traceStages.length" class="agent-meta-block">
                  <span class="meta-label">服务端阶段耗时</span>
                  <div class="stage-list"><span v-for="stage in traceStages" :key="stage[0]">{{ stage[0] }} · {{ stage[1] }}</span></div>
                </div>
                <div v-if="sidebar.evaluation && Object.keys(sidebar.evaluation).length" class="quality-row">
                  <span>结构完整 {{ sidebar.evaluation.structuredValid ? '通过' : '待完善' }}</span>
                  <span>原文匹配 {{ formatPercent(sidebar.evaluation.evidenceSupportRate) }}</span>
                  <span>质量检查 {{ sidebar.evaluation.criticPassed ? '通过' : '未通过，需核验' }}</span>
                </div>
                </div>
              </details>
              <div class="follow-up-suggestions">
                <button type="button" @click="sidebar.followUp = SAMPLE_FOLLOW_UP">试着问：反复看笔记为什么会产生错觉？</button>
                <p>相同问题可读取后端缓存；新问题可能需要调用模型。</p>
              </div>
              <div class="follow-up-box">
                <textarea
                    v-model="sidebar.followUp"
                    aria-label="基于视频继续追问"
                    maxlength="500"
                    placeholder="基于视频继续追问...（Ctrl / ⌘ + Enter 发送）"
                    @keydown.ctrl.enter.prevent="submitFollowUp"
                    @keydown.meta.enter.prevent="submitFollowUp"
                ></textarea>
                <button :disabled="sidebar.followUpLoading || !sidebar.followUp.trim()" @click="submitFollowUp">
                  {{ sidebar.followUpLoading ? '分析中' : '追问' }}
                </button>
              </div>
              <div class="feedback-row">
                <span>这个结果有帮助吗？</span>
                <button :disabled="sidebar.feedbackLoading" :class="{ active: sidebar.feedback === 1 }" :aria-pressed="sidebar.feedback === 1" @click="sendFeedback(1)" title="有帮助">赞</button>
                <button :disabled="sidebar.feedbackLoading" :class="{ active: sidebar.feedback === -1 }" :aria-pressed="sidebar.feedback === -1" @click="sendFeedback(-1)" title="需改进">踩</button>
              </div>
            </div>
            <div v-else class="text-content">
              <p v-if="sidebar.error" class="inline-error" role="alert">{{ sidebar.error }}</p>
              <template v-if="sidebar.content">
                <div class="result-actions">
                  <button type="button" @click="copyResult">复制全文</button>
                  <button type="button" @click="downloadResult">导出文本</button>
                </div>
                <p class="text-meta">{{ transcriptMeta }}</p>
                <pre>{{ sidebar.content }}</pre>
              </template>
              <p v-else-if="!sidebar.error" class="text-meta">这个视频还没有可展示的转写文本。</p>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import AnalysisProgress from './AnalysisProgress.vue'
import { latestAnalysisRecords } from './analysisPresentation.js'
import { computed, nextTick, ref, watch, onMounted, onUnmounted } from 'vue'
import { apiRequest, captureAuthSession, clearAuthToken, setAuthToken } from './api'
import {
  forgetUploadProgress,
  formatBytes,
  formatDurationText,
  hasUploadProgress,
  uploadVideoInChunks,
  validateVideoFile
} from './chunkUpload'
import { createTaskStreams } from './taskEvents'
import { useAnalysisWorkspace, SAMPLE_FOLLOW_UP } from './useAnalysisWorkspace'
import { requestLocalSession } from './localSession.js'

// 主题偏好独立于会话；浏览器禁止存储时，仍可正常切换。
const THEME_KEY = 'coursetrace-theme'
function readTheme() {
  try { return window.localStorage.getItem(THEME_KEY) === 'dark' ? 'dark' : 'light' }
  catch { return 'light' }
}
const theme = ref(readTheme())
watch(theme, value => {
  document.documentElement.dataset.theme = value
  const themeColor = document.querySelector('meta[name="theme-color"]')
  if (themeColor) themeColor.content = value === 'dark' ? '#161a25' : '#f7f8fc'
  try { window.localStorage.setItem(THEME_KEY, value) } catch { /* 临时模式也能使用 */ }
}, { immediate: true })
function toggleTheme() { theme.value = theme.value === 'light' ? 'dark' : 'light' }
function chooseVideoFile() { if (currentUser.value && !sessionLoading.value) document.getElementById('file-input')?.click() }

// --- 变量定义 ---
const MESSAGE_TIMEOUT_MS = 4000
const file = ref(null)
const videoUrl = ref('')
const message = ref('')
const messageIsError = ref(false)
const uploading = ref(false)
const uploadProgress = ref({ label: '准备上传', filename: '', percent: null, detail: '', warning: '' })
const uploadAbort = ref(null)
const resumableFile = ref(null)
const resumableChunks = ref({ done: 0, total: 0 })
const list = ref([])
const listLoading = ref(false)
const listError = ref('')
let listRequestVersion = 0
let uploadRequestVersion = 0
let sessionRequestVersion = 0
const searchQuery = ref('')
const videoPlayer = ref(null)
const sidebarPanel = ref(null)
const sidebarBody = ref(null)
const deletingId = ref(null)
const isOffline = ref(typeof navigator !== 'undefined' && navigator.onLine === false)
const activeTasks = ref([])
const visibleList = computed(() => {
  const query = searchQuery.value.trim().toLocaleLowerCase()
  if (!query) return list.value
  return list.value.filter(item => item.filename?.toLocaleLowerCase().includes(query))
})
const isDragOver = ref(false)
const currentUser = ref(null)
const sessionLoading = ref(true)
const sessionError = ref('')
let sessionAbort = null
const taskStreams = createTaskStreams({
  onActiveChange: tasks => { activeTasks.value = tasks }
})
let dragDepth = 0
let messageTimer = null
let lastUploadProgress = {}
let focusBeforeSidebar = null

// --- 状态展示 ---
const activeTaskOf = mediaId => activeTasks.value.find(task => String(task.id) === String(mediaId))

const systemStatusText = computed(() => {
  if (isOffline.value) return '网络已断开'
  if (sessionLoading.value) return '正在连接服务'
  if (sessionError.value) return '服务连接失败'
  if (uploading.value) {
    return uploadProgress.value.percent !== null
      ? `上传 ${uploadProgress.value.percent}%`
      : '处理中'
  }
  if (activeTasks.value.length) return `后台任务 ${activeTasks.value.length}`
  return '系统就绪'
})

const cardStatusClass = item => {
  if (activeTaskOf(item.id)) return 'processing'
  return mediaStatusClass(item.status)
}
const cardStatusLabel = item => {
  const task = activeTaskOf(item.id)
  if (task) return task.type === 'ai' ? 'ANALYZING' : 'TRANSCRIBING'
  return mediaStatusLabel(item.status)
}
const cardStatusTitle = item => {
  const task = activeTaskOf(item.id)
  if (!task) return null
  return task.type === 'ai'
    ? 'AI 分析正在后台执行，完成后会提示你'
    : '文字提取正在后台执行，完成后会提示你'
}
const actionTitle = (item, label) => item.status === 'COMPLETED'
  ? null
  : `视频尚未处理完成，暂时无法${label}`

const loadingHeadline = computed(() => {
  const fallback = sidebar.value.type === 'ai'
    ? 'Agent 正在分析视频证据'
    : '正在识别视频语音'
  return sidebar.value.statusMessage || fallback
})

const transcriptMeta = computed(() => {
  const length = sidebar.value.content?.length || 0
  if (!length) return ''
  return `共 ${length.toLocaleString('zh-CN')} 字`
})

const resumeHint = computed(() => {
  const target = resumableFile.value
  if (!target) return ''
  const { done, total } = resumableChunks.value
  const progress = total ? `已完成 ${Math.round((done / total) * 100)}%` : '已保留上传进度'
  return `${target.name} ${progress}，可继续未完成的上传`
})

// --- 核心业务逻辑 ---

const handleDragEnter = () => {
  dragDepth += 1
  isDragOver.value = true
}

// 拖过子元素也会触发 dragleave，用进出计数避免提示文案反复闪烁。
const handleDragLeave = () => {
  dragDepth = Math.max(0, dragDepth - 1)
  if (!dragDepth) isDragOver.value = false
}

const resetDragState = () => {
  dragDepth = 0
  isDragOver.value = false
}

/** 统一入口：本机会话、格式、体积三道校验全部在进入上传态之前完成。 */
const startUpload = async (selectedFile, extraFileCount = 0) => {
  if (uploading.value) {
    showMsg('已有上传任务在进行，请等当前任务结束', true)
    return
  }
  if (!currentUser.value) {
    showMsg(sessionLoading.value ? '正在连接本机服务，请稍后再上传' : '服务连接失败，请点击重新连接', true)
    return
  }
  if (!selectedFile) return
  const invalid = validateVideoFile(selectedFile)
  if (invalid) {
    showMsg(`⚠️ ${invalid}`, true)
    return
  }
  if (extraFileCount > 0) {
    showMsg(`一次只处理一个视频，已选择 ${selectedFile.name}，其余 ${extraFileCount} 个已忽略`)
  }
  file.value = selectedFile
  videoUrl.value = ''
  await uploadFile()
}

const handleFileChange = async (e) => {
  const selected = e.target.files
  await startUpload(selected?.[0], Math.max(0, (selected?.length || 0) - 1))
  e.target.value = ''
}

const handleDrop = async (e) => {
  resetDragState()
  const dropped = e.dataTransfer?.files
  if (!dropped?.length) return
  await startUpload(dropped[0], dropped.length - 1)
}

const buildUploadWarning = progress => {
  if (progress.retryingCount) {
    return `网络不稳定，正在重试 ${progress.retryingCount} 个分片（第 ${progress.retryAttempt}/${progress.retryMaxAttempts} 次）`
  }
  if (progress.resumedChunks) {
    return `已续传：跳过 ${progress.resumedChunks} 个此前完成的分片`
  }
  return ''
}

const applyUploadProgress = progress => {
  lastUploadProgress = progress
  const merging = progress.phase === 'merging'
  const detail = [`${formatBytes(progress.uploadedBytes)} / ${formatBytes(progress.totalBytes)}`]
  detail.push(`分片 ${progress.completedChunks}/${progress.totalChunks}`)
  if (!merging && progress.bytesPerSecond) {
    detail.push(`${formatBytes(progress.bytesPerSecond)}/s`)
    const eta = formatDurationText(progress.etaSeconds)
    if (eta) detail.push(`剩余约 ${eta}`)
  }
  uploadProgress.value = {
    label: merging ? '分片已全部送达，正在服务端合并' : '正在安全上传',
    filename: file.value?.name || uploadProgress.value.filename,
    percent: progress.percent,
    detail: detail.join(' · '),
    warning: buildUploadWarning(progress)
  }
}

const rememberResumableUpload = target => {
  if (!target || !hasUploadProgress(target, currentUser.value?.id)) {
    resumableFile.value = null
    return
  }
  resumableFile.value = target
  resumableChunks.value = {
    done: lastUploadProgress.completedChunks || 0,
    total: lastUploadProgress.totalChunks || 0
  }
}

const uploadFile = async () => {
  const target = file.value
  if (!target) return

  const controller = new AbortController()
  const requestVersion = ++uploadRequestVersion
  const isCurrentSession = captureAuthSession()
  const isCurrentUpload = () => requestVersion === uploadRequestVersion && isCurrentSession()
  uploadAbort.value = controller
  uploading.value = true
  resumableFile.value = null
  lastUploadProgress = {}
  const uploadUserId = currentUser.value?.id
  uploadProgress.value = {
    label: hasUploadProgress(target, uploadUserId) ? '正在核对已上传分片' : '准备分片上传',
    filename: target.name,
    percent: 0,
    detail: `0 B / ${formatBytes(target.size)}`,
    warning: ''
  }

  try {
    const uploadedMedia = await uploadVideoInChunks(target, progress => {
      if (isCurrentUpload()) applyUploadProgress(progress)
    }, controller.signal, uploadUserId)
    if (!isCurrentUpload()) return
    resumableFile.value = null
    showMsg(`${target.name} 上传完成`)
    await fetchList({ notify: true })
    if (!isCurrentUpload()) return
    openAgent(uploadedMedia, { useDefaultGoal: true })
  } catch (error) {
    if (!isCurrentUpload()) return
    rememberResumableUpload(target)
    if (error?.aborted) {
      showMsg('上传已取消，进度已保留，可点“继续上传”接着传')
      return
    }
    console.error(error)
    showMsg(
      resumableFile.value
        ? `❌ 上传中断：${error.message}（进度已保留，可继续上传）`
        : `❌ 上传失败：${error.message}`,
      true
    )
  } finally {
    if (isCurrentUpload()) {
      uploading.value = false
      uploadAbort.value = null
      file.value = null
    }
  }
}

const cancelUpload = () => {
  if (!uploadAbort.value) return
  uploadProgress.value = { ...uploadProgress.value, label: '正在取消上传', warning: '' }
  uploadAbort.value.abort()
}

const resumeUpload = async () => {
  const target = resumableFile.value
  if (!target || uploading.value) return
  file.value = target
  await uploadFile()
}

const discardResumableUpload = () => {
  forgetUploadProgress(resumableFile.value, currentUser.value?.id)
  resumableFile.value = null
  resumableChunks.value = { done: 0, total: 0 }
  showMsg('已清除保留的上传进度，下次将从头开始')
}

const handleUrlUpload = async () => {
  const normalizedUrl = videoUrl.value.trim()
  if (!normalizedUrl) return
  if (uploading.value) {
    showMsg('已有上传任务在进行，请等当前任务结束', true)
    return
  }

  if (!currentUser.value) {
    showMsg(sessionLoading.value ? '正在连接本机服务，请稍后再上传' : '服务连接失败，请点击重新连接', true)
    return
  }

  let parsedUrl
  try {
    parsedUrl = new URL(normalizedUrl)
  } catch {
    parsedUrl = null
  }
  if (!parsedUrl || !['http:', 'https:'].includes(parsedUrl.protocol)) {
    showMsg('⚠️ 请输入合法的 http/https 链接', true)
    return
  }

  uploading.value = true
  const requestVersion = ++uploadRequestVersion
  const isCurrentSession = captureAuthSession()
  const isCurrentUpload = () => requestVersion === uploadRequestVersion && isCurrentSession()
  uploadProgress.value = {
    label: '正在解析视频链接',
    filename: parsedUrl.hostname,
    percent: null,
    detail: '服务端正在拉取源视频，时长取决于源站速度',
    warning: ''
  }
  messageIsError.value = false
  message.value = '正在解析链接并极速下载 (低码率模式)...'

  const formData = new FormData()
  formData.append('url', normalizedUrl)

  try {
    const res = await apiRequest('/media/upload-url', {
      method: 'POST',
      body: formData
    })
    if (!res.ok) throw new Error(await res.text())
    const uploadedMedia = await res.json()
    if (!isCurrentUpload()) return

    showMsg('链接资源已入库')
    videoUrl.value = ''
    await fetchList({ notify: true })
    if (!isCurrentUpload()) return
    openAgent(uploadedMedia, { useDefaultGoal: true })
  } catch (error) {
    console.error(error)
    if (!isCurrentUpload()) return
    let errMsg = error.message
    if (errMsg.includes("Unsupported URL")) errMsg = "不支持该平台链接"
    showMsg('❌ 解析失败: ' + errMsg, true)
  } finally {
    if (isCurrentUpload()) uploading.value = false
  }
}

/** 成功提示自动消失；错误提示保留到用户点掉，避免关键失败原因 4 秒后就没了。 */
const showMsg = (msg, isError = false) => {
  clearTimeout(messageTimer)
  messageTimer = null
  message.value = msg
  messageIsError.value = isError
  if (isError) return
  messageTimer = setTimeout(() => {
    if (message.value !== msg) return
    message.value = ''
    messageIsError.value = false
  }, MESSAGE_TIMEOUT_MS)
}

const dismissMessage = () => {
  if (!messageIsError.value) return
  clearTimeout(messageTimer)
  messageTimer = null
  message.value = ''
  messageIsError.value = false
}

const fetchList = async ({ notify = false } = {}) => {
  const requestVersion = ++listRequestVersion
  const isCurrentSession = captureAuthSession()
  const isCurrentRequest = () => requestVersion === listRequestVersion && isCurrentSession()
  if (!currentUser.value) {
    list.value = []
    return list.value
  }
  listLoading.value = true
  listError.value = ''
  try {
    // 带时间戳绕开浏览器缓存，避免删除/新增之后列表还是旧的。
    const res = await apiRequest(`/media/list?_t=${Date.now()}`)
    if (res.status === 401) return null
    if (!res.ok) throw new Error('加载视频列表失败')
    const media = await res.json()
    if (!isCurrentRequest()) return null
    list.value = media
  } catch (error) {
    if (!isCurrentRequest()) return null
    console.error(error)
    listError.value = '视频资料库加载失败，请稍后重试'
    if (notify) showMsg('视频资料库加载失败，请稍后刷新', true)
    return null
  } finally {
    if (isCurrentRequest()) listLoading.value = false
  }
  return list.value
}

const mediaStatusClass = status => ['COMPLETED', 'PROCESSING', 'FAILED'].includes(status)
  ? status.toLowerCase()
  : 'unknown'
const mediaStatusLabel = status => ({
  COMPLETED: 'READY',
  PROCESSING: 'PROCESSING',
  FAILED: 'FAILED'
})[status] || 'PENDING'

const {
  sidebar,
  goalPresets,
  analysisModes,
  traceStages,
  renderedMarkdown,
  transcribe,
  closeSidebar,
  openAgent,
  submitAgent,
  skipPresentation,
  startNewAnalysis,
  submitFollowUp,
  searchEvidence,
  sendFeedback,
  retryPlayback,
  handlePlaybackError,
  resetWorkspace,
  discardMediaWorkspace,
  formatPercent
} = useAnalysisWorkspace({
  taskStreams,
  showMessage: showMsg,
  refreshMediaList: fetchList,
  findMediaItem: id => list.value.find(item => item.id === id),
  onAnswerAppended: () => scrollToLatestAnswer()
})

const originalProcessRecords = computed(() => latestAnalysisRecords(sidebar.value.processRecords))

/** 追问的答案追加在长文末尾，主动滚过去，否则用户会以为“点了没反应”。 */
const scrollToLatestAnswer = async () => {
  await nextTick()
  const container = sidebarBody.value?.querySelector('.markdown-content')
  if (!container) return
  const headings = container.querySelectorAll('h2, h3')
  const anchor = headings.length ? headings[headings.length - 1] : container.lastElementChild
  anchor?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

const seekVideo = seconds => {
  if (!Number.isFinite(seconds)) return
  const player = videoPlayer.value
  if (!player) {
    if (sidebar.value.playbackError) {
      showMsg('原视频加载失败，无法跳转，可先点“重新加载”', true)
    } else if (sidebar.value.playbackLoading) {
      showMsg('原视频还在载入，稍等一下再点这个时间戳')
    } else {
      showMsg('这个视频暂时没有可播放的原片，无法跳转', true)
    }
    return
  }
  if (player.readyState === 0) {
    player.addEventListener('loadedmetadata', () => seekVideo(seconds), { once: true })
    return
  }
  const duration = player.duration
  const maxTime = Number.isFinite(duration) ? Math.max(0, duration - 0.1) : Number.MAX_SAFE_INTEGER
  player.currentTime = Math.min(Math.max(0, seconds), maxTime)
  player.play().catch(() => {})
  player.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
}

const seekEvidence = event => {
  const link = event.target.closest('a[href^="#video-t="]')
  if (!link) return
  event.preventDefault()
  seekVideo(Number(link.getAttribute('href').split('=')[1]))
}

const seekToEvidence = timestampMs => seekVideo(Number(timestampMs) / 1000)
const formatEvidenceTime = timestampMs => {
  const seconds = Math.max(0, Math.floor(Number(timestampMs) / 1000))
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const time = `${String(minutes).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
  return hours ? `${String(hours).padStart(2, '0')}:${time}` : time
}

/** Clipboard API 在非 HTTPS 环境不可用，这里保留一条降级路径，避免“复制失败”变成死路。 */
const copyToClipboard = async text => {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    // 继续走下面的降级方案。
  }
  try {
    const scratch = document.createElement('textarea')
    scratch.value = text
    scratch.setAttribute('readonly', '')
    scratch.style.position = 'fixed'
    scratch.style.top = '0'
    scratch.style.opacity = '0'
    document.body.appendChild(scratch)
    scratch.select()
    const copied = document.execCommand('copy')
    document.body.removeChild(scratch)
    return copied
  } catch {
    return false
  }
}

const copyResult = async () => {
  const content = sidebar.value.content
  if (!content) {
    showMsg('还没有可复制的内容', true)
    return
  }
  const label = sidebar.value.type === 'ai' ? '分析结果' : '转写全文'
  if (await copyToClipboard(content)) showMsg(`${label}已复制`)
  else showMsg('复制失败，请手动选中内容后复制', true)
}

const resultFileBaseName = () => {
  const title = sidebar.value.title || ''
  const raw = title.split(' · ').slice(1).join(' · ') || title
  const cleaned = raw.replace(/\.[^/.]+$/, '').replace(/[\\/:*?"<>|]/g, '_').trim()
  return cleaned || (sidebar.value.type === 'ai' ? 'analysis' : 'transcript')
}

const downloadResult = () => {
  const content = sidebar.value.content
  if (!content) {
    showMsg('还没有可导出的内容', true)
    return
  }
  const isMarkdown = sidebar.value.type === 'ai'
  const blob = new Blob([content], {
    type: isMarkdown ? 'text/markdown;charset=utf-8' : 'text/plain;charset=utf-8'
  })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${resultFileBaseName()}.${isMarkdown ? 'md' : 'txt'}`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  // 立刻 revoke 在部分浏览器会导致下载拿不到内容，延后一拍更稳。
  setTimeout(() => URL.revokeObjectURL(url), 0)
  showMsg(`已导出 ${link.download}`)
}

const deleteItem = async (item) => {
  if (deletingId.value) return
  const runningTask = activeTaskOf(item.id)
  const warning = runningTask
    ? '\n\n注意：该视频还有任务正在后台执行，删除后这次的结果会丢失。'
    : ''
  if (!confirm(`确认要永久删除 "${item.filename}" 吗？${warning}`)) return
  deletingId.value = item.id
  const isCurrentSession = captureAuthSession()
  try {
    const res = await apiRequest(`/media/delete?id=${item.id}`, { method: 'DELETE' })
    const text = await res.text()
    if (!isCurrentSession()) return
    if (res.ok) {
      listRequestVersion += 1
      listLoading.value = false
      showMsg(`已删除 ${item.filename}`)
      list.value = list.value.filter(i => i.id !== item.id)
      discardMediaWorkspace(item.id)
    } else {
      showMsg('❌ ' + text, true)
    }
  } catch (e) {
    if (!isCurrentSession()) return
    showMsg('❌ 删除请求失败', true)
  } finally {
    if (isCurrentSession()) deletingId.value = null
  }
}

const formatRecordTime = value => {
  if (!value) return '时间未记录'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

const formatTime = (timeStr) => {
  if (!timeStr) return '--'
  const date = new Date(timeStr)
  if (Number.isNaN(date.getTime())) return '--'
  return `${date.getMonth() + 1}/${date.getDate()} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

const downloadAudio = async (item) => {
  let fileName = item.filename || 'audio.mp3';
  fileName = fileName.replace(/\.[^/.]+$/, "") + ".mp3";
  try {
    showMsg('正在转码并下载...')
    const res = await apiRequest(`/analysis/download?id=${item.id}`)
    // 失败时后端返回的是 JSON 信封，api.js 会把 message 解包给 text()，
    // 这里读出来向上抛，避免把“视频不存在 / 无权访问 / 转码失败”统一显示成同一句话。
    if (!res.ok) throw new Error((await res.text()) || '请稍后重试')
    const blob = await res.blob()
    const downloadUrl = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(downloadUrl)
    showMsg('✅ 下载完成')
  } catch (e) {
    showMsg('音频下载失败：' + (e?.message || '请稍后重试'), true)
  }
}

const restoreFocus = element => {
  if (element?.isConnected && typeof element.focus === 'function') element.focus()
}

const closeActiveOverlay = () => { if (sidebar.value.visible) closeSidebar() }
const handleKeydown = event => {
  if (event.key === 'Escape') closeActiveOverlay()
}

/** 弹窗内循环 Tab，键盘用户不会一路跳到被遮住的背景里。 */
const trapPanelFocus = (event, panel) => {
  if (event.key !== 'Tab' || !panel) return
  const focusable = [...panel.querySelectorAll('button, input, textarea, select, a[href], summary, [tabindex]:not([tabindex="-1"])')]
    .filter(element => !element.disabled && element.offsetParent !== null)
  if (!focusable.length) return
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  const active = document.activeElement
  if (event.shiftKey && (active === first || active === panel || !panel.contains(active))) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && active === last) {
    event.preventDefault()
    first.focus()
  }
}

/** 本机会话失效时清理任务和媒体状态，再重新连接。 */
const resetSessionState = () => {
  sessionAbort?.abort()
  uploadRequestVersion += 1
  listRequestVersion += 1
  sessionRequestVersion += 1
  deletingId.value = null
  listLoading.value = false
  listError.value = ''
  uploadAbort.value?.abort()
  uploadAbort.value = null
  taskStreams.stopAll()
  resetWorkspace()
  currentUser.value = null
  list.value = []
  searchQuery.value = ''
  videoUrl.value = ''
  file.value = null
  resumableFile.value = null
  resumableChunks.value = { done: 0, total: 0 }
  uploading.value = false
}

const initializeSession = async () => {
  sessionAbort?.abort()
  const controller = new AbortController()
  sessionAbort = controller
  const version = ++sessionRequestVersion
  sessionLoading.value = true
  sessionError.value = ''
  try {
    clearAuthToken()
    const data = await requestLocalSession(controller.signal)
    if (version !== sessionRequestVersion || controller.signal.aborted) return
    setAuthToken(data.token)
    currentUser.value = data.userInfo
    await fetchList({ notify: true })
  } catch (error) {
    if (version !== sessionRequestVersion || controller.signal.aborted) return
    currentUser.value = null
    sessionError.value = error.message || '无法连接本机服务，请确认后端已启动'
  } finally {
    if (version === sessionRequestVersion) sessionLoading.value = false
  }
}

const handleAuthExpired = () => {
  resetSessionState()
  initializeSession()
}

const handleOnline = () => {
  isOffline.value = false
  showMsg('网络已恢复，正在同步最新状态')
  if (currentUser.value) fetchList()
  else initializeSession()
}

const handleOffline = () => {
  isOffline.value = true
  showMsg('网络已断开：上传会自动重试，后台任务会在恢复后继续', true)
}

// 上传中误关标签页会白丢已传分片，这里让浏览器先问一句。
const handleBeforeUnload = event => {
  if (!uploading.value) return
  event.preventDefault()
  event.returnValue = ''
}

// 打开弹层时挂上标记类，锁背景滚动的规则只在窄屏生效（见样式里的说明）。
const overlayOpen = computed(() => sidebar.value.visible)
watch(overlayOpen, open => {
  document.body.classList.toggle('overlay-open', open)
})

watch(() => sidebar.value.visible, async visible => {
  if (visible) {
    focusBeforeSidebar = document.activeElement
    await nextTick()
    sidebarPanel.value?.focus()
    return
  }
  restoreFocus(focusBeforeSidebar)
  focusBeforeSidebar = null
})

watch(() => sidebar.value.presenting, async presenting => {
  if (presenting) { videoPlayer.value?.pause(); await nextTick(); sidebarBody.value?.scrollTo({ top: 0, behavior: 'smooth' }) }
})

onMounted(() => {
  window.addEventListener('auth-expired', handleAuthExpired)
  window.addEventListener('keydown', handleKeydown)
  window.addEventListener('online', handleOnline)
  window.addEventListener('offline', handleOffline)
  window.addEventListener('beforeunload', handleBeforeUnload)
  initializeSession()
})
onUnmounted(() => {
  sessionAbort?.abort()
  uploadRequestVersion += 1
  listRequestVersion += 1
  sessionRequestVersion += 1
  resetWorkspace()
  window.removeEventListener('auth-expired', handleAuthExpired)
  window.removeEventListener('keydown', handleKeydown)
  window.removeEventListener('online', handleOnline)
  window.removeEventListener('offline', handleOffline)
  window.removeEventListener('beforeunload', handleBeforeUnload)
  clearTimeout(messageTimer)
  uploadAbort.value?.abort()
  document.body.classList.remove('overlay-open')
  taskStreams.stopAll()
})
</script>

<style>
:root {
  color-scheme: light;
  --page: #f7f8fc; --surface: #ffffff; --surface-soft: #f2f4fa; --ink: #242c42; --muted: #69738a;
  --line: #e4e8f1; --accent: #5e5cdb; --accent-hover: #4a47c3; --tint: #eeedfc;
  --teal: #167d79; --teal-tint: #eaf6f3; --danger: #bd3c51; --danger-tint: #fff0f2;
  --shadow: 0 12px 40px rgba(38, 47, 83, .055); --overlay: rgba(31, 37, 56, .32);
}
:root[data-theme="dark"] {
  color-scheme: dark;
  --page: #161a25; --surface: #202636; --surface-soft: #282f42; --ink: #e9edf8; --muted: #a6afc5;
  --line: #384157; --accent: #afa7ff; --accent-hover: #c4bfff; --tint: #33314f;
  --teal: #88d6c6; --teal-tint: #203c3a; --danger: #ffb0bb; --danger-tint: #462d38;
  --shadow: 0 12px 40px rgba(0, 0, 0, .15); --overlay: rgba(0, 0, 0, .55);
}
* { box-sizing: border-box; }
html, body, #app { margin: 0; min-height: 100%; background: var(--page); }
body { color: var(--ink); font-family: 'Noto Sans SC', 'Microsoft YaHei', system-ui, sans-serif; }
button, input, textarea { font: inherit; }
button { cursor: pointer; }
button, input, textarea, label[role="button"] { -webkit-tap-highlight-color: transparent; }
button:disabled { cursor: not-allowed; opacity: .5; }
button:focus-visible, input:focus-visible, textarea:focus-visible, label[role="button"]:focus-visible, summary:focus-visible, a:focus-visible { outline: 3px solid var(--accent); outline-offset: 3px; }
button { transition: background .15s, border-color .15s, color .15s; }
p, h1, h2, h3 { margin: 0; }
.app-stage { min-height: 100vh; }
.service-notice { display: flex; align-items: center; justify-content: space-between; gap: 14px; padding: 14px 18px; border-radius: 12px; background: var(--teal-tint); color: var(--teal); margin-bottom: 24px; font-size: 12px; line-height: 1.8; }
.service-notice.error { background: var(--danger-tint); color: var(--danger); }
.service-notice button { border: 1px solid var(--line); border-radius: 8px; padding: 8px 12px; color: var(--accent); background: var(--surface); white-space: nowrap; }
.navbar { position: sticky; top: 0; z-index: 100; background: var(--surface); border-bottom: 1px solid var(--line); }
.nav-content { max-width: 1240px; margin: auto; padding: 18px 32px; display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.brand { display: flex; align-items: center; gap: 7px; white-space: nowrap; }
.brand-mark { width: 36px; height: 36px; display: grid; place-items: center; border-radius: 11px; background: var(--accent); color: var(--surface); font-size: 19px; font-weight: 700; margin-right: 4px; }
.brand-name { font-size: 22px; font-weight: 800; letter-spacing: -.5px; }
.brand-agent { font-size: 19px; font-weight: 500; }
.brand-caption { font-size: 11px; color: var(--muted); margin-left: 9px; padding-left: 13px; border-left: 1px solid var(--line); }
.nav-controls, .user-profile { display: flex; align-items: center; gap: 12px; }
.theme-toggle { display: flex; align-items: center; justify-content: center; gap: 7px; border: 1px solid var(--line); color: var(--ink); border-radius: 10px; background: var(--surface); padding: 9px 12px; font-size: 12px; min-height: 38px; }
.theme-toggle:hover { background: var(--tint); border-color: var(--accent); }
.btn-icon, .item-icon { display: flex; }
.status-pill { display: flex; gap: 7px; align-items: center; font-size: 11px; color: var(--muted); padding: 8px 0 8px 9px; }
.status-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--teal); }
.status-pill.is-active .status-dot { animation: pulse 1.5s infinite; }
.main-container { max-width: 1180px; margin: auto; padding: 60px 32px 72px; }
.hero-section { text-align: center; }
.hero-eyebrow { color: var(--teal); font-size: 12px; display: inline-flex; align-items: center; gap: 8px; background: var(--teal-tint); padding: 7px 13px; border-radius: 99px; margin-bottom: 22px; }
.hero-eyebrow span { width: 5px; height: 5px; border-radius: 50%; background: currentColor; }
.slogan-main { font-size: clamp(34px, 4.6vw, 53px); line-height: 1.32; letter-spacing: -1.7px; font-weight: 800; }
.slogan-main span { color: var(--accent); }
.slogan-sub { font-size: 14px; line-height: 1.9; color: var(--muted); margin: 18px auto 32px; max-width: 580px; }
.upload-wrapper { max-width: 820px; margin: auto; }
.upload-magnet { background: var(--surface); border: 1px solid var(--line); border-radius: 20px; box-shadow: var(--shadow); overflow: hidden; }
.upload-magnet.is-dragover { border-color: var(--accent); background: var(--tint); }
.split-container { display: grid; grid-template-columns: 1fr 1fr; }
.split-gap, .border-glow, .shimmer { display: none; }
.upload-pane { display: flex; justify-content: center; min-height: 236px; padding: 28px 24px; }
.pane-local { border-right: 1px solid var(--line); cursor: pointer; }
.pane-local:hover { background: var(--tint); }
.pane-content { width: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; }
.magnet-icon { width: 52px; height: 52px; display: grid; place-items: center; background: var(--tint); color: var(--accent); border-radius: 16px; margin-bottom: 14px; }
.magnet-icon svg { width: 25px; height: 25px; }
.pane-url .magnet-icon { background: var(--teal-tint); color: var(--teal); }
.magnet-title { font-size: 16px; font-weight: 700; }
.magnet-desc { font-size: 12px; color: var(--muted); margin-top: 7px; }
.url-input-box { display: flex; align-items: center; width: 100%; max-width: 300px; margin-top: 19px; padding: 5px; border: 1px solid var(--line); border-radius: 11px; background: var(--surface-soft); }
.url-input-box:focus-within { border-color: var(--accent); }
.url-input-box input { width: 100%; min-width: 0; background: transparent; border: 0; color: var(--ink); padding: 7px; font-size: 12px; outline: none; }
.url-go-btn { flex-shrink: 0; width: 32px; height: 32px; display: grid; place-items: center; border: 0; border-radius: 7px; background: var(--accent); color: var(--surface); }
.upload-footnote { font-size: 11px; color: var(--muted); margin: 14px 0 0; }
.magnet-content.busy { min-height: 260px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 13px; padding: 28px; }
.quantum-loader { width: 36px; height: 36px; border: 3px solid var(--line); border-top-color: var(--accent); border-radius: 50%; animation: spin .9s linear infinite; }
.quantum-loader.small { width: 30px; height: 30px; }
.busy-text { font-weight: 600; }
.busy-file, .busy-stat { color: var(--muted); font-size: 12px; max-width: 100%; overflow-wrap: anywhere; }
.busy-warning { color: var(--danger); font-size: 12px; line-height: 1.6; }
.upload-progress { width: min(100%, 320px); height: 6px; background: var(--surface-soft); border-radius: 99px; overflow: hidden; }
.upload-progress span { display: block; height: 100%; background: var(--accent); transition: width .2s; }
.upload-resume { margin-top: 15px; padding: 12px; display: flex; align-items: center; gap: 10px; border: 1px solid var(--line); border-radius: 10px; font-size: 12px; }
.upload-resume span { flex: 1; }
.upload-resume button, .busy-actions button { border: 1px solid var(--line); border-radius: 7px; background: var(--surface); color: var(--accent); padding: 7px 10px; }
.reuse-notice { padding: 12px 14px; margin-bottom: 16px; background: var(--teal-tint); color: var(--teal); border-radius: 9px; font-size: 12px; line-height: 1.8; }
.reuse-event { margin-top: 6px; font-size: 11px; }
.notification-bar { max-width: 820px; margin: 16px auto 0; background: var(--teal-tint); border-radius: 10px; padding: 13px 18px; color: var(--teal); font-size: 13px; line-height: 1.7; }
.notification-bar.error { color: var(--danger); background: var(--danger-tint); cursor: pointer; }
.learning-steps { display: grid; grid-template-columns: repeat(3, 1fr); max-width: 820px; margin: 32px auto 54px; gap: 22px; }
.learning-steps > div { display: flex; gap: 12px; align-items: center; }
.step-number { color: var(--accent); font-size: 12px; background: var(--tint); border-radius: 10px; width: 34px; height: 34px; display: grid; place-items: center; flex-shrink: 0; }
.learning-steps strong { font-size: 12px; font-weight: 600; }
.learning-steps p { font-size: 11px; color: var(--muted); margin-top: 4px; }
.section-header { display: flex; align-items: center; justify-content: space-between; gap: 18px; margin-bottom: 20px; }
.library-title { display: flex; align-items: center; gap: 12px; }
.library-title h3 { font-size: 19px; font-weight: 700; }
.count-chip { color: var(--muted); background: var(--surface-soft); border: 1px solid var(--line); border-radius: 99px; font-size: 11px; padding: 4px 9px; }
.library-search { display: flex; align-items: center; gap: 9px; width: 260px; color: var(--muted); background: var(--surface); border: 1px solid var(--line); border-radius: 10px; padding: 10px 12px; }
.library-search input { border: 0; background: transparent; width: 100%; min-width: 0; font-size: 12px; color: var(--ink); outline: none; }
.library-search:focus-within { border-color: var(--accent); }
.card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 18px; }
.project-card { border: 1px solid var(--line); background: var(--surface); border-radius: 16px; overflow: hidden; position: relative; box-shadow: 0 4px 16px rgba(38, 47, 83, .025); transition: border-color .2s, transform .2s; }
.project-card:hover { border-color: var(--accent); transform: translateY(-2px); }
.card-meta { display: flex; gap: 13px; padding: 23px 32px 21px 20px; align-items: center; }
.meta-icon { width: 44px; height: 48px; border-radius: 12px; background: var(--tint); color: var(--accent); display: grid; place-items: center; flex-shrink: 0; }
.meta-info { min-width: 0; }
.filename-mask { font-size: 14px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.meta-tags { display: flex; align-items: center; gap: 9px; margin-top: 9px; font-size: 10px; flex-wrap: wrap; }
.time-tag { color: var(--muted); }
.status-indicator { padding: 3px 7px; border-radius: 6px; }
.status-indicator.completed { color: var(--teal); background: var(--teal-tint); }
.status-indicator.processing { color: var(--accent); background: var(--tint); }
.status-indicator.failed { color: var(--danger); background: var(--danger-tint); }
.status-indicator.unknown { color: var(--muted); background: var(--surface-soft); }
.action-dock { display: grid; grid-template-columns: 1fr 1fr 1.3fr; gap: 6px; padding: 12px; border-top: 1px solid var(--line); }
.dock-item { min-width: 0; display: flex; justify-content: center; align-items: center; gap: 5px; padding: 10px 4px; border: 0; background: transparent; color: var(--muted); border-radius: 8px; font-size: 11px; }
.dock-item .item-icon svg { width: 15px; height: 15px; }
.dock-item:hover:not(:disabled) { color: var(--accent); background: var(--tint); }
.dock-item.ai-core { color: var(--accent); background: var(--tint); font-weight: 600; }
.delete-btn { position: absolute; top: 8px; right: 7px; width: 30px; height: 30px; padding: 0; border: 0; border-radius: 7px; background: transparent; color: var(--muted); opacity: .65; display: grid; place-items: center; }
.delete-btn svg { width: 15px; }
.delete-btn:hover:not(:disabled) { color: var(--danger); background: var(--danger-tint); opacity: 1; }
.library-empty { text-align: center; padding: 34px 20px; border: 1px dashed var(--line); border-radius: 16px; color: var(--muted); font-size: 13px; line-height: 1.8; background: var(--surface); }
.library-empty button { border: 0; background: var(--tint); color: var(--accent); padding: 8px 15px; border-radius: 8px; margin-top: 14px; }
.sidebar-backdrop { position: fixed; inset: 0; background: var(--overlay); backdrop-filter: blur(3px); }
.sidebar-backdrop { z-index: 998; }
.sidebar-panel { position: fixed; inset: 0 0 0 auto; transform: translateX(100%); width: 880px; max-width: calc(100vw - 36px); height: 100dvh; background: var(--surface); border-left: 1px solid var(--line); box-shadow: -16px 0 60px rgba(0, 0, 0, .12); z-index: 999; display: flex; flex-direction: column; transition: transform .25s; }
.sidebar-panel.is-open { transform: translateX(0); }
.sidebar-header { display: flex; justify-content: space-between; align-items: center; gap: 12px; border-bottom: 1px solid var(--line); padding: 19px 26px; }
.sidebar-title { display: flex; align-items: center; gap: 10px; font-size: 16px; font-weight: 700; min-width: 0; overflow-wrap: anywhere; }
.sidebar-title .icon { color: var(--accent); display: flex; flex-shrink: 0; }
.close-btn { width: 34px; height: 34px; border: 0; background: var(--surface-soft); color: var(--muted); font-size: 24px; border-radius: 10px; flex-shrink: 0; }
.close-btn:hover { color: var(--ink); background: var(--tint); }
.sidebar-body { padding: 26px; flex: 1; min-height: 0; overflow-y: auto; overscroll-behavior: contain; }
.video-evidence { background: var(--surface-soft); border: 1px solid var(--line); border-radius: 13px; margin-bottom: 24px; overflow: hidden; }
.video-evidence video { display: block; width: 100%; max-height: 420px; aspect-ratio: 16 / 9; object-fit: contain; background: #141821; }
.video-evidence > p { padding: 11px 16px; font-size: 11px; color: var(--muted); line-height: 1.7; }
.video-evidence-loading, .video-evidence-error { padding: 26px 16px; font-size: 12px; color: var(--muted); display: flex; align-items: center; justify-content: center; gap: 12px; }
.video-evidence-error { color: var(--danger); }
.video-evidence-error button { background: var(--surface); border: 1px solid var(--line); border-radius: 8px; color: var(--danger); padding: 8px 10px; }
.agent-composer { display: flex; flex-direction: column; gap: 15px; }
.agent-caption { font-size: 13px; color: var(--muted); line-height: 1.7; }
.inline-error { background: var(--danger-tint); color: var(--danger); padding: 12px 14px; border-radius: 9px; font-size: 13px; line-height: 1.7; margin-bottom: 8px; }
.agent-composer textarea, .follow-up-box textarea, .plan-editor input, .evidence-search-form input { width: 100%; min-width: 0; border: 1px solid var(--line); background: var(--surface-soft); color: var(--ink); border-radius: 10px; padding: 13px; line-height: 1.7; font-size: 13px; }
.agent-composer textarea { min-height: 125px; resize: vertical; }
.field-counter { color: var(--muted); font-size: 11px; text-align: right; }
.goal-presets { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 9px; }
.goal-presets button { text-align: left; border: 1px solid var(--line); background: var(--surface); color: var(--muted); border-radius: 11px; padding: 13px; min-height: 88px; }
.goal-presets strong { display: block; font-size: 13px; color: var(--ink); margin-bottom: 6px; }
.goal-presets span { display: block; font-size: 11px; line-height: 1.7; }
.goal-presets button:hover, .goal-presets button.active { border-color: var(--accent); background: var(--tint); }
.goal-presets button.active strong { color: var(--accent); }
.agent-run-btn { padding: 13px 18px; background: var(--accent); color: var(--surface); border: 0; border-radius: 10px; font-weight: 600; font-size: 13px; }
.agent-run-btn:hover:not(:disabled) { background: var(--accent-hover); }
.agent-running { display: flex; flex-direction: column; gap: 20px; }
.video-evidence-compact video { max-height: 180px; object-fit: contain; }
.process-choice { display: flex; align-items: center; gap: 9px; color: var(--ink); font-size: 13px; margin-top: 6px; cursor: pointer; }
.process-choice input { accent-color: var(--accent); width: 16px; height: 16px; }
.process-choice-note { color: var(--muted); font-size: 12px; line-height: 1.8; }
.loading-state { min-height: 200px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 18px; font-size: 13px; line-height: 1.7; color: var(--muted); }
.loading-state p { text-align: center; max-width: 34rem; }
.loading-hint { font-size: 11px; }
.stream-offline { color: var(--danger); font-size: 12px; }
.result-actions { display: flex; gap: 8px; justify-content: flex-end; margin-bottom: 20px; flex-wrap: wrap; }
.result-actions button, .evidence-search-form button, .evidence-search-results button, .plan-editor button, .plan-edit-trigger, .feedback-row button { border: 1px solid var(--line); background: var(--surface); color: var(--muted); border-radius: 8px; padding: 8px 12px; font-size: 12px; }
.result-actions button:hover:not(:disabled), .evidence-search-form button:hover:not(:disabled), .evidence-search-results button:hover, .plan-editor button:hover, .plan-edit-trigger:hover, .feedback-row button:hover, .feedback-row button.active { color: var(--accent); border-color: var(--accent); background: var(--tint); }
.evidence-search { margin-bottom: 24px; }
.evidence-search-form { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; }
.evidence-search-form input { padding: 10px 12px; }
.evidence-search-error { margin-top: 8px; font-size: 12px; color: var(--danger); }
.evidence-search-results { display: grid; gap: 6px; margin-top: 10px; }
.evidence-search-results button { display: grid; grid-template-columns: 52px 70px minmax(0, 1fr); gap: 10px; text-align: left; align-items: center; }
.evidence-search-results strong { color: var(--accent); }
.evidence-search-results small { color: var(--teal); }
.evidence-search-results span, .evidence-search-results small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.markdown-content, .text-content { color: var(--ink); font-size: 14px; line-height: 1.95; overflow-wrap: anywhere; }
.markdown-content h1, .markdown-content h2, .markdown-content h3 { color: var(--ink); line-height: 1.5; margin: 1.5em 0 .65em; }
.markdown-content h1 { font-size: 25px; padding-bottom: 13px; border-bottom: 1px solid var(--line); }
.markdown-content h2 { font-size: 20px; }
.markdown-content h3 { font-size: 16px; }
.markdown-content p, .markdown-content ul, .markdown-content ol { margin-bottom: 1em; }
.markdown-content ul, .markdown-content ol { padding-left: 24px; }
.markdown-content li { margin: 6px 0; }
.markdown-content a { color: var(--accent); }
.markdown-content a[href^="#video-t="] { display: inline-block; background: var(--tint); padding: 0 7px; border-radius: 6px; text-decoration: none; font-size: 12px; font-variant-numeric: tabular-nums; }
.markdown-content blockquote { margin: 15px 0; border-left: 3px solid var(--accent); padding: 12px 18px; background: var(--surface-soft); border-radius: 0 9px 9px 0; }
.markdown-content code { background: var(--surface-soft); padding: 2px 5px; border-radius: 4px; font-size: .9em; }
.markdown-content pre, .text-content pre { overflow-x: auto; padding: 16px; border: 1px solid var(--line); border-radius: 10px; background: var(--surface-soft); font-size: 12px; }
.text-content pre { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.9; }
.markdown-content pre code { padding: 0; }
.markdown-content table { display: block; overflow-x: auto; border-collapse: collapse; max-width: 100%; margin-bottom: 20px; font-size: 12px; }
.markdown-content th, .markdown-content td { border: 1px solid var(--line); padding: 8px 12px; }
.markdown-content th { background: var(--surface-soft); }
.text-meta { color: var(--muted); font-size: 12px; margin-bottom: 12px; }
.agent-inspector { margin-top: 28px; padding-top: 12px; border-top: 1px solid var(--line); }
.agent-inspector summary { cursor: pointer; font-size: 12px; color: var(--muted); padding: 9px 0; }
.agent-inspector-content { padding-top: 13px; }
.agent-meta-block { padding: 16px; border: 1px solid var(--line); border-radius: 11px; background: var(--surface-soft); margin-bottom: 14px; }
.meta-label { color: var(--accent); font-size: 12px; font-weight: 600; display: block; margin-bottom: 10px; }
.agent-meta-block ol { color: var(--muted); font-size: 12px; line-height: 1.8; padding-left: 20px; margin: 0; }
.agent-meta-block li { margin: 6px 0; }
.plan-editor { display: grid; gap: 9px; }
.plan-editor-row { display: grid; grid-template-columns: minmax(0, 1fr) 34px; gap: 8px; }
.plan-editor input { background: var(--surface); padding: 9px; }
.plan-editor-actions { display: flex; justify-content: flex-end; gap: 8px; }
.plan-edit-trigger { margin-top: 9px; }
.stage-list, .quality-row { display: flex; flex-wrap: wrap; gap: 8px; }
.stage-list span, .quality-row span { border: 1px solid var(--line); border-radius: 7px; padding: 6px 9px; font-size: 11px; color: var(--muted); background: var(--surface); }
.process-record-note { color: var(--text-sub, var(--muted)); font-size: 11px; line-height: 1.8; }
.process-record-list { margin-top: 12px !important; max-height: 360px; overflow-y: auto; }
.process-record-list li { padding-bottom: 12px; }
.process-record-list li > div { display: flex; gap: 10px; flex-wrap: wrap; font-size: 10px; }
.process-record-list li time { font-variant-numeric: tabular-nums; }
.process-record-list li p { margin-top: 5px; color: var(--ink); }
.follow-up-suggestions { margin-top: 24px; }
.follow-up-suggestions button { background: var(--tint); color: var(--accent); border: 1px solid var(--line); border-radius: 8px; padding: 8px 11px; font-size: 11px; text-align: left; }
.follow-up-suggestions p { color: var(--muted); font-size: 11px; line-height: 1.8; margin-top: 8px; }
.follow-up-box { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 10px; margin-top: 26px; }
.follow-up-box textarea { resize: vertical; min-height: 80px; }
.follow-up-box button { border: 1px solid var(--accent); background: var(--tint); color: var(--accent); border-radius: 10px; padding: 12px 22px; font-size: 13px; }
.feedback-row { display: flex; align-items: center; gap: 8px; font-size: 12px; color: var(--muted); margin-top: 16px; }
.toast-pop-enter-active, .toast-pop-leave-active { transition: opacity .15s, transform .15s; }
.toast-pop-enter-from, .toast-pop-leave-to { opacity: 0; transform: translateY(-5px); }
@media (max-width: 800px) {
  .nav-content { padding: 15px 20px; }
  .brand-caption, .status-pill { display: none; }
  .main-container { padding: 42px 20px 48px; }
  .learning-steps { gap: 12px; }
  .learning-steps > div { gap: 8px; }
  .sidebar-panel { max-width: calc(100vw - 20px); }
}
@media (max-width: 560px) {
  .nav-content { padding: 13px 16px; gap: 10px; }
  .brand-mark { width: 30px; height: 30px; font-size: 16px; border-radius: 9px; margin-right: 2px; }
  .brand-name { font-size: 19px; }
  .brand-agent { font-size: 15px; }
  .brand { gap: 4px; }
  .nav-controls { gap: 6px; }
  .theme-toggle { width: 36px; padding: 8px; }
  .theme-toggle span { display: none; }
  .main-container { padding: 35px 16px 40px; }
  .hero-eyebrow { font-size: 11px; margin-bottom: 19px; }
  .slogan-main { font-size: 36px; letter-spacing: -1px; }
  .slogan-sub { font-size: 12px; max-width: 290px; margin-bottom: 26px; }
  .split-container { grid-template-columns: 1fr; }
  .upload-pane { min-height: 180px; padding: 22px; }
  .pane-local { border-right: 0; border-bottom: 1px solid var(--line); }
  .pane-url { min-height: 220px; }
  .learning-steps { gap: 15px; margin: 25px 0 35px; grid-template-columns: 1fr; padding: 0 8px; }
  .learning-steps > div { gap: 13px; }
  .learning-steps > div > div { display: flex; align-items: center; gap: 14px; }
  .learning-steps p { margin-top: 0; }
  .section-header { align-items: stretch; flex-direction: column; gap: 14px; }
  .library-search { width: 100%; }
  .card-grid { grid-template-columns: minmax(0, 1fr); }
  .sidebar-panel { width: 100%; max-width: 100%; }
  .sidebar-header { padding: 15px 16px; }
  .sidebar-title { font-size: 14px; }
  .sidebar-body { padding: 18px 16px; }
  .goal-presets { grid-template-columns: 1fr; }
  .goal-presets button { min-height: 70px; }
  .result-actions { justify-content: stretch; gap: 5px; }
  .result-actions button { flex: 1; padding: 9px 5px; font-size: 11px; }
  .evidence-search-form { grid-template-columns: minmax(0, 1fr) auto; }
  .evidence-search-form button { padding: 8px; }
  .evidence-search-results button { grid-template-columns: 50px minmax(0, 1fr); }
  .evidence-search-results small { display: none; }
  .follow-up-box { grid-template-columns: 1fr; }
  .follow-up-box button { min-height: 42px; }
  .upload-resume { flex-direction: column; align-items: stretch; }
  .feedback-row { flex-wrap: wrap; }
  body.overlay-open { overflow: hidden; }
}
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { animation-duration: .01ms !important; animation-iteration-count: 1 !important; transition-duration: .01ms !important; scroll-behavior: auto !important; }
}
@keyframes spin { to { transform: rotate(360deg); } }
@keyframes pulse { 50% { opacity: .35; } }
</style>
