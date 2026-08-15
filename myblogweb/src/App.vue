<template>
  <SiteNav v-if="!isAdminArea" />
  <main id="main">
    <router-view />
  </main>
  <ContactSection v-if="!isAdminArea" :intro="intro" :loading="loading" />
</template>

<script>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import SiteNav from '@/components/SiteNav.vue'
import ContactSection from '@/components/ContactSection.vue'
import { sharedLoad } from '@/api'

export default {
  name: 'App',
  components: { SiteNav, ContactSection },
  setup() {
    const route = useRoute()
    // Admin 区域不显示公开导航与联系页脚（#16）
    const isAdminArea = computed(() => route.path.startsWith('/admin'))

    // 联系区（#contact）由全局页脚提供；介绍内容冷启动失败时保留 GitHub 静态入口
    const intro = ref(null)
    const loading = ref(true)

    onMounted(async () => {
      if (isAdminArea.value) {
        loading.value = false
        return
      }
      try {
        // 正式领域语义 API（#29）：介绍与联系方式分开读取后合并供页脚使用
        const [introduction, contact] = await Promise.all([
          sharedLoad('/api/site/introduction'),
          sharedLoad('/api/site/contact'),
        ])
        intro.value = { ...introduction, ...contact }
      } catch (e) {
        intro.value = null
      } finally {
        loading.value = false
      }
    })

    return { isAdminArea, intro, loading }
  },
}
</script>
