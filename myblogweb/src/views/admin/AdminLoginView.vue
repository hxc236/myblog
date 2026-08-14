<template>
  <div class="page admin-login">
    <div class="admin-card">
      <p class="admin-eyebrow" aria-hidden="true">Admin Console</p>
      <h1 class="admin-title">站点管理登录</h1>

      <p v-if="notice" class="admin-notice" role="status">{{ notice }}</p>
      <p v-else class="admin-hint">
        仅允许名单中的唯一 GitHub 身份进入；登录后会话仅保存在当前浏览器标签页。
      </p>

      <a
        class="btn btn-primary github-login"
        :href="oauthUrl"
        :aria-disabled="busy"
        @click="busy = true"
      >
        {{ busy ? '正在跳转 GitHub…' : '使用 GitHub 登录' }}
      </a>

      <p class="admin-error" v-if="error" role="alert">{{ error }}</p>

      <router-link class="admin-back" to="/">返回首页</router-link>
    </div>
  </div>
</template>

<script>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { exchangeCode, oauthAuthorizationUrl, setAdminToken } from '@/api/admin'

/**
 * Admin Console 登录页（#16）。
 *
 * 流程：点击按钮 → 后端 GitHub OAuth → 回调带一次性交换码回到本页 →
 * 立即把交换码换成会话令牌（仅存 sessionStorage）→ 进入 /admin。
 * 错误（allowlist 拒绝、OAuth 失败、交换码失效）都在本页给出反馈。
 */
export default {
  name: 'AdminLoginView',
  setup() {
    const route = useRoute()
    const router = useRouter()
    const oauthUrl = oauthAuthorizationUrl()
    const busy = ref(false)
    const error = ref('')
    const notice = ref('')

    onMounted(async () => {
      const query = route.query
      if (query.error === 'forbidden') {
        error.value = '该 GitHub 账号不在允许名单中，无法进入管理后台。'
        return
      }
      if (query.error === 'oauth_failed') {
        error.value = 'GitHub 登录失败，请重试。'
        return
      }
      if (query.error === 'expired') {
        error.value = '会话已过期或已被撤销，请重新登录。'
        return
      }
      if (query.error === 'session_required') {
        error.value = '请先登录后再进入管理后台。'
        return
      }
      if (query.logged_out) {
        notice.value = '已退出登录。'
      }
      const code = typeof query.code === 'string' ? query.code : ''
      if (!code) {
        return
      }
      notice.value = '正在完成登录…'
      const session = await exchangeCode(code)
      if (!session) {
        error.value = '登录交换码已失效，请重新使用 GitHub 登录。'
        notice.value = ''
        return
      }
      setAdminToken(session.token)
      router.replace('/admin')
    })

    return { oauthUrl, busy, error, notice }
  },
}
</script>

<style scoped>
.admin-login {
  padding-top: 150px;
  padding-bottom: 80px;
  display: flex;
  justify-content: center;
}

.admin-card {
  width: 100%;
  max-width: 420px;
  padding: 36px 32px;
  background: var(--surface, #fffdf8);
  border: 1px solid var(--line, #e6e0d4);
  border-radius: 12px;
  text-align: center;
}

.admin-eyebrow {
  margin: 0 0 6px;
  font-family: var(--font-serif);
  color: var(--meta-quiet, #8b8577);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  font-size: 13px;
}

.admin-title {
  font-size: 26px;
  margin: 0 0 14px;
}

.admin-hint,
.admin-notice {
  color: var(--body-muted, #5d584c);
  font-size: 14px;
  line-height: 1.7;
  margin: 0 0 22px;
}

.admin-notice {
  color: var(--accent, #2f6b4f);
}

.admin-error {
  color: #a63d2f;
  font-size: 14px;
  margin: 16px 0 0;
}

.github-login {
  display: inline-block;
  width: 100%;
}

.admin-back {
  display: inline-block;
  margin-top: 18px;
  color: var(--meta-quiet, #8b8577);
  font-size: 14px;
}
</style>
