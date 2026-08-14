<template>
  <div class="page home">
    <!-- 1. 公开介绍（Hero） -->
    <section id="intro" class="hero" aria-label="公开介绍">
      <div class="hero-copy">
        <p v-if="intro" class="eyebrow">{{ intro.eyebrow }}</p>
        <p v-else class="skeleton eyebrow-skeleton" aria-hidden="true"></p>

        <h1 v-if="intro" class="hero-title">{{ intro.headline }}</h1>
        <h1 v-else class="skeleton title-skeleton" aria-hidden="true">加载中</h1>

        <p v-if="intro" class="hero-intro">{{ intro.introduction }}</p>
        <p v-else class="skeleton intro-skeleton" aria-hidden="true"></p>

        <div class="hero-actions">
          <a href="#work" class="btn btn-primary" @click.prevent="scrollTo('work')">查看精选作品</a>
          <a
            v-if="intro"
            class="btn btn-secondary"
            :href="`mailto:${intro.email}`"
          >给我发邮件</a>
          <span v-else class="skeleton button-skeleton" aria-hidden="true"></span>
        </div>

        <a
          v-if="intro"
          class="hero-github"
          :href="intro.githubUrl"
          target="_blank"
          rel="noopener noreferrer"
        >在 GitHub 查看更多公开代码 →</a>
      </div>

      <SkillPanel :groups="intro ? intro.skillGroups : []" />
    </section>

    <!-- 2. 作品展示 -->
    <section id="work" class="work" aria-label="精选作品">
      <h2 class="section-title">不只展示代码，也讲清楚它解决了什么</h2>
      <p v-if="projects && projects.length" class="section-note">
        三个精选作品，按编号、成果、职责、技术栈与年份归档。
      </p>

      <div v-if="projects && projects.length" class="project-list">
        <ProjectRow v-for="(project, i) in projects" :key="project.key" :project="project" :index="i" />
      </div>
      <div v-else-if="phase !== 'error'" class="project-list" aria-hidden="true">
        <div v-for="i in 3" :key="i" class="skeleton project-skeleton"></div>
      </div>
    </section>

    <!-- 3. 博客 -->
    <section id="writing" class="writing" aria-label="博客">
      <h2 class="section-title">近期博客</h2>
      <div v-if="posts && posts.length" class="post-list">
        <PostRow v-for="post in posts" :key="post.slug" :post="post" />
      </div>
      <div v-else-if="phase !== 'error'" class="post-list" aria-hidden="true">
        <div v-for="i in 2" :key="i" class="skeleton post-skeleton"></div>
      </div>
      <router-link v-if="posts && posts.length" class="all-posts" to="/blog">查看全部博客 →</router-link>
    </section>

    <LoadStateNotice :phase="phase" @retry="loadAll" />
  </div>
</template>

<script>
import { onMounted, ref } from 'vue'
import { loadJson } from '@/api'
import { usePhaseNotice } from '@/composables/usePhaseNotice'
import SkillPanel from '@/components/SkillPanel.vue'
import ProjectRow from '@/components/ProjectRow.vue'
import PostRow from '@/components/PostRow.vue'
import LoadStateNotice from '@/components/LoadStateNotice.vue'

export default {
  name: 'HomePageView',
  components: { SkillPanel, ProjectRow, PostRow, LoadStateNotice },
  setup() {
    const intro = ref(null)
    const projects = ref(null)
    const posts = ref(null)
    const { phase, onPhase } = usePhaseNotice()

    function scrollTo(id) {
      document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }

    async function loadAll() {
      phase.value = 'loading'
      const [introResult, projectsResult, postsResult] = await Promise.allSettled([
        loadJson('/api/v1/introduction', { onPhase }),
        loadJson('/api/v1/projects', { onPhase }),
        loadJson('/api/v1/posts', { onPhase }),
      ])
      if (introResult.status === 'fulfilled') intro.value = introResult.value
      if (projectsResult.status === 'fulfilled') projects.value = projectsResult.value
      if (postsResult.status === 'fulfilled') {
        // 首页最多展示三篇近期博客
        posts.value = postsResult.value.slice(0, 3)
      }
      // 任一资源在 75 秒窗口内失败 → 手动重试；全部成功 → 无需提示
      phase.value =
        introResult.status === 'rejected' ||
        projectsResult.status === 'rejected' ||
        postsResult.status === 'rejected'
          ? 'error'
          : 'ready'
    }

    onMounted(loadAll)

    return { intro, projects, posts, phase, scrollTo, loadAll }
  },
}
</script>

<style scoped>
.home {
  padding-top: 155px;
}

/* ---- Hero ---- */
.hero {
  display: grid;
  grid-template-columns: 1.35fr 1fr;
  gap: 56px;
  align-items: start;
  padding-bottom: 40px;
}

.eyebrow {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.12em;
  color: var(--forest);
  margin: 0 0 16px;
}

.hero-title {
  font-size: clamp(54px, 6.4vw, 92px);
  margin: 0 0 20px;
  letter-spacing: 0.01em;
}

.hero-intro {
  max-width: 540px;
  color: var(--body-muted);
  margin: 0 0 28px;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-bottom: 20px;
}

.hero-github {
  display: inline-block;
  font-size: 15px;
  font-weight: 600;
}

/* ---- 作品 ---- */
.work {
  padding-top: 40px;
}

.section-note {
  color: var(--body-muted);
  margin: 0 0 18px;
}

.project-list {
  border-bottom: 1px solid var(--rule);
}

/* ---- 博客 ---- */
.writing {
  padding-top: 48px;
}

.post-list {
  border-bottom: 1px solid var(--rule);
}

.all-posts {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  margin-top: 18px;
  font-weight: 600;
}

/* ---- 骨架 ---- */
.eyebrow-skeleton {
  width: 180px;
  height: 18px;
  margin-bottom: 16px;
}

.title-skeleton {
  width: 70%;
  height: 72px;
  margin-bottom: 20px;
}

.intro-skeleton {
  width: 90%;
  height: 72px;
  margin-bottom: 28px;
}

.button-skeleton {
  width: 130px;
  height: 44px;
}

.project-skeleton {
  height: 120px;
  margin: 20px 0;
}

.post-skeleton {
  height: 64px;
  margin: 18px 0;
}

/* 900px 以下单栏：先文案与行动，再技能面板 */
@media (max-width: 900px) {
  .home {
    padding-top: 120px;
  }

  .hero {
    grid-template-columns: 1fr;
    gap: 32px;
  }

  .hero-title {
    font-size: clamp(42px, 8vw, 54px);
  }
}
</style>
