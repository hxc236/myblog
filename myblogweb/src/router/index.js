import { createRouter, createWebHistory } from 'vue-router'

import HomePageView from '@/views/home/HomePageView'
import BlogListView from '@/views/blog/BlogListView'
import BlogPostView from '@/views/blog/BlogPostView'
import NotFoundView from '@/views/error/NotFoundView'

// 生产首页与博客路由（#5「页面与路由」）。
// 旧 /home/、/news/、/chatroom/、/coding/、登录和注册入口不出现在生产导航，
// 统一重定向到新首页或博客列表，不激活旧功能。
const routes = [
  { path: '/', name: 'home', component: HomePageView },
  { path: '/blog', name: 'blog', component: BlogListView },
  { path: '/blog/:slug', name: 'blog-post', component: BlogPostView },
  { path: '/404', name: 'not-found', component: NotFoundView },

  // 旧入口重定向（不得激活旧功能）
  { path: '/home/', redirect: '/' },
  { path: '/news/', redirect: '/blog' },
  { path: '/chatroom/', redirect: '/' },
  { path: '/coding/', redirect: '/' },
  { path: '/account/login/', redirect: '/' },
  { path: '/account/register/', redirect: '/' },
  { path: '/404/', redirect: '/404' },

  // 未知路由：站内 404，保留 URL，提供返回路径
  { path: '/:catchAll(.*)', name: 'not-found-any', component: NotFoundView },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 从博客详情回到首页时始终滚动到顶部，避免残留锚点
router.afterEach((to) => {
  if (to.path === '/') {
    window.scrollTo(0, 0)
  }
})

export default router
