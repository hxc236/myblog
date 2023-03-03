<template>
    <div class="row">
        <div class="col-8" style="padding-left: 5vh;">
            <ContentField>
                主页
            </ContentField>
        </div>
        <div class="col-3" style="padding-right: 5vh;">
            <ContentField>
                侧栏
            </ContentField>
        </div>
    </div>
</template>

<script>
import ContentField from '@/components/ContentField.vue'

export default {
    components: {
        ContentField,
    },
}
</script>

<style scoped></style>