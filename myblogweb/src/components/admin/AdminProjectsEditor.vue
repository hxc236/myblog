<template>
  <div class="projects-editor">
    <div class="pe-head">
      <p v-if="notice" class="admin-notice" role="status">{{ notice }}</p>
      <p v-if="error" class="admin-error" role="alert">{{ error }}</p>
      <button class="btn btn-primary" :disabled="!projects || saving" @click="addProject">
        ＋ 新增 Project
      </button>
    </div>

    <p v-if="!projects" class="admin-hint">正在加载 Project…</p>

    <div v-else-if="projects.length === 0" class="admin-hint">还没有 Project，点击“新增 Project”开始。</div>

    <div v-for="(project, i) in projects" :key="project.id" class="project-card">
      <div class="project-card-head">
        <span class="project-index">{{ i + 1 }}</span>
        <input v-model="project.title" class="project-title-input" maxlength="200" placeholder="作品标题" />
        <button class="icon-btn" title="上移" :disabled="i === 0" @click="moveProject(i, -1)">↑</button>
        <button class="icon-btn" title="下移" :disabled="i === projects.length - 1" @click="moveProject(i, 1)">↓</button>
        <button class="icon-btn danger" title="删除 Project" @click="removeProject(project)">✕</button>
      </div>

      <label class="admin-field">
        <span>成果说明</span>
        <textarea v-model="project.summary" rows="3" maxlength="2000"></textarea>
      </label>
      <div class="project-row-fields">
        <label class="admin-field">
          <span>职责</span>
          <input v-model="project.role" maxlength="200" />
        </label>
        <label class="admin-field">
          <span>年份</span>
          <input v-model="project.year" maxlength="20" />
        </label>
      </div>
      <div class="project-row-fields">
        <label class="admin-field">
          <span>代码仓库链接</span>
          <input v-model="project.repositoryUrl" maxlength="500" placeholder="https://github.com/…" />
        </label>
        <label class="admin-field">
          <span>演示链接</span>
          <input v-model="project.demoUrl" maxlength="500" placeholder="https://…" />
        </label>
      </div>

      <div class="admin-field">
        <span>技术栈</span>
        <div class="admin-skills">
          <span v-for="(skill, s) in project.stack" :key="s" class="skill-chip">
            {{ skill }}
            <button class="chip-btn" title="前移" :disabled="s === 0" @click="moveStack(project, s, -1)">↑</button>
            <button class="chip-btn" title="后移" :disabled="s === project.stack.length - 1" @click="moveStack(project, s, 1)">↓</button>
            <button class="chip-x" title="删除技术栈项" @click="removeStack(project, s)">✕</button>
          </span>
          <span class="skill-add">
            <input
              v-model="newStack[project.id]"
              class="skill-add-input"
              maxlength="100"
              placeholder="新技术栈项"
              @keyup.enter="addStack(project)"
            />
            <button class="icon-btn" title="添加技术栈项" @click="addStack(project)">＋</button>
          </span>
        </div>
      </div>

      <div class="project-card-foot">
        <label class="admin-field featured-select">
          <span>首页精选位置</span>
          <select v-model.number="project.featuredOrder">
            <option :value="null">不精选</option>
            <option :value="1">精选 1</option>
            <option :value="2">精选 2</option>
            <option :value="3">精选 3</option>
          </select>
        </label>
        <button class="btn btn-primary" :disabled="saving" @click="saveProject(project)">
          保存并发布
        </button>
      </div>
    </div>
  </div>
</template>

<script>
import { onMounted, ref } from 'vue'
import { createProject, deleteProject, fetchProjects, updateProject } from '@/api/admin'

/**
 * Project 管理（#18）：新增、编辑、排序、删除与首页精选（0–3）。
 * 保存即发布（无 Draft/修订历史）；每张卡片独立保存，带成功/失败反馈。
 */
