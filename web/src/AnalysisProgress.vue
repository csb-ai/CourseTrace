<template>
  <section class="analysis-presentation" aria-label="视频分析过程">
    <div class="presentation-heading">
      <span class="presentation-badge">分析处理流程</span>
      <span class="presentation-count">{{ index + 1 }} / {{ steps.length }}</span>
    </div>
    <p class="presentation-note">本次请求 {{ requestTime }} · 读取已保存的分析记录</p>
    <div class="presentation-bar" role="progressbar" aria-label="过程展示进度"
      :aria-valuenow="Math.round((index + 1) / steps.length * 100)" aria-valuemin="0" aria-valuemax="100">
      <span :style="{ width: `${(index + 1) / steps.length * 100}%` }"></span>
    </div>
    <ol class="presentation-stages">
      <li v-for="(step, position) in steps" :key="step.key"
        :class="{ active: position === index, done: position < index, warning: step.warning && position <= index }"
        :aria-current="position === index ? 'step' : undefined">
        <span class="presentation-dot">{{ position < index ? (step.warning ? '!' : '✓') : String(position + 1).padStart(2, '0') }}</span>
        <span>{{ step.title }}</span>
        <small>{{ position < index ? (step.warning ? '需核验' : '已展示') : position === index ? '展示中' : '待展示' }}</small>
      </li>
    </ol>
    <div class="presentation-current" aria-live="polite" aria-atomic="true">
      <h3>{{ steps[index]?.title }}</h3>
      <p>{{ steps[index]?.detail }}</p>
      <ul v-if="steps[index]?.tasks"><li v-for="task in steps[index].tasks" :key="task">{{ task }}</li></ul>
    </div>
    <button type="button" class="presentation-skip" @click="$emit('skip')">直接查看结果 →</button>
  </section>
</template>

<script setup>
import { computed } from 'vue'
const props = defineProps({ steps: { type: Array, required: true }, index: { type: Number, required: true }, requestedAt: String })
const requestTime = computed(() => props.requestedAt ? new Date(props.requestedAt).toLocaleString('zh-CN', { hour12: false }) : '—')
defineEmits(['skip'])
</script>

<style scoped>
.analysis-presentation { padding: 24px; border: 1px solid var(--line); background: var(--surface); border-radius: 20px; }
.presentation-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.presentation-badge { color: var(--accent); background: var(--tint); padding: 7px 12px; border-radius: 999px; font-size: 12px; font-weight: 600; }
.presentation-count { color: var(--muted); font-size: 12px; font-variant-numeric: tabular-nums; }
.presentation-note { margin: 12px 0 18px; color: var(--muted); font-size: 12px; line-height: 1.7; }
.presentation-bar { height: 5px; border-radius: 6px; background: var(--surface-soft); overflow: hidden; }
.presentation-bar span { display: block; height: 100%; background: var(--accent); border-radius: inherit; transition: width .5s ease; }
.presentation-stages { list-style: none; padding: 0; margin: 22px 0; display: grid; gap: 9px; }
.presentation-stages li { display: flex; align-items: center; gap: 12px; padding: 10px 12px; border-radius: 10px; color: var(--muted); font-size: 13px; transition: background .25s, color .25s; }
.presentation-stages li.active { background: var(--tint); color: var(--accent); font-weight: 600; }
.presentation-stages li.done { color: #17857b; }
.presentation-stages li.warning { color: #b27520; }
.presentation-dot { display: grid; place-items: center; flex: 0 0 28px; height: 28px; border-radius: 50%; background: var(--surface-soft); font-size: 11px; }
.active .presentation-dot { background: var(--accent); color: white; box-shadow: 0 0 0 4px var(--tint); }
.presentation-stages small { margin-left: auto; white-space: nowrap; font-size: 10px; font-weight: 400; }
.presentation-current { padding: 16px 18px; background: var(--surface-soft); border-radius: 12px; min-height: 76px; }
.presentation-current h3 { color: var(--ink); font-size: 15px; margin: 0 0 8px; }
.presentation-current p, .presentation-current li { font-size: 12px; line-height: 1.8; color: var(--muted); }
.presentation-current ul { padding-left: 18px; margin-top: 10px; }
.presentation-skip { display: block; margin: 18px 0 0 auto; border: 0; color: var(--accent); background: transparent; cursor: pointer; padding: 8px 0 0 12px; font-size: 12px; }
@media (max-width: 560px) { .analysis-presentation { padding: 16px; } .presentation-stages li { padding: 8px 4px; gap: 8px; } }
@media (prefers-reduced-motion: reduce) { .presentation-bar span, .presentation-stages li { transition: none; } }
</style>
