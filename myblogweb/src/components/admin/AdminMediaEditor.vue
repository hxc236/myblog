<template>
  <div class="media-editor">
    <div class="me-head">
      <p v-if="notice" class="admin-notice" role="status">{{ notice }}</p>
      <p v-if="error" class="admin-error" role="alert">{{ error }}</p>
    </div>

    <form class="me-upload" @submit.prevent="upload">
      <input ref="fileInput" type="file" accept="image/jpeg,image/png,image/webp" @change="pickFile" />
      <input v-model="altText" class="me-alt" maxlength="200" placeholder="替代文本（可选）" />
      <button class="btn btn-primary" :disabled="!file || uploading" type="submit">
        {{ uploading ? '正在上传…' : '上传图片' }}
      </button>
    </form>
    <p class="me-hint">只接受 JPEG / PNG / WebP，不超过 5 MB 与 4096×4096。</p>

    <p v-if="!assets" class="admin-hint">正在加载媒体库…</p>
    <p v-else-if="assets.length === 0" class="admin-hint">还没有媒体资源，上传第一张图片吧。</p>

    <ul v-else class="media-grid">
      <li v-for="asset in assets" :key="asset.id" class="media-card">
        <img class="media-thumb" :src="asset.publicUrl" :alt="asset.altText || asset.fileName" />
        <div class="media-info">
          <p class="media-name" :title="asset.fileName">{{ asset.fileName }}</p>
          <p class="media-meta">
            {{ asset.width }}×{{ asset.height }} ·
            {{ formatSize(asset.sizeBytes) }} ·
            {{ asset.mimeType }}
          </p>
          <p class="media-ref" :class="asset.referenced ? 'ref-yes' : 'ref-no'">
            {{ asset.referenced ? '已被文章引用' : '未被引用（可删除）' }}
          </p>
          <code class="media-url">{{ asset.publicUrl }}</code>
        </div>
        <div class="media-actions">
          <button
            class="btn btn-secondary btn-sm"
            :disabled="asset.referenced"
            :title="asset.referenced ? '被引用的资源不能删除' : ''"
            @click="remove(asset)"
          >删除</button>
        </div>
      </li>
    </ul>
  </div>
</template>

<script>
import { onMounted, ref } from 'vue'
import { deleteMedia, fetchMedia, uploadMedia } from '@/api/admin'

/**
 * 媒体库（#24）：上传 JPEG/PNG/WebP、引用状态标记、未引用资源手动清理；
 * 被 Draft 或 Published Revision 引用的资源不可删除。
 */
export default {
  name: 'AdminMediaEditor',
  setup() {
    const assets = ref(null)
    const file = ref(null)
    const altText = ref('')
    const fileInput = ref(null)
    const notice = ref('')
    const error = ref('')
    const uploading = ref(false)

    onMounted(load)

    async function load() {
      assets.value = await fetchMedia()
    }

    function pickFile(event) {
      file.value = event.target.files && event.target.files[0]
      if (file.value && !/^image\/(jpeg|png|webp)$/.test(file.value.type)) {
        error.value = '只接受 JPEG、PNG 或 WebP 图片。'
        file.value = null
        event.target.value = ''
      }
    }

    async function upload() {
      if (!file.value) return
      uploading.value = true
      error.value = ''
      notice.value = ''
      try {
        const result = await uploadMedia(file.value, altText.value)
        if (!result.ok) {
          error.value = result.message
          return
        }
        notice.value = '已上传。'
        altText.value = ''
        file.value = null
        if (fileInput.value) fileInput.value.value = ''
        await load()
      } catch (e) {
        error.value = '无法连接管理服务，请稍后重试。'
      } finally {
        uploading.value = false
      }
    }

    async function remove(asset) {
      if (!window.confirm(`确认删除「${asset.fileName}」？`)) {
        return
      }
      error.value = ''
      notice.value = ''
      try {
        const result = await deleteMedia(asset.id)
        if (!result.ok) {
          error.value = result.message
          return
        }
        notice.value = '已删除。'
        await load()
      } catch (e) {
        error.value = '无法连接管理服务，请稍后重试。'
      }
    }

    function formatSize(bytes) {
      if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
      if (bytes >= 1024) return `${Math.round(bytes / 1024)} KB`
      return `${bytes} B`
    }

    return { assets, file, altText, fileInput, notice, error, uploading, pickFile, upload, remove, formatSize }
  },
}
</script>

<style scoped>
.me-head {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.me-upload {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 6px;
}

.me-alt {
  flex: 1;
  min-width: 200px;
  padding: 8px 10px;
  border: 1px solid var(--line, #e6e0d4);
  border-radius: 6px;
  font: inherit;
}

.me-hint {
  font-size: 13px;
  color: var(--meta-quiet, #8b8577);
  margin: 0 0 18px;
}

.media-grid {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 14px;
}

.media-card {
  border: 1px solid var(--line, #e6e0d4);
  border-radius: 10px;
  padding: 12px;
  background: var(--surface, #fffdf8);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.media-thumb {
  width: 100%;
  height: 140px;
  object-fit: cover;
  border-radius: 6px;
  background: var(--chip-bg, #f3efe6);
}

.media-info {
  font-size: 13px;
}

.media-name {
  font-weight: 600;
  margin: 0 0 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.media-meta {
  color: var(--meta-quiet, #8b8577);
  margin: 0 0 4px;
}

.media-ref {
  margin: 0 0 6px;
  font-size: 12px;
}

.ref-yes {
  color: var(--accent, #2f6b4f);
}

.ref-no {
  color: var(--meta-quiet, #8b8577);
}

.media-url {
  display: block;
  font-size: 11px;
  color: var(--meta-quiet, #8b8577);
  word-break: break-all;
  background: var(--chip-bg, #f3efe6);
  border-radius: 4px;
  padding: 3px 6px;
}

.media-actions {
  margin-top: auto;
}

.btn-sm {
  padding: 4px 12px;
  font-size: 13px;
}

.admin-notice {
  color: var(--accent, #2f6b4f);
  font-size: 14px;
  margin: 0;
}

.admin-error {
  color: #a63d2f;
  font-size: 14px;
  margin: 0;
}

.admin-hint {
  color: var(--body-muted, #5d584c);
  font-size: 14px;
}
</style>
