<template>
  <header class="site-nav" role="banner">
    <nav class="nav-inner" aria-label="主导航">
      <router-link to="/" class="nav-brand" aria-label="回到首页">hxc236</router-link>

      <div class="nav-links">
        <a href="#intro" class="nav-link" @click.prevent="goToSection('intro')">介绍</a>
        <a href="#work" class="nav-link" @click.prevent="goToSection('work')">作品</a>
        <a href="#writing" class="nav-link" @click.prevent="goToBlog">博客</a>
      </div>

      <div class="nav-actions">
        <a
          class="nav-github"
          href="https://github.com/hxc236"
          target="_blank"
          rel="noopener noreferrer"
          aria-label="GitHub（新窗口打开）"
        >
          <span class="github-label">GitHub</span>
          <svg class="github-icon" viewBox="0 0 16 16" width="18" height="18" aria-hidden="true" focusable="false">
            <path fill="currentColor" d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27s1.36.09 2 .27c1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8Z" />
          </svg>
        </a>
        <a href="#contact" class="btn btn-primary nav-contact" @click.prevent="goToSection('contact')">联系我</a>
      </div>
    </nav>
  </header>
</template>

<script>
import { useRoute, useRouter } from 'vue-router'

export default {
  name: 'SiteNav',
  setup() {
    const route = useRoute()
    const router = useRouter()

    function scrollToId(id) {
      const el = document.getElementById(id)
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'start' })
      }
    }

    function goToSection(id) {
      if (route.path === '/') {
        scrollToId(id)
      } else {
        router.push('/').then(() => {
          requestAnimationFrame(() => scrollToId(id))
        })
      }
    }

    function goToBlog() {
      if (route.path === '/') {
        scrollToId('writing')
      } else {
        router.push('/blog')
      }
    }

    return { goToSection, goToBlog }
  },
}
</script>

<style scoped>
.site-nav {
  position: fixed;
  top: 16px;
  left: 0;
  right: 0;
  z-index: 100;
  display: flex;
  justify-content: center;
  padding: 0 20px;
}

.nav-inner {
  width: 100%;
  max-width: var(--content-max);
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 8px 12px 8px 20px;
  border-radius: var(--radius-nav);
  background: rgba(251, 249, 243, 0.82);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid var(--rule);
}

.nav-brand {
  font-family: var(--font-serif);
  font-size: 20px;
  font-weight: 700;
  color: var(--ink);
}

.nav-links {
  display: flex;
  gap: 4px;
  margin-left: auto;
}

.nav-link {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  padding: 0 14px;
  border-radius: 999px;
  color: var(--ink);
  font-size: 15px;
  font-weight: 500;
}

.nav-link:hover {
  background: rgba(23, 33, 27, 0.06);
  color: var(--forest);
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.nav-github {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  padding: 0 14px;
  border-radius: 999px;
  color: var(--ink);
  font-size: 15px;
  font-weight: 500;
}

.nav-github:hover {
  background: rgba(23, 33, 27, 0.06);
  color: var(--forest);
}

.nav-contact {
  padding: 6px 20px;
  font-size: 15px;
}

/* 720px 以下隐藏中间锚点，保留站点标识、GitHub 与联系入口 */
@media (max-width: 720px) {
  .nav-links {
    display: none;
  }
  .nav-inner {
    gap: 12px;
  }
  .github-label {
    display: none;
  }
  .nav-github {
    padding: 0 10px;
  }
}
</style>
