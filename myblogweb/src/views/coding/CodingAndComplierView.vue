<template>
    <div class="container text-center">
        <div class="row">
            <div class="col-3">
                <ContentField>
                    你的代码侧栏
                </ContentField>
            </div>
            <div class="col-9">
                <ContentField>
                    写写代码吧！
                </ContentField>
            </div>
        </div>
    </div>
</template>

<script>
import ContentField from '@/components/ContentField.vue'
export default {
    components: {
        ContentField
    }
}
</script>

<style scoped></style>