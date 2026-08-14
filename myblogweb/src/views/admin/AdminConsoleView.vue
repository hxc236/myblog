<template>
  <div class="page admin-console">
    <header class="admin-header">
      <div>
        <p class="admin-eyebrow" aria-hidden="true">Admin Console</p>
        <h1 class="admin-title">站点管理</h1>
      </div>
      <div class="admin-header-actions">
        <span v-if="me" class="admin-identity">已登录：{{ me.login }}</span>
        <button class="btn btn-secondary" :disabled="loggingOut" @click="doLogout">
          {{ loggingOut ? '正在退出…' : '退出登录' }}
        </button>
      </div>
    </header>

    <nav class="admin-tabs" aria-label="管理功能">
      <button
        class="admin-tab"
        :class="{ active: activeTab === 'settings' }"
        @click="activeTab = 'settings'"
      >
        站点设置
      </button>
      <button
        class="admin-tab"
        :class="{ active: activeTab === 'projects' }"
        @click="activeTab = 'projects'"
      >
        项目管理
      </button>
      <button
        class="admin-tab"
        :class="{ active: activeTab === 'taxonomy' }"
        @click="activeTab = 'taxonomy'"
      >
        分类与标签
      </button>
      <button
        class="admin-tab"
        :class="{ active: activeTab === 'posts' }"
        @click="activeTab = 'posts'"
      >
        写作
      </button>
    </nav>

    <template v-if="activeTab === 'settings'">
      <p v-if="loadError" class="admin-error" role="alert">{{ loadError }}</p>
      <p v-else-if="!settings" class="admin-hint">正在加载站点设置…</p>

      <div v-else class="admin-layout">
        <!-- 编辑区 -->
        <section class="admin-card editor" aria-label="编辑表单">
          <h2 class="admin-section-title">公开介绍</h2>
          <label class="admin-field">
            <span>公开称呼</span>
            <input v-model="settings.introduction.displayName" maxlength="64" />
          </label>
          <label class="admin-field">
            <span>Hero 主标题</span>
            <input v-model="settings.introduction.headline" maxlength="120" />
          </label>
          <label class="admin-field">
            <span>个人介绍（隐私安全）</span>
            <textarea
              v-model="settings.introduction.introduction"
              rows="4"
              maxlength="2000"
            ></textarea>
          </label>

          <h2 class="admin-section-title">技能分组</h2>
          <div v-for="(group, g) in settings.introduction.skillGroups" :key="g" class="admin-group">
            <div class="admin-group-head">
              <input v-model="group.name" class="admin-group-name" maxlength="64" placeholder="分组名称" />
              <button class="icon-btn" title="上移" :disabled="g === 0" @click="moveGroup(g, -1)">↑</button>
              <button class="icon-btn" title="下移" :disabled="g === settings.introduction.skillGroups.length - 1" @click="moveGroup(g, 1)">↓</button>
              <button class="icon-btn danger" title="删除分组" @click="removeGroup(g)">✕</button>
            </div>
            <div class="admin-skills">
              <span v-for="(skill, s) in group.skills" :key="s" class="skill-chip">
                {{ skill }}
                <button class="chip-btn" title="前移" :disabled="s === 0" @click="moveSkill(g, s, -1)">↑</button>
                <button class="chip-btn" title="后移" :disabled="s === group.skills.length - 1" @click="moveSkill(g, s, 1)">↓</button>
                <button class="chip-x" title="删除技术项" @click="removeSkill(g, s)">✕</button>
              </span>
              <span class="skill-add">
                <input
                  v-model="newSkills[g]"
                  class="skill-add-input"
                  maxlength="64"
                  placeholder="新技术项"
                  @keyup.enter="addSkill(g)"
                />
                <button class="icon-btn" title="添加技术项" @click="addSkill(g)">＋</button>
              </span>
            </div>
          </div>
          <button class="btn btn-secondary" @click="addGroup">＋ 添加技能分组</button>

          <h2 class="admin-section-title">作品区设置</h2>
          <label class="admin-field">
            <span>标题</span>
            <input v-model="settings.workSection.title" maxlength="120" />
          </label>
          <label class="admin-field">
            <span>副标题（可留空，留空时前台不显示）</span>
            <input v-model="settings.workSection.subtitle" maxlength="500" />
          </label>

          <h2 class="admin-section-title">联系方式</h2>
          <label class="admin-field">
            <span>公开邮箱</span>
            <input v-model="settings.contact.email" maxlength="254" />
          </label>
          <label class="admin-field">
            <span>GitHub 链接</span>
            <input v-model="settings.contact.githubUrl" maxlength="500" />
          </label>
          <label class="admin-field">
            <span>版权标识</span>
            <input v-model="settings.contact.copyright" maxlength="200" />
          </label>
        </section>

        <!-- 即时预览区：只来自本地表单状态，不改变公开内容 -->
        <aside class="admin-card preview" aria-label="即时预览">
          <h2 class="admin-section-title">即时预览</h2>
          <div class="preview-hero">
            <p class="preview-headline">{{ settings.introduction.headline || '（主标题）' }}</p>
            <p class="preview-intro">{{ settings.introduction.introduction || '（个人介绍）' }}</p>
          </div>
          <div class="preview-groups">
            <div v-for="(group, g) in settings.introduction.skillGroups" :key="g" class="preview-group">
              <p class="preview-group-name">{{ group.name || '（分组名称）' }}</p>
              <div class="preview-skills">
                <span v-for="(skill, s) in group.skills" :key="s" class="preview-skill">{{ skill }}</span>
              </div>
            </div>
          </div>
          <div class="preview-work">
            <p class="preview-work-title">{{ settings.workSection.title || '（作品区标题）' }}</p>
            <p v-if="settings.workSection.subtitle" class="preview-work-subtitle">
              {{ settings.workSection.subtitle }}
            </p>
          </div>
          <div class="preview-contact">
            <p v-if="settings.contact.email">{{ settings.contact.email }}</p>
            <p v-if="settings.contact.githubUrl">{{ settings.contact.githubUrl }}</p>
            <p v-if="settings.contact.copyright">{{ settings.contact.copyright }}</p>
          </div>
        </aside>
      </div>

      <footer v-if="settings" class="admin-footer">
        <p v-if="notice" class="admin-notice" role="status">{{ notice }}</p>
        <p v-if="error" class="admin-error" role="alert">{{ error }}</p>
        <button class="btn btn-primary" :disabled="saving" @click="save">
          {{ saving ? '正在保存并发布…' : '保存并发布' }}
        </button>
      </footer>
    </template>

    <template v-else-if="activeTab === 'projects'">
      <AdminProjectsEditor />
    </template>

    <template v-else-if="activeTab === 'taxonomy'">
      <AdminTaxonomyEditor />
    </template>

    <template v-else>
      <AdminPostsEditor />
    </template>
  </div>
