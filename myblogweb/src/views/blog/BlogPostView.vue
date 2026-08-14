<template>
  <div class="page post-page">
    <article v-if="post" class="post-article">
      <header class="post-header">
        <h1 class="post-title">{{ post.title }}</h1>
        <p class="post-summary">{{ post.summary }}</p>
        <p class="post-meta">
          <time :datetime="post.publishedAt">{{ post.publishedAt }}</time>
          <span aria-hidden="true">·</span>
          <span>{{ post.readingMinutes }} 分钟阅读</span>
        </p>
      </header>
      <MarkdownView :markdown="post.body" />
    </article>

    <div v-else-if="phase === 'not-found'" class="not-found">
      <h1 class="nf-title">文章不存在</h1>
      <p class="nf-text">这篇博客不存在或已被移除。</p>
      <router-link class="btn btn-primary" to="/blog">返回博客列表</router-link>
    </div>

    <div v-else class="post-skeleton-wrap" aria-hidden="true">
      <div class="skeleton title-skeleton"></div>
      <div class="skeleton line"></div>
      <div class="skeleton line short"></div>
      <div class="skeleton line"></div>
    </div>

    <LoadStateNotice :phase="phase" @retry="loadPost" />

    <router-link class="back-home" to="/blog">← 返回博客列表</router-link>
  </div>
</template>

<script>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { loadJson } from '@/api'
import { usePhaseNotice } from '@/composables/usePhaseNotice'
import MarkdownView from '@/components/MarkdownView.vue'
import LoadStateNotice from '@/components/LoadStateNotice.vue'

export default {
  name: 'BlogPostView',
  components: { MarkdownView, LoadStateNotice },
  setup() {
    const route = useRoute()
    const post = ref(null)
    // loading | starting | retrying | ready | error | not-found
    const { phase, onPhase } = usePhaseNotice()

    async function loadPost() {
      phase.value = 'loading'
      try {
        post.value = await loadJson(`/api/v1/posts/${route.params.slug}`, { onPhase })
        phase.value = 'ready'
      } catch (e) {
        // 后端对不存在/格式错误的 slug 返回 JSON 404 → 站内“文章不存在”
        phase.value = e && e.status === 404 ? 'not-found' : 'error'
      }
    }

    onMounted(loadPost)

    return { post, phase, loadPost }
  },
}
</script>

<style scoped>
.post-page {
  padding-top: 150px;
  padding-bottom: 48px;
}

.post-article {
  max-width: 720px;
  margin: 0 auto;
}

.post-header {
  margin-bottom: 36px;
}

.post-title {
  font-size: clamp(28px, 4vw, 40px);
  margin: 0 0 12px;
}

.post-summary {
  color: var(--body-muted);
  font-size: 17px;
  margin: 0 0 14px;
}

.post-meta {
  display: flex;
  gap: 10px;
  font-size: 14px;
  color: var(--meta-quiet);
  margin: 0;
}

.not-found {
  max-width: 560px;
  margin: 0 auto;
  text-align: center;
  padding: 48px 0;
}

.nf-title {
  font-size: 30px;
  margin: 0 0 10px;
}

.nf-text {
  color: var(--body-muted);
  margin: 0 0 24px;
}

.post-skeleton-wrap {
  max-width: 720px;
  margin: 0 auto;
}

.title-skeleton {
  width: 70%;
  height: 48px;
  margin-bottom: 24px;
}

.line {
  height: 18px;
  margin-bottom: 14px;
}

.line.short {
  width: 55%;
}

.back-home {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  margin-top: 32px;
  font-weight: 600;
}
</style>
