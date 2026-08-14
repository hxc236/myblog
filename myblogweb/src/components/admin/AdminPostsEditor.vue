<template>
  <div class="posts-editor">
    <!-- 列表视图 -->
    <template v-if="view === 'list'">
      <div class="pe-head">
        <p v-if="notice" class="admin-notice" role="status">{{ notice }}</p>
        <p v-if="error" class="admin-error" role="alert">{{ error }}</p>
        <button class="btn btn-primary" :disabled="!posts" @click="newPost">＋ 新建文章</button>
      </div>
      <p v-if="!posts" class="admin-hint">正在加载文章…</p>
      <p v-else-if="posts.length === 0" class="admin-hint">还没有文章，点击“新建文章”开始写作。</p>
      <ul v-else class="post-list">
        <li v-for="post in posts" :key="post.id" class="post-row">
          <button class="post-open" @click="openPost(post.id)">
            <span class="post-title">{{ post.title || '（未命名草稿）' }}</span>
            <span class="post-state" :class="'state-' + post.state">
              {{ stateLabel(post.state) }}
            </span>
            <span class="post-meta">
              {{ post.categoryName || '未分类' }}
              · {{ post.updatedAt ? formatTime(post.updatedAt) : '' }}
            </span>
          </button>
        </li>
      </ul>
    </template>

    <!-- 编辑视图 -->
    <template v-else-if="detail">
      <div class="pe-head">
        <button class="btn btn-secondary" @click="backToList">← 返回列表</button>
        <p v-if="notice" class="admin-notice" role="status">{{ notice }}</p>
        <p v-if="error" class="admin-error" role="alert">{{ error }}</p>
        <span v-if="stashedAt" class="stash-hint">本地已暂存：{{ formatTime(stashedAt) }}</span>
      </div>

      <div class="editor-layout">
        <section class="admin-card editor-form" aria-label="文章编辑">
          <label class="admin-field">
            <span>标题</span>
            <input v-model="form.title" maxlength="200" />
          </label>
          <div class="editor-row">
            <label class="admin-field">
              <span>slug（小写字母/数字/连字符，发布时唯一）</span>
              <input v-model="form.slug" maxlength="64" placeholder="my-first-post" />
            </label>
            <label class="admin-field">
              <span>分类（发布必须选择）</span>
              <select v-model="form.categoryId">
                <option :value="null">未选择</option>
                <option v-for="category in categories" :key="category.id" :value="category.id">
                  {{ category.name }}
                </option>
              </select>
            </label>
          </div>
          <label class="admin-field">
            <span>摘要</span>
            <textarea v-model="form.summary" rows="2" maxlength="500"></textarea>
          </label>
          <label class="admin-field">
            <span>标签</span>
            <div class="tag-checkboxes">
              <label v-for="tag in tags" :key="tag.id" class="tag-check">
                <input type="checkbox" :value="tag.id" v-model="form.tagIds" />
                {{ tag.name }}
              </label>
              <span v-if="tags.length === 0" class="admin-hint">暂无标签，可先在“分类与标签”页添加。</span>
            </div>
          </label>
          <label class="admin-field">
            <span>Markdown 正文（发布后作为权威内容保存）</span>
            <textarea v-model="form.bodyMarkdown" rows="16" class="md-input"></textarea>
          </label>
          <div class="editor-actions">
            <button class="btn btn-primary" :disabled="saving" @click="saveDraft">
              {{ saving ? '正在保存…' : '保存草稿' }}
            </button>
            <button class="btn btn-secondary" :disabled="saving || publishing" @click="publish">
              {{ publishing ? '正在发布…' : '发布' }}
            </button>
          </div>
        </section>

        <aside class="admin-card preview-pane" aria-label="预览">
          <h2 class="preview-title">预览（仅管理员可见）</h2>
          <p class="preview-post-title">{{ form.title || '（标题）' }}</p>
          <p class="preview-summary">{{ form.summary }}</p>
          <MarkdownView :markdown="form.bodyMarkdown" />
        </aside>
      </div>
    </template>
  </div>
