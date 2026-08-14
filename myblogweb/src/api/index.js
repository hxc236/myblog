/**
 * 统一公开内容 API 客户端（#5「前端数据与状态」）。
 *
 * - 来源只读 VUE_APP_API_BASE_URL，不写死 localhost；
 * - 匿名、只读，不依赖 Vuex 登录状态，不携带认证凭据；
 * - 幂等 GET，冷启动时在不超过 75 秒的窗口内有限重试；
 * - 通过 onPhase 回调通知加载阶段：loading → starting → retrying → ready / error。
 */
const API_BASE = (process.env.VUE_APP_API_BASE_URL || '').replace(/\/+$/, '')

const STARTING_NOTICE_MS = 3000 // 三秒未取得内容时提示服务正在启动
const RETRY_AFTER_MS = 5000 // 重试间隔
const WINDOW_MS = 75000 // 总重试窗口，不超过 75 秒
const ATTEMPT_TIMEOUT_MS = 35000 // 单次请求上限（Render 冷启动可能较久）

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function fetchWithTimeout(path, timeoutMs) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  return fetch(`${API_BASE}${path}`, {
    headers: { Accept: 'application/json' },
    signal: controller.signal,
  }).finally(() => clearTimeout(timer))
}

/**
 * 加载一个 JSON 资源；失败时在 75 秒窗口内重试，最终失败抛出异常。
 * @param {string} path 例如 /api/v1/introduction
 * @param {{ onPhase?: (phase: string) => void }} [options]
 * @returns {Promise<any>}
 */
export async function loadJson(path, { onPhase } = {}) {
  const startedAt = Date.now()
  let noticeShown = false

  for (;;) {
    const elapsed = Date.now() - startedAt
    if (!noticeShown && elapsed >= STARTING_NOTICE_MS) {
      noticeShown = true
      onPhase && onPhase('starting')
    }
    if (elapsed >= WINDOW_MS) {
      onPhase && onPhase('error')
      throw new Error(`加载 ${path} 失败：重试窗口已用尽`)
    }
    try {
      const response = await fetchWithTimeout(path, Math.min(ATTEMPT_TIMEOUT_MS, WINDOW_MS - elapsed))
      if (!response.ok) {
        const err = new Error(`HTTP ${response.status}`)
        err.status = response.status
        throw err
      }
      onPhase && onPhase('ready')
      return await response.json()
    } catch (e) {
      // 4xx 是确定性结果（如博客 404）：不重试，立即交给调用方处理
      if (e && e.status >= 400 && e.status < 500) {
        throw e
      }
      // 5xx / 网络 / 超时：在窗口内继续重试
      onPhase && onPhase('retrying')
      await sleep(Math.min(RETRY_AFTER_MS, WINDOW_MS - (Date.now() - startedAt)))
    }
  }
}

/** 模块级共享缓存：多个组件请求同一资源时只发一次请求。 */
const shared = new Map()

export function sharedLoad(path) {
  if (!shared.has(path)) {
    shared.set(path, loadJson(path))
  }
  return shared.get(path)
}