export default {
  name: 'AdminProjectsEditor',
  setup() {
    const projects = ref(null)
    const newStack = ref({})
    const notice = ref('')
    const error = ref('')
    const saving = ref(false)

    onMounted(load)

    async function load() {
      projects.value = await fetchProjects()
    }

    async function addProject() {
      saving.value = true
      error.value = ''
      notice.value = ''
      try {
        const result = await createProject({
          title: '',
          summary: '',
          role: '',
          year: '',
          stack: [],
          repositoryUrl: '',
          demoUrl: '',
          featuredOrder: null,
        })
        if (!result.ok) {
          error.value = result.message
          return
        }
        await load()
        notice.value = '已新增 Project，请填写内容后保存。'
      } catch (e) {
        error.value = '无法连接管理服务，请稍后重试。'
      } finally {
        saving.value = false
      }
    }

    async function saveProject(project) {
      saving.value = true
      error.value = ''
      notice.value = ''
      try {
        const result = await updateProject(project.id, {
          title: project.title,
          summary: project.summary,
          role: project.role,
          year: project.year,
          stack: project.stack,
          repositoryUrl: project.repositoryUrl,
          demoUrl: project.demoUrl,
          displayOrder: project.displayOrder,
          featuredOrder: project.featuredOrder,
        })
        if (!result.ok) {
          error.value = result.message
          return
        }
        Object.assign(project, result.payload)
        notice.value = '已保存并发布。'
      } catch (e) {
        error.value = '无法连接管理服务，保存失败，请重试。'
      } finally {
        saving.value = false
      }
    }

    async function removeProject(project) {
      if (!window.confirm(`确认删除 Project「${project.title || '未命名'}」？`)) {
        return
      }
      saving.value = true
      error.value = ''
      notice.value = ''
      try {
        const result = await deleteProject(project.id)
        if (!result.ok) {
          error.value = result.message
          return
        }
        await load()
        notice.value = '已删除。'
      } catch (e) {
        error.value = '无法连接管理服务，请稍后重试。'
      } finally {
        saving.value = false
      }
    }

    async function moveProject(i, delta) {
      const target = i + delta
      if (target < 0 || target >= projects.value.length) return
      const a = projects.value[i]
      const b = projects.value[target]
      saving.value = true
      error.value = ''
      try {
        // 相邻交换 displayOrder：先后两次保存，服务端事务内平移其余项目
        const first = await updateProject(a.id, {
          ...payloadOf(a),
          displayOrder: b.displayOrder,
        })
        const second = first.ok
          ? await updateProject(b.id, { ...payloadOf(b), displayOrder: a.displayOrder })
          : first
        if (!second.ok) {
          error.value = second.message
          await load()
          return
        }
        await load()
      } catch (e) {
        error.value = '无法连接管理服务，排序失败，请重试。'
      } finally {
        saving.value = false
      }
    }

    function payloadOf(project) {
      return {
        title: project.title,
        summary: project.summary,
        role: project.role,
        year: project.year,
        stack: project.stack,
        repositoryUrl: project.repositoryUrl,
        demoUrl: project.demoUrl,
        displayOrder: project.displayOrder,
        featuredOrder: project.featuredOrder,
      }
    }

    function addStack(project) {
      const value = (newStack.value[project.id] || '').trim()
      if (!value) return
      project.stack.push(value)
      newStack.value[project.id] = ''
    }

    function removeStack(project, s) {
      project.stack.splice(s, 1)
    }

    function moveStack(project, s, delta) {
      const target = s + delta
      if (target < 0 || target >= project.stack.length) return
      const [moved] = project.stack.splice(s, 1)
      project.stack.splice(target, 0, moved)
    }

    return {
      projects, newStack, notice, error, saving,
      addProject, saveProject, removeProject, moveProject,
      addStack, removeStack, moveStack,
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

.project-card {
  border: 1px solid var(--line, #e6e0d4);
  border-radius: 12px;
  padding: 18px;
  margin-bottom: 16px;
  background: var(--surface, #fffdf8);
}

.project-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.project-index {
  font-family: var(--font-serif);
  color: var(--meta-quiet, #8b8577);
  font-size: 15px;
}

.project-title-input {
  flex: 1;
  font-size: 17px;
  font-weight: 600;
  padding: 8px 10px;
  border: 1px solid var(--line, #e6e0d4);
  border-radius: 6px;
  font-family: inherit;
}

.project-row-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

@media (max-width: 700px) {
  .project-row-fields {
    grid-template-columns: 1fr;
  }
}

.project-card-foot {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin-top: 6px;
  flex-wrap: wrap;
}

.featured-select {
  margin-bottom: 0;
}

.featured-select select {
  padding: 8px 10px;
  border: 1px solid var(--line, #e6e0d4);
  border-radius: 6px;
  font: inherit;
  background: #fff;
  min-width: 140px;
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

.admin-skills {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
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
