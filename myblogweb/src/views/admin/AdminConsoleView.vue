<template>
  <div class="page admin-console">
    <div class="admin-card">
      <p class="admin-eyebrow" aria-hidden="true">Admin Console</p>
      <h1 class="admin-title">站点管理</h1>

      <p v-if="error" class="admin-error" role="alert">{{ error }}</p>

      <template v-else-if="me">
        <p class="admin-identity">已登录：<strong>{{ me.login }}</strong></p>
        <p class="admin-hint">内容管理功能将随后续卡片逐步开放。</p>
        <button class="btn btn-secondary admin-logout" :disabled="loggingOut" @click="doLogout">
          {{ loggingOut ? '正在退出…' : '退出登录' }}
        </button>
      </template>

      <p v-else class="admin-hint">正在校验会话…</p>

      <router-link class="admin-back" to="/">返回公开首页</router-link>
    </div>
  </div>
</template>

<script>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { clearAdminToken, fetchMe, requestLogout } from '@/api/admin'

/**
 * Admin Console 外壳（#16）：校验会话、显示身份、登出撤销。
 *
 * 会话过期或已被撤销时 /api/admin/me 返回 401 → 清理本地令牌并回到登录页，
 * 给出“会话已过期”反馈。
 */
export default {
  name: 'AdminConsoleView',
  setup() {
    const router = useRouter()
    const me = ref(null)
    const error = ref('')
    const loggingOut = ref(false)

    onMounted(async () => {
      try {
        const identity = await fetchMe()
        if (!identity) {
          clearAdminToken()
          router.replace({ path: '/admin/login', query: { error: 'expired' } })
          return
        }
        me.value = identity
      } catch (e) {
        error.value = '无法连接管理服务，请稍后重试。'
      }
    })

    async function doLogout() {
      loggingOut.value = true
      try {
        await requestLogout()
      } catch (e) {
        // 令牌已失效时后端可能直接拒绝：无论结果都清理本地会话
      } finally {
        clearAdminToken()
        router.replace({ path: '/admin/login', query: { logged_out: 1 } })
      }
    }

    return { me, error, loggingOut, doLogout }
  },
}
</script>

<style scoped>
.admin-console {
  padding-top: 150px;
  padding-bottom: 80px;
  display: flex;
  justify-content: center;
}

.admin-card {
  width: 100%;
  max-width: 460px;
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
  margin: 0 0 16px;
}

.admin-identity {
  font-size: 15px;
  margin: 0 0 8px;
}

.admin-hint {
  color: var(--body-muted, #5d584c);
  font-size: 14px;
  line-height: 1.7;
  margin: 0 0 22px;
}

.admin-error {
  color: #a63d2f;
  font-size: 14px;
}

.admin-logout {
  display: inline-block;
}

.admin-back {
  display: inline-block;
  margin-top: 18px;
  color: var(--meta-quiet, #8b8577);
  font-size: 14px;
}
</style>
