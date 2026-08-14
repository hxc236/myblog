<template>
  <div class="taxonomy-editor">
    <div class="tax-head">
      <p v-if="notice" class="admin-notice" role="status">{{ notice }}</p>
      <p v-if="error" class="admin-error" role="alert">{{ error }}</p>
    </div>

    <div class="tax-columns">
      <!-- 分类 -->
      <section class="admin-card tax-card" aria-label="分类管理">
        <h2 class="tax-title">分类</h2>
        <p class="tax-hint">一篇文章属于一个分类；删除使用中的分类时，文章迁移到“未分类”。</p>
        <ul class="tax-list">
          <li v-for="category in categories" :key="category.id" class="tax-row">
            <input
              v-model="category.name"
              class="tax-name"
              maxlength="64"
              :disabled="category.uncategorized"
            />
            <span v-if="category.uncategorized" class="tax-badge">内置</span>
            <button class="icon-btn" title="保存分类名" :disabled="category.uncategorized" @click="saveCategory(category)">存</button>
            <button class="icon-btn danger" title="删除分类" :disabled="category.uncategorized" @click="removeCategory(category)">✕</button>
          </li>
        </ul>
        <div class="tax-add">
          <input v-model="newCategory" class="tax-add-input" maxlength="64" placeholder="新分类名称" @keyup.enter="addCategory" />
          <button class="btn btn-secondary" @click="addCategory">添加分类</button>
        </div>
      </section>

      <!-- 标签 -->
      <section class="admin-card tax-card" aria-label="标签管理">
        <h2 class="tax-title">标签</h2>
        <p class="tax-hint">可复用的主题标签；删除标签自动解除全部文章关联。</p>
        <ul class="tax-list">
          <li v-for="tag in tags" :key="tag.id" class="tax-row">
            <input v-model="tag.name" class="tax-name" maxlength="64" />
            <button class="icon-btn" title="保存标签名" @click="saveTag(tag)">存</button>
            <button class="icon-btn danger" title="删除标签" @click="removeTag(tag)">✕</button>
          </li>
        </ul>
        <div class="tax-add">
          <input v-model="newTag" class="tax-add-input" maxlength="64" placeholder="新标签名称" @keyup.enter="addTag" />
          <button class="btn btn-secondary" @click="addTag">添加标签</button>
        </div>
      </section>
    </div>
  </div>
</template>

<script>
import { onMounted, ref } from 'vue'
import {
  createCategory,
  createTag,
  deleteCategory,
  deleteTag,
  fetchCategories,
  fetchTags,
  renameCategory,
  renameTag,
} from '@/api/admin'

/**
 * 分类与标签管理（#19）：Uncategorized 内置不可删除；删除使用中的分类会
 * 把文章迁移到“未分类”，删除标签自动解除关联。
 */
export default {
  name: 'AdminTaxonomyEditor',
  setup() {
    const categories = ref([])
    const tags = ref([])
    const newCategory = ref('')
    const newTag = ref('')
    const notice = ref('')
    const error = ref('')

    onMounted(load)

    async function load() {
      categories.value = (await fetchCategories()) || []
      tags.value = (await fetchTags()) || []
    }

    function report(result, okMessage) {
      if (result.ok) {
        notice.value = okMessage
        error.value = ''
        return true
      }
      error.value = result.message
      notice.value = ''
      return false
    }

    async function addCategory() {
      const name = newCategory.value.trim()
      if (!name) return
      const result = await createCategory(name)
      if (report(result, '已添加分类。')) {
        newCategory.value = ''
        await load()
      }
    }

    async function saveCategory(category) {
      const result = await renameCategory(category.id, category.name)
      if (report(result, '分类名已保存。')) {
        await load()
      }
    }

    async function removeCategory(category) {
      if (!window.confirm(`确认删除分类「${category.name}」？使用中的文章将迁移到“未分类”。`)) {
        return
      }
      const result = await deleteCategory(category.id)
      if (report(result, '分类已删除。')) {
        await load()
      }
    }

    async function addTag() {
      const name = newTag.value.trim()
      if (!name) return
      const result = await createTag(name)
      if (report(result, '已添加标签。')) {
        newTag.value = ''
        await load()
      }
    }

    async function saveTag(tag) {
      const result = await renameTag(tag.id, tag.name)
      if (report(result, '标签名已保存。')) {
        await load()
      }
    }

    async function removeTag(tag) {
      if (!window.confirm(`确认删除标签「${tag.name}」？将解除全部文章的关联。`)) {
        return
      }
      const result = await deleteTag(tag.id)
      if (report(result, '标签已删除。')) {
        await load()
      }
    }

    return {
      categories, tags, newCategory, newTag, notice, error,
      addCategory, saveCategory, removeCategory, addTag, saveTag, removeTag,
    }
  },
}
</script>

<style scoped>
.tax-head {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.tax-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  align-items: start;
}

@media (max-width: 800px) {
  .tax-columns {
    grid-template-columns: 1fr;
  }
}

.tax-card {
  background: var(--surface, #fffdf8);
  border: 1px solid var(--line, #e6e0d4);
  border-radius: 12px;
  padding: 20px;
}

.tax-title {
  font-size: 17px;
  margin: 0 0 6px;
}

.tax-hint {
  font-size: 13px;
  color: var(--body-muted, #5d584c);
  margin: 0 0 14px;
  line-height: 1.6;
}

.tax-list {
  list-style: none;
  margin: 0 0 14px;
  padding: 0;
}

.tax-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 0;
  border-bottom: 1px solid var(--line, #e6e0d4);
}

.tax-name {
  flex: 1;
  padding: 6px 8px;
  border: 1px solid var(--line, #e6e0d4);
  border-radius: 6px;
  font: inherit;
}

.tax-name:disabled {
  background: var(--chip-bg, #f3efe6);
  color: var(--meta-quiet, #8b8577);
}

.tax-badge {
  font-size: 12px;
  color: var(--meta-quiet, #8b8577);
  border: 1px solid var(--line, #e6e0d4);
  border-radius: 999px;
  padding: 1px 8px;
}

.tax-add {
  display: flex;
  gap: 8px;
}

.tax-add-input {
  flex: 1;
  padding: 7px 10px;
  border: 1px solid var(--line, #e6e0d4);
  border-radius: 6px;
  font: inherit;
}

.icon-btn {
  border: 1px solid var(--line, #e6e0d4);
  background: #fff;
  border-radius: 6px;
  width: 30px;
  height: 30px;
  cursor: pointer;
  font-size: 13px;
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
</style>
