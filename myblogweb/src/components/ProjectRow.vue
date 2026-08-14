<template>
  <article class="project-row">
    <div class="row-number" aria-hidden="true">{{ String(index + 1).padStart(2, '0') }}</div>

    <div class="row-main">
      <h3 class="row-title">{{ project.title }}</h3>
      <p class="row-summary">{{ project.summary }}</p>
      <dl class="row-meta">
        <div class="meta-item">
          <dt>职责</dt>
          <dd>{{ project.role }}</dd>
        </div>
        <div class="meta-item">
          <dt>技术栈</dt>
          <dd>{{ project.stack.join(' · ') }}</dd>
        </div>
        <div class="meta-item">
          <dt>年份</dt>
          <dd>{{ project.year }}</dd>
        </div>
      </dl>
    </div>

    <div class="row-actions">
      <a
        v-if="project.repositoryUrl"
        class="action-link"
        :href="project.repositoryUrl"
        target="_blank"
        rel="noopener noreferrer"
      >代码仓库 →</a>
      <a
        v-if="project.demoUrl"
        class="action-link"
        :href="project.demoUrl"
        target="_blank"
        rel="noopener noreferrer"
      >在线演示 →</a>
    </div>
  </article>
</template>

<script>
export default {
  name: 'ProjectRow',
  props: {
    project: { type: Object, required: true },
    index: { type: Number, required: true },
  },
}
</script>

<style scoped>
.project-row {
  display: grid;
  grid-template-columns: 72px 1fr auto;
  gap: 24px;
  align-items: start;
  padding: 26px 8px;
  border-top: 1px solid var(--rule);
  transition: background-color var(--transition);
}

.project-row:hover {
  background: rgba(251, 249, 243, 0.6);
}

.row-number {
  font-family: var(--font-serif);
  font-size: 20px;
  color: var(--meta-quiet);
  padding-top: 4px;
}

.row-title {
  font-size: 22px;
  margin: 0 0 6px;
}

.row-summary {
  margin: 0 0 14px;
  color: var(--ink);
}

.row-meta {
  margin: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 10px 28px;
  font-size: 14px;
  color: var(--body-muted);
}

.meta-item {
  display: flex;
  gap: 6px;
  margin: 0;
}

.meta-item dt {
  font-weight: 600;
  color: var(--meta-quiet);
}

.meta-item dd {
  margin: 0;
}

.row-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-top: 4px;
}

.action-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 44px;
  padding: 6px 18px;
  border: 1px solid var(--rule);
  border-radius: 999px;
  color: var(--forest);
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
  transition: border-color var(--transition), background-color var(--transition);
}

.action-link:hover {
  border-color: var(--forest);
  background: rgba(50, 106, 74, 0.06);
  color: var(--forest);
}

/* 900px 以下堆叠：职责与技术栈移到标题下方（本就可见，不被隐藏） */
@media (max-width: 900px) {
  .project-row {
    grid-template-columns: 1fr;
    gap: 10px;
    padding: 24px 4px;
  }

  .row-number {
    font-size: 15px;
  }

  .row-actions {
    flex-direction: row;
    flex-wrap: wrap;
    padding-top: 4px;
  }
}
</style>
