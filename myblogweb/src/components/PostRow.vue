<template>
  <article class="post-row">
    <time class="post-date" :datetime="post.publishedAt">{{ post.publishedAt }}</time>
    <div class="post-main">
      <h3 class="post-title">{{ post.title }}</h3>
      <p class="post-summary">{{ post.summary }}</p>
    </div>
    <div class="post-meta">
      <span class="reading-time">{{ post.readingMinutes }} 分钟</span>
      <router-link class="read-link" :to="`/blog/${post.slug}`">阅读 →</router-link>
    </div>
  </article>
</template>

<script>
export default {
  name: 'PostRow',
  props: {
    post: { type: Object, required: true },
  },
}
</script>

<style scoped>
.post-row {
  display: grid;
  grid-template-columns: 120px 1fr auto;
  gap: 24px;
  align-items: baseline;
  padding: 22px 8px;
  border-top: 1px solid var(--rule);
  transition: background-color var(--transition);
}

.post-row:hover {
  background: rgba(251, 249, 243, 0.6);
}

.post-date {
  font-size: 14px;
  color: var(--meta-quiet);
  font-variant-numeric: tabular-nums;
}

.post-title {
  font-size: 20px;
  margin: 0 0 4px;
}

.post-summary {
  margin: 0;
  font-size: 15px;
  color: var(--body-muted);
}

.post-meta {
  display: flex;
  align-items: center;
  gap: 16px;
}

.reading-time {
  font-size: 14px;
  color: var(--meta-quiet);
  white-space: nowrap;
}

.read-link {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  font-weight: 600;
  white-space: nowrap;
}

@media (max-width: 620px) {
  .post-row {
    grid-template-columns: 1fr;
    gap: 6px;
  }
  .post-meta {
    margin-top: 6px;
  }
}
</style>
