/**
 * Admin Console API 客户端（#16/#17/#18）。
 *
 * - 会话令牌只保存在 sessionStorage（#14 实现决策）：关闭浏览器后需要重新登录；
 * - 所有管理请求携带 `Authorization: Bearer <token>`，令牌不进入 URL；
 * - 401 表示令牌缺失/过期/已撤销，调用方应清理本地令牌并回到登录页。
 */
const API_BASE = (process.env.VUE_APP_API_BASE_URL || '').replace(/\/+$/, '')

const TOKEN_KEY = 'adminSessionToken'

export function getAdminToken() {
  return window.sessionStorage.getItem(TOKEN_KEY)
}

export function setAdminToken(token) {
  if (token) {
    window.sessionStorage.setItem(TOKEN_KEY, token)
  } else {
    window.sessionStorage.removeItem(TOKEN_KEY)
  }
}

export function clearAdminToken() {
  window.sessionStorage.removeItem(TOKEN_KEY)
}

/** GitHub OAuth 授权入口（后端处理跳转 GitHub）。 */
export function oauthAuthorizationUrl() {
  return `${API_BASE}/oauth2/authorization/github`
}

async function adminFetch(path, { method = 'GET', body } = {}) {
  const token = getAdminToken()
  const headers = { Accept: 'application/json' }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  const response = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  let payload = null
  try {
    payload = await response.json()
  } catch (e) {
    // 非 JSON 响应（如网关错误页）视为不可解析，保留 null
  }
  return { status: response.status, payload }
}

/** 用一次性交换码换取会话令牌；失败时返回 null。 */
export async function exchangeCode(code) {
  const { status, payload } = await adminFetch('/api/admin/auth/exchange', {
    method: 'POST',
    body: { code },
  })
  if (status !== 200 || !payload || !payload.token) {
    return null
  }
  return { token: payload.token, login: payload.login, expiresIn: payload.expiresIn }
}

/** 当前会话身份；401 时返回 null。 */
export async function fetchMe() {
  const { status, payload } = await adminFetch('/api/admin/me')
  if (status !== 200 || !payload) {
    return null
  }
  return payload
}

/** 登出（撤销令牌）；调用方无论结果都应清理本地令牌。 */
export async function requestLogout() {
  await adminFetch('/api/admin/auth/logout', { method: 'POST' })
}

/** 当前站点设置（编辑表单初始值）；401 时返回 null。 */
export async function fetchSiteSettings() {
  const { status, payload } = await adminFetch('/api/admin/site-settings')
  if (status !== 200 || !payload) {
    return null
  }
  return payload
}

/**
 * 保存并发布整组站点设置（#17）；成功返回 { ok: true, payload }，
 * 校验失败返回 { ok: false, message }。
 */
export async function publishSiteSettings(settings) {
  const { status, payload } = await adminFetch('/api/admin/site-settings', {
    method: 'PUT',
    body: settings,
  })
  if (status === 200) {
    return { ok: true, payload }
  }
  return { ok: false, message: (payload && payload.message) || `保存失败（HTTP ${status}）` }
}

/** 全部 Project（#18 管理端）；401 时返回 null。 */
export async function fetchProjects() {
  const { status, payload } = await adminFetch('/api/admin/projects')
  if (status !== 200 || !payload) {
    return null
  }
  return payload
}

/** 新增 Project（#18）。 */
export async function createProject(project) {
  const { status, payload } = await adminFetch('/api/admin/projects', {
    method: 'POST',
    body: project,
  })
  if (status === 201) {
    return { ok: true, payload }
  }
  return { ok: false, message: (payload && payload.message) || `创建失败（HTTP ${status}）` }
}

/** 保存并发布单个 Project（#18，无 Draft/修订历史）。 */
export async function updateProject(id, project) {
  const { status, payload } = await adminFetch(`/api/admin/projects/${id}`, {
    method: 'PUT',
    body: project,
  })
  if (status === 200) {
    return { ok: true, payload }
  }
  return { ok: false, message: (payload && payload.message) || `保存失败（HTTP ${status}）` }
}

/** 删除 Project（#18）。 */
export async function deleteProject(id) {
  const { status, payload } = await adminFetch(`/api/admin/projects/${id}`, {
    method: 'DELETE',
  })
  if (status === 204) {
    return { ok: true, payload: null }
  }
  return { ok: false, message: (payload && payload.message) || `删除失败（HTTP ${status}）` }
}
