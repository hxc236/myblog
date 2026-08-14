<template>
  <div class="page not-found">
    <p class="nf-code" aria-hidden="true">404</p>
    <h1 class="nf-title">页面不存在</h1>
    <p class="nf-text">你访问的地址不存在或已被移动。</p>
    <div class="nf-actions">
      <router-link class="btn btn-primary" to="/">返回首页</router-link>
      <router-link class="btn btn-secondary" to="/blog">查看博客</router-link>
    </div>
  </div>
</template>

<script>
export default {
  name: 'NotFoundView',
}
</script>

<style scoped>
.not-found {
  padding-top: 170px;
  padding-bottom: 60px;
  text-align: center;
  max-width: 560px;
}

.nf-code {
  font-family: var(--font-serif);
  font-size: 96px;
  color: var(--meta-quiet);
  margin: 0 0 8px;
  line-height: 1;
}

.nf-title {
  font-size: 30px;
  margin: 0 0 10px;
}

.nf-text {
  color: var(--body-muted);
  margin: 0 0 28px;
}

.nf-actions {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 12px;
}
</style>
