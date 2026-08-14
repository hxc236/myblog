/**
 * 设备本地 Reading History（#23 / #14 用户故事 17-19）。
 *
 * - 只保存在当前浏览器 localStorage，不上传服务器、不跨设备同步；
 * - 最多十篇，最近阅读在前，重复阅读按 slug 去重并提前；
 * - 一键清除；渲染前会清理已归档或不存在的引用（slug 无法解析时移除）。
 */
const HISTORY_KEY = 'myblog:reading-history'
const MAX_ENTRIES = 10

export function loadReadingHistory() {
  try {
    const raw = window.localStorage.getItem(HISTORY_KEY)
    const list = raw ? JSON.parse(raw) : []
    return Array.isArray(list) ? list.filter((e) => e && e.slug) : []
  } catch (e) {
    return []
  }
}

function saveReadingHistory(list) {
  try {
    window.localStorage.setItem(HISTORY_KEY, JSON.stringify(list.slice(0, MAX_ENTRIES)))
  } catch (e) {
    // 存储不可用时静默降级
  }
}

/** 记录一次阅读：去重、置顶、最多十篇。 */
export function recordReading(slug, title) {
  const list = loadReadingHistory().filter((e) => e.slug !== slug)
  list.unshift({ slug, title: title || slug, readAt: new Date().toISOString() })
  saveReadingHistory(list)
}

/** 一键清除。 */
export function clearReadingHistory() {
  saveReadingHistory([])
}

/**
 * 清理失效引用：并发验证每个 slug 仍可读取（404/归档即移除）。
 * @param {(slug: string) => Promise<unknown>} fetchPost 读取单篇文章的加载器
 */
export async function pruneReadingHistory(fetchPost) {
  const list = loadReadingHistory()
  const results = await Promise.allSettled(list.map((e) => fetchPost(e.slug)))
  const alive = list.filter((_, i) => results[i].status === 'fulfilled')
  if (alive.length !== list.length) {
    saveReadingHistory(alive)
  }
  return alive
}
