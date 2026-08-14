<template>
  <section id="contact" class="contact" aria-label="联系方式">
    <div class="page contact-inner">
      <h2 class="contact-title">保持联系</h2>
      <p class="contact-line">
        如果你对我的工作感兴趣，或者想交流技术问题，欢迎给我发邮件或查看 GitHub。
      </p>

      <div class="contact-actions">
        <a
          v-if="intro && intro.email"
          class="contact-link email"
          :href="`mailto:${intro.email}`"
        >{{ intro.email }}</a>
        <span v-else-if="loading" class="skeleton email-skeleton" aria-hidden="true"></span>
        <p v-else class="email-unavailable">邮箱链接暂时不可用，请稍后重试。</p>

        <a
          class="contact-link"
          :href="intro && intro.githubUrl ? intro.githubUrl : 'https://github.com/hxc236'"
          target="_blank"
          rel="noopener noreferrer"
        >GitHub（新窗口打开）</a>
      </div>

      <p class="contact-copyright">
        {{ intro && intro.copyright ? intro.copyright : '© 2026 hxc236' }}
      </p>
    </div>
  </section>
</template>

<script>
export default {
  name: 'ContactSection',
  props: {
    intro: { type: Object, default: null },
    loading: { type: Boolean, default: false },
  },
}
</script>

<style scoped>
.contact {
  background: var(--ink);
  color: var(--paper);
  padding: 64px 0 48px;
  margin-top: 72px;
}

.contact-title {
  font-size: clamp(24px, 3vw, 30px);
  margin: 0 0 12px;
}

.contact-line {
  color: rgba(251, 249, 243, 0.8);
  max-width: 560px;
}

.contact-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
  align-items: center;
  margin: 24px 0 32px;
}

.contact-link {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  padding: 6px 20px;
  border: 1px solid rgba(251, 249, 243, 0.35);
  border-radius: 999px;
  color: var(--paper);
  font-weight: 600;
  transition: border-color var(--transition), background-color var(--transition), color var(--transition);
}

.contact-link:hover {
  border-color: var(--signal);
  background: rgba(223, 255, 120, 0.1);
  color: var(--signal);
}

.email-skeleton {
  display: inline-block;
  width: 240px;
  height: 44px;
  background: rgba(251, 249, 243, 0.15);
}

.email-unavailable {
  margin: 0;
  font-size: 14px;
  color: rgba(251, 249, 243, 0.55);
}

.contact-copyright {
  font-size: 14px;
  color: rgba(251, 249, 243, 0.55);
  margin: 0;
}
</style>
