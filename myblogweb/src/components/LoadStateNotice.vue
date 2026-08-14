<template>
  <div v-if="phase === 'starting'" class="notice" role="status">
    <span class="notice-dot" aria-hidden="true"></span>
    免费内容服务正在启动，通常可能需要约一分钟
  </div>
  <div v-else-if="phase === 'retrying'" class="notice" role="status">
    <span class="notice-dot" aria-hidden="true"></span>
    内容服务仍在启动，正在自动重试…
  </div>
  <div v-else-if="phase === 'error'" class="notice notice-error" role="alert">
    <p class="error-text">内容服务暂时不可用，请稍后重试。</p>
    <button type="button" class="btn btn-primary retry-btn" @click="$emit('retry')">重新加载</button>
  </div>
</template>

<script>
export default {
  name: 'LoadStateNotice',
  props: {
    phase: { type: String, default: 'loading' }, // loading | starting | retrying | error | ready
  },
  emits: ['retry'],
}
</script>

<style scoped>
.notice {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  margin: 18px 0;
  border-radius: var(--radius-small);
  background: var(--surface);
  border: 1px solid var(--rule);
  color: var(--body-muted);
  font-size: 15px;
}

.notice-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--forest);
  animation: blink 1.2s ease-in-out infinite;
}

@keyframes blink {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 1; }
}

.notice-error {
  border-color: rgba(50, 106, 74, 0.4);
}

.error-text {
  margin: 0;
}

.retry-btn {
  padding: 6px 22px;
  font-size: 15px;
}
</style>
