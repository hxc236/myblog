<template>
  <div class="analytics-view">
    <p v-if="error" class="admin-error" role="alert">{{ error }}</p>
    <p v-else-if="!overview" class="admin-hint">正在加载统计…</p>

    <template v-else>
      <div class="an-cards">
        <div class="an-card">
          <p class="an-card-label">全站累计 Page View</p>
          <p class="an-card-value">{{ overview.siteTotal.toLocaleString() }}</p>
        </div>
        <div class="an-card">
          <p class="an-card-label">最近三十天</p>
          <p class="an-card-value">{{ last30Total.toLocaleString() }}</p>
        </div>
      </div>

      <section class="an-section">
        <h2 class="an-title">最近三十天趋势</h2>
        <div class="an-bars" role="img" :aria-label="`最近三十天每日 Page View，共 ${last30Total}`">
          <div v-for="(d, i) in overview.last30Days" :key="d.day" class="an-bar-col" :title="`${d.day}: ${d.count}`">
            <div
              class="an-bar"
              :style="{ height: barHeight(d.count) + '%' }"
            ></div>
            <span v-if="i % 5 === 0" class="an-bar-label">{{ shortDay(d.day) }}</span>
          </div>
        </div>
      </section>

      <section class="an-section">
        <h2 class="an-title">访问量最高的十篇文章</h2>
        <ul class="an-top">
          <li v-for="(post, i) in overview.topPosts" :key="post.postId" class="an-top-row">
            <span class="an-rank">{{ i + 1 }}</span>
            <button class="an-top-link" @click="selectPost(post)">{{ post.title || post.slug }}</button>
            <span class="an-top-total">{{ post.total.toLocaleString() }}</span>
            <span class="an-top-slug">{{ post.slug }}</span>
          </li>
          <li v-if="overview.topPosts.length === 0" class="an-empty">暂无数据。</li>
        </ul>
      </section>

      <section v-if="trend" class="an-section">
        <h2 class="an-title">
          单篇趋势：{{ trendTitle }}
          <button
            class="an-days"
            :class="{ active: trendDays === 30 }"
            @click="loadTrend(selectedPost, 30)"
          >30 天</button>
          <button
            class="an-days"
            :class="{ active: trendDays === 90 }"
            @click="loadTrend(selectedPost, 90)"
          >90 天</button>
        </h2>
        <div class="an-bars" role="img">
          <div v-for="d in trend" :key="d.day" class="an-bar-col" :title="`${d.day}: ${d.count}`">
            <div class="an-bar" :style="{ height: barHeight(d.count) + '%' }"></div>
          </div>
        </div>
      </section>

      <p class="an-note">统计只包含匿名聚合（按文章与日期），不展示访客身份、独立访客数或画像。</p>
    </template>
  </div>
</template>

<script>
import { computed, onMounted, ref } from 'vue'
import { fetchAnalytics, fetchPostTrend } from '@/api/admin'

/**
 * 内容分析（#25）：全站累计、最近三十天趋势、访问量最高十篇与单篇
 * 30/90 天趋势；只展示匿名聚合，无访客画像。
 */
export default {
  name: 'AdminAnalyticsView',
  setup() {
    const overview = ref(null)
    const trend = ref(null)
    const trendTitle = ref('')
    const trendDays = ref(30)
    const selectedPost = ref(null)
    const error = ref('')

    const last30Total = computed(() =>
      (overview.value ? overview.value.last30Days : []).reduce((sum, d) => sum + d.count, 0))

    const maxCount = computed(() => {
      const values = overview.value ? overview.value.last30Days.map((d) => d.count) : []
      if (trend.value) values.push(...trend.value.map((d) => d.count))
      return Math.max(1, ...values)
    })

    onMounted(async () => {
      overview.value = await fetchAnalytics()
    })

    function barHeight(count) {
      return Math.max(2, Math.round((count / maxCount.value) * 100))
    }

    function shortDay(day) {
      return day.slice(5)
    }

    async function selectPost(post) {
      await loadTrend(post, 30)
    }

    async function loadTrend(post, days) {
      if (!post) return
      selectedPost.value = post
      trendDays.value = days
      trendTitle.value = post.title || post.slug
      trend.value = await fetchPostTrend(post.postId, days)
    }

    return { overview, trend, trendTitle, trendDays, error, last30Total, barHeight, shortDay, selectPost, loadTrend }
  },
}
</script>

<style scoped>
.an-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 14px;
  margin-bottom: 20px;
}

.an-card {
  background: var(--surface, #fffdf8);
  border: 1px solid var(--line, #e6e0d4);
  border-radius: 12px;
  padding: 18px 20px;
}

.an-card-label {
  font-size: 13px;
  color: var(--body-muted, #5d584c);
  margin: 0 0 6px;
}

.an-card-value {
  font-family: var(--font-serif);
  font-size: 30px;
  margin: 0;
}

.an-section {
  margin-bottom: 26px;
}

.an-title {
  font-size: 16px;
  margin: 0 0 12px;
}

.an-bars {
  display: flex;
  align-items: flex-end;
  gap: 2px;
  height: 120px;
  padding: 8px 0 0;
  border-bottom: 1px solid var(--line, #e6e0d4);
  overflow-x: auto;
}

.an-bar-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  min-width: 8px;
  flex: 1;
}

.an-bar {
  width: 100%;
  max-width: 18px;
  background: var(--accent, #2f6b4f);
  border-radius: 2px 2px 0 0;
  min-height: 2px;
}

.an-bar-label {
  font-size: 10px;
  color: var(--meta-quiet, #8b8577);
  margin-top: 3px;
}

.an-top {
  list-style: none;
  margin: 0;
  padding: 0;
}

.an-top-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 4px;
  border-bottom: 1px solid var(--line, #e6e0d4);
  font-size: 14px;
}

.an-rank {
  color: var(--meta-quiet, #8b8577);
  width: 20px;
  text-align: right;
  font-family: var(--font-serif);
}

.an-top-link {
  flex: 1;
  text-align: left;
  border: none;
  background: none;
  font: inherit;
  cursor: pointer;
  font-weight: 600;
}

.an-top-link:hover {
  color: var(--accent, #2f6b4f);
}

.an-top-total {
  color: var(--accent, #2f6b4f);
  font-weight: 600;
}

.an-top-slug {
  color: var(--meta-quiet, #8b8577);
  font-size: 12px;
}

.an-empty {
  color: var(--meta-quiet, #8b8577);
  font-size: 14px;
}

.an-days {
  border: 1px solid var(--line, #e6e0d4);
  background: #fff;
  border-radius: 999px;
  padding: 2px 12px;
  font: inherit;
  font-size: 12px;
  margin-left: 6px;
  cursor: pointer;
}

.an-days.active {
  background: var(--accent, #2f6b4f);
  border-color: var(--accent, #2f6b4f);
  color: #fff;
}

.an-note {
  font-size: 12px;
  color: var(--meta-quiet, #8b8577);
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
