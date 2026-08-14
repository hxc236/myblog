<template>
  <div class="page blog-list">
    <h1 class="page-title">全部博客</h1>
    <p v-if="posts && posts.length" class="page-note">按发布日期倒序排列。</p>

    <div v-if="posts && posts.length" class="post-list">
      <PostRow v-for="post in posts" :key="post.slug" :post="post" />
    </div>
    <div v-else-if="phase !== 'error'" class="post-list" aria-hidden="true">
      <div v-for="i in 3" :key="i" class="skeleton post-skeleton"></div>
    </div>

    <LoadStateNotice :phase="phase" @retry="loadPosts" />

    <router-link class="back-home" to="/">← 返回首页</router-link>
  </div>
</template>

<script>
import { onMounted, ref } from 'vue'
import { loadJson } from '@/api'
import { usePhaseNotice } from '@/composables/usePhaseNotice'
import PostRow from '@/components/PostRow.vue'
import LoadStateNotice from '@/components/LoadStateNotice.vue'

export default {
  name: 'BlogListView',
  components: { PostRow, LoadStateNotice },
  setup() {
    const posts = ref(null)
    const { phase, onPhase } = usePhaseNotice()

    async function loadPosts() {
      phase.value = 'loading'
      try {
        posts.value = await loadJson('/api/v1/posts', { onPhase })
        phase.value = 'ready'
      } catch (e) {
        phase.value = 'error'
      }
    }

    onMounted(loadPosts)

    return { posts, phase, loadPosts }
  },
}
</script>

<style scoped>
.blog-list {
  padding-top: 150px;
  padding-bottom: 48px;
}

.page-title {
  font-size: clamp(30px, 4vw, 42px);
  margin: 0 0 8px;
}

.page-note {
  color: var(--body-muted);
  margin: 0 0 20px;
}

.post-list {
  border-bottom: 1px solid var(--rule);
}

.post-skeleton {
  height: 64px;
  margin: 18px 0;
}

.back-home {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  margin-top: 28px;
  font-weight: 600;
}
</style>
