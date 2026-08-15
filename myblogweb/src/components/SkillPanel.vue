<template>
  <aside class="skill-panel" aria-label="技能与技术栈">
    <h3 class="panel-title">能力 / 技术栈</h3>
    <div v-if="groups.length" class="groups">
      <div v-for="group in groups" :key="group.name" class="group">
        <h4 class="group-name">{{ group.name }}</h4>
        <ul class="skill-list">
          <li v-for="skill in group.skills" :key="skill" class="skill-tag">{{ skill }}</li>
        </ul>
      </div>
    </div>
    <div v-else class="skeleton panel-skeleton" aria-hidden="true"></div>
  </aside>
</template>

<script>
export default {
  name: 'SkillPanel',
  props: {
    groups: { type: Array, default: () => [] },
  },
}
</script>

<style scoped>
.skill-panel {
  background: var(--surface);
  border: 1px solid var(--rule);
  border-radius: var(--radius-panel);
  padding: 28px 26px;
}

.panel-title {
  font-family: var(--font-serif);
  font-size: 20px;
  margin: 0 0 18px;
}

.groups {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px 18px;
}

.group:last-child:nth-child(odd) {
  grid-column: 1 / -1;
}

.group-name {
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: var(--meta-quiet);
  margin: 0 0 8px;
}

.skill-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.skill-tag {
  font-size: 14px;
  color: var(--ink);
  background: rgba(50, 106, 74, 0.08);
  border-radius: 999px;
  padding: 4px 12px;
}

.panel-skeleton {
  height: 220px;
}

@media (max-width: 900px) {
  .groups {
    grid-template-columns: 1fr;
  }

  .group:last-child:nth-child(odd) {
    grid-column: auto;
  }
}
</style>