</template>

<script>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AdminProjectsEditor from '@/components/admin/AdminProjectsEditor.vue'
import AdminTaxonomyEditor from '@/components/admin/AdminTaxonomyEditor.vue'
import AdminPostsEditor from '@/components/admin/AdminPostsEditor.vue'
import {
  clearAdminToken,
  fetchMe,
  fetchSiteSettings,
  publishSiteSettings,
  requestLogout,
} from '@/api/admin'

/**
 * Admin Console（#16/#17/#18）：站点设置（公开介绍、技能分组、作品区、
 * 联系方式）与项目管理。即时预览只来自本地表单；点击“保存并发布”才
 * 原子更新公开内容，并有明确的成功/失败反馈。
 */
export default {
  name: 'AdminConsoleView',
  components: { AdminProjectsEditor, AdminTaxonomyEditor, AdminPostsEditor },
  setup() {
    const router = useRouter()
    const me = ref(null)
    const settings = ref(null)
    const activeTab = ref('settings')
    const newSkills = ref({})
    const loadError = ref('')
    const notice = ref('')
    const error = ref('')
    const saving = ref(false)
    const loggingOut = ref(false)

    onMounted(async () => {
      try {
        const [identity, siteSettings] = await Promise.all([fetchMe(), fetchSiteSettings()])
        if (!identity || !siteSettings) {
          clearAdminToken()
          router.replace({ path: '/admin/login', query: { error: 'expired' } })
          return
        }
        me.value = identity
        settings.value = siteSettings
      } catch (e) {
        loadError.value = '无法连接管理服务，请稍后重试。'
      }
    })

    function moveGroup(g, delta) {
      const groups = settings.value.introduction.skillGroups
      const target = g + delta
      if (target < 0 || target >= groups.length) return
      const [moved] = groups.splice(g, 1)
      groups.splice(target, 0, moved)
    }

    function removeGroup(g) {
      settings.value.introduction.skillGroups.splice(g, 1)
    }

    function addGroup() {
      settings.value.introduction.skillGroups.push({ name: '', skills: [] })
    }

    function moveSkill(g, s, delta) {
      const skills = settings.value.introduction.skillGroups[g].skills
      const target = s + delta
      if (target < 0 || target >= skills.length) return
      const [moved] = skills.splice(s, 1)
      skills.splice(target, 0, moved)
    }

    function removeSkill(g, s) {
      settings.value.introduction.skillGroups[g].skills.splice(s, 1)
    }

    function addSkill(g) {
      const value = (newSkills.value[g] || '').trim()
      if (!value) return
      settings.value.introduction.skillGroups[g].skills.push(value)
      newSkills.value[g] = ''
    }

    async function save() {
      saving.value = true
      error.value = ''
      notice.value = ''
      try {
        const result = await publishSiteSettings(settings.value)
        if (result.ok) {
          notice.value = '已保存并发布，公开页面已更新。'
          settings.value = result.payload
        } else {
          error.value = result.message
        }
      } catch (e) {
        error.value = '无法连接管理服务，保存失败，请重试。'
      } finally {
        saving.value = false
      }
    }

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

    return {
      me, settings, activeTab, newSkills, loadError, notice, error, saving, loggingOut,
      moveGroup, removeGroup, addGroup, moveSkill, removeSkill, addSkill, save, doLogout,
    }
  },
}
</script>

