import { onBeforeUnmount, ref } from 'vue'

/**
 * 冷启动加载阶段的状态机（#5「前端数据与状态」）。
 *
 * 阶段：loading（无提示）→ starting（3 秒后）→ retrying（自动重试）→
 * 由调用方在请求结束后设置最终状态 ready / error / 自定义值。
 */
export function usePhaseNotice() {
  const phase = ref('loading')
  let cancelled = false

  const onPhase = (p) => {
    if (!cancelled && (p === 'starting' || p === 'retrying')) {
      phase.value = p
    }
  }

  const stop = () => {
    cancelled = true
  }

  onBeforeUnmount(stop)

  return { phase, onPhase }
}
