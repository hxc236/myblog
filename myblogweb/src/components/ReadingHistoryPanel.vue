<template>
  <aside v-if="entries.length" class="reading-history" aria-label="最近阅读">
    <div class="rh-head">
      <h2 class="rh-title">最近阅读</h2>
      <button class="rh-clear" @click="clear">一键清除</button>
    </div>
    <ul class="rh-list">
      <li v-for="entry in entries" :key="entry.slug" class="rh-item">
        <router-link :to="`/blog/${entry.slug}`" class="rh-link">
          {{ entry.title }}
        </router-link>
      </li>
    </ul>
    <p class="rh-note">仅保存在当前设备，不同步到服务器。</p>
  </aside>
</template>

<script>
import { onMounted, ref } from 'vue'
import { loadJson } from '@/api'
import { clearReadingHistory, loadReadingHistory, pruneReadingHistory } from '@/composables/readingHistory'

/**
 * 最近阅读（#23）：设备本地 Reading History，最多十篇，一键清除；
 * 渲染前清理已归档或不存在的引用。
 */
export default {
  name: 'ReadingHistoryPanel',
  setup() {
    const entries = ref([])

    onMounted(async () => {
      try {
        entries.value = await pruneReadingHistory((slug) => loadJson(`/api/v1/posts/${slug}`))
      } catch (e) {
        entries.value = loadReadingHistory()
      }
    })

    function clear() {
      clearReadingHistory()
      entries.value = []
    }

    return { entries, clear }
  },
}
</script>

<style scoped>
.reading-history {
  margin-top: 40px;
  padding-top: 18px;
  border-top: 1px solid var(--line, #e6e0d4);
}

.rh-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.rh-title {
  font-size: 15px;
  margin: 0;
}

.rh-clear {
  border: none;
  background: none;
  color: var(--meta-quiet, #8b8577);
  font-size: 13px;
  cursor: pointer;
  padding: 2px 0;
}

.rh-clear:hover {
  color: #a63d2f;
}

.rh-list {
  list-style: none;
  margin: 10px 0 6px;
  padding: 0;
}

.rh-item {
  padding: 5px 0;
}

.rh-link {
  color: var(--body-muted, #5d584c);
  text-decoration: none;
  font-size: 14px;
}

.rh-link:hover {
  color: var(--accent, #2f6b4f);
  text-decoration: underline;
}

.rh-note {
  font-size: 12px;
  color: var(--meta-quiet, #8b8577);
  margin: 6px 0 0;
}
</style>
