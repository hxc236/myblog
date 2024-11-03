<template>
    <div class="container text-center">
        <div class="row">
            <div class="col-9">
                <ContentField>
                    <div>
                        
                    </div>
                </ContentField>
            </div>
            <div class="col-3">
                <ContentField>
                    侧栏
                </ContentField>
            </div>
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