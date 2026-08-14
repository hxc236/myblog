<template>
  <div class="markdown-body" v-html="safeHtml"></div>
</template>

<script>
import { computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

// Markdown 支持：标题、列表、链接、引用、行内代码、围栏代码块；禁用原始 HTML。
marked.setOptions({
  gfm: true,
  breaks: false,
})

// 外部链接安全行为：新窗口打开并注明目标；清除任何危险属性。
DOMPurify.addHook('afterSanitizeAttributes', (node) => {
  if (node.tagName === 'A') {
    const href = node.getAttribute('href') || ''
    if (/^https?:\/\//.test(href)) {
      node.setAttribute('target', '_blank')
      node.setAttribute('rel', 'noopener noreferrer')
      if (!node.getAttribute('aria-label')) {
        node.setAttribute('aria-label', `${node.textContent}（新窗口打开）`)
      }
    }
  }
})

export default {
  name: 'MarkdownView',
  props: {
    markdown: { type: String, required: true },
  },
  setup(props) {
    const safeHtml = computed(() => {
      const rawHtml = marked.parse(props.markdown || '')
      return DOMPurify.sanitize(rawHtml)
    })
    return { safeHtml }
  },
}
</script>

<style scoped>
.markdown-body {
  font-family: var(--font-serif);
  font-size: 18px;
  line-height: 1.8;
  color: var(--ink);
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  font-family: var(--font-serif);
  line-height: 1.3;
  margin: 1.6em 0 0.6em;
}

.markdown-body :deep(h1) {
  font-size: 30px;
}

.markdown-body :deep(h2) {
  font-size: 26px;
}

.markdown-body :deep(h3) {
  font-size: 22px;
}

.markdown-body :deep(p) {
  margin: 0 0 1.1em;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 1.4em;
  margin: 0 0 1.1em;
}

.markdown-body :deep(blockquote) {
  margin: 0 0 1.1em;
  padding: 0.2em 1.1em;
  border-left: 3px solid var(--forest);
  color: var(--body-muted);
  background: rgba(50, 106, 74, 0.05);
}

.markdown-body :deep(code) {
  font-family: ui-monospace, SFMono-Regular, Consolas, "Liberation Mono", monospace;
  font-size: 0.88em;
  background: rgba(23, 33, 27, 0.07);
  border-radius: 6px;
  padding: 0.15em 0.4em;
}

.markdown-body :deep(pre) {
  background: var(--ink);
  color: var(--paper);
  border-radius: var(--radius-small);
  padding: 18px 20px;
  overflow-x: auto;
  margin: 0 0 1.2em;
}

.markdown-body :deep(pre code) {
  background: transparent;
  color: inherit;
  padding: 0;
  font-size: 0.86em;
  line-height: 1.6;
}

.markdown-body :deep(a) {
  color: var(--forest);
  text-decoration: underline;
  text-underline-offset: 3px;
}
</style>
