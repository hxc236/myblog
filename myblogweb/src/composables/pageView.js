/**
 * 匿名 Page View 上报（#25）：同一浏览器对同一文章每天最多上报一次，
 * 去重标记只保存在浏览器本地（#14 实现决策），不上传身份信息。
 */
const VIEW_KEY = 'myblog:pageview-reported'

function todayKey() {
  const now = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

function readMarkers() {
  try {
    const raw = window.localStorage.getItem(VIEW_KEY)
    const map = raw ? JSON.parse(raw) : {}
    return map && typeof map === 'object' ? map : {}
  } catch (e) {
    return {}
  }
}

function writeMarkers(map) {
  try {
    window.localStorage.setItem(VIEW_KEY, JSON.stringify(map))
  } catch (e) {
    // 存储不可用时静默降级（至多重复上报一次，无身份信息）
  }
}

/** 当天是否已上报过该文章。 */
export function isReportedToday(slug) {
  const markers = readMarkers()
  return markers[slug] === todayKey()
}

/** 上报一次 Page View（若当天尚未上报）。 */
export async function reportPageView(slug) {
  if (isReportedToday(slug)) {
    return
  }
  const API_BASE = (process.env.VUE_APP_API_BASE_URL || '').replace(/\/+$/, '')
  let ok = false
  try {
    const response = await fetch(`${API_BASE}/api/posts/${encodeURIComponent(slug)}/view`, {
      method: 'POST',
      headers: { Accept: 'application/json' },
    })
    ok = response.status === 200
  } catch (e) {
    ok = false
  }
  if (ok) {
    const markers = readMarkers()
    markers[slug] = todayKey()
    writeMarkers(markers)
  }
}