</template>

<script>
import { onMounted, ref, watch } from 'vue'
import MarkdownView from '@/components/MarkdownView.vue'
import {
  createPost,
  fetchCategories,
  fetchPost,
  fetchPosts,
  fetchTags,
  publishPost,
  savePostDraft,
} from '@/api/admin'

const STASH_PREFIX = 'myblog:post-draft:'

/**
 * 写作页（#20）：文章列表、Draft 编辑（标题/摘要/Markdown/slug/分类/标签）、
 * 即时预览（仅管理员）、浏览器本地定期暂存与恢复、保存草稿与立即发布反馈。
 */
export default {
  name: 'AdminPostsEditor',
  components: { MarkdownView },
  setup() {
    const view = ref('list')
    const posts = ref(null)
    const detail = ref(null)
    const categories = ref([])
    const tags = ref([])
    const form = ref({ title: '', summary: '', bodyMarkdown: '', slug: '', categoryId: null, tagIds: [] })
    const notice = ref('')
    const error = ref('')
    const saving = ref(false)
    const publishing = ref(false)
    const stashedAt = ref(null)
    let stashTimer = null

    onMounted(async () => {
      posts.value = await fetchPosts()
      const [cats, tgs] = await Promise.all([fetchCategories(), fetchTags()])
      categories.value = cats || []
      tags.value = tgs || []
    })

    function stateLabel(state) {
      return { draft: '草稿', published: '已发布', draft_published: '草稿+已发布' }[state] || state
    }

    function formatTime(iso) {
      const date = new Date(iso)
      const pad = (n) => String(n).padStart(2, '0')
      return `${date.getMonth() + 1}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
    }

    async function newPost() {
      error.value = ''
      notice.value = ''
      try {
        const created = await createPost()
        if (!created) {
          error.value = '创建失败，请重试。'
          return
        }
        await openPost(created.id)
      } catch (e) {
        error.value = '无法连接管理服务，请稍后重试。'
      }
    }

    async function openPost(id) {
      error.value = ''
      notice.value = ''
      const detailData = await fetchPost(id)
      if (!detailData) {
        error.value = '加载失败：文章不存在或会话已过期。'
        return
      }
      detail.value = detailData
      posts.value = await fetchPosts()
      // 本地暂存恢复：比服务端更新的未保存内容优先
      const stashed = readStash(id)
      if (stashed) {
        form.value = { ...detailData, ...stashed }
        stashedAt.value = stashed._at
        notice.value = '已恢复本地暂存内容（尚未保存到服务器）。'
      } else {
        form.value = {
          title: detailData.title,
          summary: detailData.summary,
          bodyMarkdown: detailData.bodyMarkdown,
          slug: detailData.slug,
          categoryId: detailData.categoryId,
          tagIds: detailData.tagIds || [],
        }
      }
      view.value = 'editor'
    }

    function backToList() {
      view.value = 'list'
      detail.value = null
      error.value = ''
      notice.value = ''
    }

    // 定期暂存到当前浏览器（#14 实现决策：编辑过程防意外关闭）
    watch(
      form,
      () => {
        if (!detail.value) return
        clearTimeout(stashTimer)
        stashTimer = setTimeout(() => {
          const payload = { ...form.value, _at: new Date().toISOString() }
          try {
            window.localStorage.setItem(STASH_PREFIX + detail.value.id, JSON.stringify(payload))
            stashedAt.value = payload._at
          } catch (e) {
            // 存储不可用时静默降级，不打断编辑
          }
        }, 2000)
      },
      { deep: true },
    )

    function readStash(id) {
      try {
        const raw = window.localStorage.getItem(STASH_PREFIX + id)
        return raw ? JSON.parse(raw) : null
      } catch (e) {
        return null
      }
    }

    function clearStash(id) {
      try {
        window.localStorage.removeItem(STASH_PREFIX + id)
      } catch (e) {
        // 忽略
      }
      stashedAt.value = null
    }

    async function saveDraft() {
      saving.value = true
      error.value = ''
      notice.value = ''
      try {
        const result = await savePostDraft(detail.value.id, payload())
        if (!result.ok) {
          error.value = result.message
          return
        }
        detail.value = result.payload
        notice.value = '草稿已保存。'
      } catch (e) {
        error.value = '无法连接管理服务，保存失败，请重试。'
      } finally {
        saving.value = false
      }
    }

    async function publish() {
      publishing.value = true
      error.value = ''
      notice.value = ''
      try {
        const result = await publishPost(detail.value.id)
        if (!result.ok) {
          error.value = result.message
          return
        }
        detail.value = result.payload
        clearStash(detail.value.id)
        notice.value = '已发布，公开页面已更新。'
        posts.value = await fetchPosts()
      } catch (e) {
        error.value = '无法连接管理服务，发布失败，请重试。'
      } finally {
        publishing.value = false
      }
    }

    function payload() {
      return {
        title: form.value.title,
        summary: form.value.summary,
        bodyMarkdown: form.value.bodyMarkdown,
        slug: form.value.slug,
        categoryId: form.value.categoryId,
        tagIds: form.value.tagIds,
      }
    }

    return {
      view, posts, detail, categories, tags, form, notice, error, saving, publishing, stashedAt,
      stateLabel, formatTime, newPost, openPost, backToList, saveDraft, publish,
    }
  },
}
</script>

<style scoped>
.pe-head {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.post-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.post-row {
  border-bottom: 1px solid var(--line, #e6e0d4);
}

.post-open {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 14px 6px;
  background: none;
  border: none;
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.post-open:hover .post-title {
  color: var(--accent, #2f6b4f);
}

.post-title {
  flex: 1;
  font-size: 15px;
  font-weight: 600;
}

.post-state {
  font-size: 12px;
  border-radius: 999px;
  padding: 2px 10px;
  white-space: nowrap;
}

.state-draft {
  background: var(--chip-bg, #f3efe6);
  color: var(--meta-quiet, #8b8577);
}

.state-published {
  background: #e2efe7;
  color: #2f6b4f;
}

.state-draft_published {
  background: #fdf3dd;
  color: #8a6d1a;
}

.post-meta {
  font-size: 13px;
  color: var(--meta-quiet, #8b8577);
  white-space: nowrap;
}

.editor-layout {
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 20px;
  align-items: start;
}

@media (max-width: 900px) {
  .editor-layout {
    grid-template-columns: 1fr;
  }
}

.editor-form {
  background: var(--surface, #fffdf8);
  border: 1px solid var(--line, #e6e0d4);
  border-radius: 12px;
  padding: 22px;
}

.editor-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

@media (max-width: 700px) {
  .editor-row {
    grid-template-columns: 1fr;
  }
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
.admin-field textarea,
.admin-field select {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--line, #e6e0d4);
  border-radius: 6px;
  font: inherit;
  background: #fff;
  box-sizing: border-box;
}

.md-input {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 13px;
  line-height: 1.6;
}

.tag-checkboxes {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  padding: 4px 0;
}

.tag-check {
  font-size: 14px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.editor-actions {
  display: flex;
  gap: 12px;
  margin-top: 8px;
}

.preview-pane {
  position: sticky;
  top: 20px;
  background: var(--surface, #fffdf8);
  border: 1px solid var(--line, #e6e0d4);
  border-radius: 12px;
  padding: 22px;
  max-height: calc(100vh - 120px);
  overflow-y: auto;
}

.preview-title {
  font-size: 14px;
  color: var(--meta-quiet, #8b8577);
  margin: 0 0 10px;
}

.preview-post-title {
  font-family: var(--font-serif);
  font-size: 26px;
  margin: 0 0 8px;
}

.preview-summary {
  color: var(--body-muted, #5d584c);
  font-size: 14px;
  margin: 0 0 14px;
}

.stash-hint {
  font-size: 13px;
  color: var(--meta-quiet, #8b8577);
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
