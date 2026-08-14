<template>
  <SiteNav />
  <main id="main">
    <router-view />
  </main>
  <ContactSection :intro="intro" :loading="loading" />
</template>

<script>
import { onMounted, ref } from 'vue'
import SiteNav from '@/components/SiteNav.vue'
import ContactSection from '@/components/ContactSection.vue'
import { sharedLoad } from '@/api'

export default {
  name: 'App',
  components: { SiteNav, ContactSection },
  setup() {
    // 联系区（#contact）由全局页脚提供；介绍内容冷启动失败时保留 GitHub 静态入口
    const intro = ref(null)
    const loading = ref(true)

    onMounted(async () => {
      try {
        intro.value = await sharedLoad('/api/v1/introduction')
      } catch (e) {
        intro.value = null
      } finally {
        loading.value = false
      }
    })

    return { intro, loading }
  },
}
</script>