<style scoped>
.admin-console {
  padding-top: 60px;
  padding-bottom: 120px;
  max-width: 1080px;
  margin: 0 auto;
}

.admin-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 8px;
}

.admin-eyebrow {
  margin: 0;
  font-family: var(--font-serif);
  color: var(--meta-quiet, #8b8577);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  font-size: 13px;
}

.admin-title {
  font-size: 28px;
  margin: 4px 0 0;
}

.admin-header-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.admin-identity {
  font-size: 14px;
  color: var(--body-muted, #5d584c);
}

.admin-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
}

.admin-tab {
  border: 1px solid var(--line, #e6e0d4);
  background: var(--surface, #fffdf8);
  border-radius: 999px;
  padding: 7px 18px;
  font: inherit;
  font-size: 14px;
  cursor: pointer;
  color: var(--body-muted, #5d584c);
}

.admin-tab.active {
  background: var(--accent, #2f6b4f);
  border-color: var(--accent, #2f6b4f);
  color: #fff;
}

.admin-layout {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: 20px;
  align-items: start;
}

@media (max-width: 900px) {
  .admin-layout {
    grid-template-columns: 1fr;
  }
}

.admin-card {
  background: var(--surface, #fffdf8);
  border: 1px solid var(--line, #e6e0d4);
  border-radius: 12px;
  padding: 22px;
}

.admin-section-title {
  font-size: 16px;
  margin: 22px 0 12px;
  padding-top: 14px;
  border-top: 1px solid var(--line, #e6e0d4);
}

.admin-section-title:first-child {
  margin-top: 0;
  padding-top: 0;
  border-top: none;
}

.admin-field {
  display: block;
  margin-bottom: 12px;
}

.admin-field span {
  display: block;
  font-size: 13px;
  color: var(--body-muted, #5d584c);
  margin-bottom: 4px;
}

.admin-field input,
.admin-field textarea {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--line, #e6e0d4);
  border-radius: 6px;
  font: inherit;
  background: #fff;
  box-sizing: border-box;
}

.admin-group {
  border: 1px solid var(--line, #e6e0d4);
  border-radius: 8px;
  padding: 10px;
  margin-bottom: 10px;
}

.admin-group-head {
  display: flex;
  align-items: center;
  gap: 6px;
}

.admin-group-name {
  flex: 1;
  padding: 6px 8px;
  border: 1px solid var(--line, #e6e0d4);
  border-radius: 6px;
  font: inherit;
}

.icon-btn {
  border: 1px solid var(--line, #e6e0d4);
  background: #fff;
  border-radius: 6px;
  width: 28px;
  height: 28px;
  cursor: pointer;
}

.icon-btn:disabled {
  opacity: 0.4;
  cursor: default;
}

.icon-btn.danger {
  color: #a63d2f;
}

.admin-skills {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.skill-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: var(--chip-bg, #f3efe6);
  border-radius: 999px;
  padding: 3px 10px;
  font-size: 13px;
}

.chip-x,
.chip-btn {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--meta-quiet, #8b8577);
  padding: 0 2px;
  font-size: 12px;
}

.chip-btn:disabled {
  opacity: 0.35;
  cursor: default;
}

.skill-add {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.skill-add-input {
  width: 110px;
  padding: 4px 8px;
  border: 1px solid var(--line, #e6e0d4);
  border-radius: 999px;
  font: inherit;
  font-size: 13px;
}

/* 即时预览 */
.preview {
  position: sticky;
  top: 20px;
}

.preview-hero {
  padding: 18px 0 8px;
}

.preview-headline {
  font-family: var(--font-serif);
  font-size: 30px;
  margin: 0 0 10px;
  line-height: 1.3;
}

.preview-intro {
  color: var(--body-muted, #5d584c);
  font-size: 14px;
  line-height: 1.8;
  margin: 0;
}

.preview-group {
  margin: 14px 0;
}

.preview-group-name {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 6px;
}

.preview-skills {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.preview-skill {
  font-size: 12px;
  background: var(--chip-bg, #f3efe6);
  border-radius: 999px;
  padding: 2px 10px;
}

.preview-work {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--line, #e6e0d4);
}

.preview-work-title {
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 4px;
}

.preview-work-subtitle {
  color: var(--body-muted, #5d584c);
  font-size: 13px;
  margin: 0;
}

.preview-contact {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--line, #e6e0d4);
  font-size: 13px;
  color: var(--body-muted, #5d584c);
}

.preview-contact p {
  margin: 0 0 4px;
}

.admin-footer {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 20px;
  flex-wrap: wrap;
}

.admin-notice {
  color: var(--accent, #2f6b4f);
  font-size: 14px;
  margin: 0;
}

.admin-error {
  color: #a63d2f;
  font-size: 14px;
  margin: 0;
}

.admin-hint {
  color: var(--body-muted, #5d584c);
  font-size: 14px;
}
</style>
