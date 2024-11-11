<template>
    <div class="container text-center">
        <div class="row">
            <div class="col-9">
                <ContentField>
                    <div>
                        <div class="card" style="width: 90%; margin-left: 5%;">
                            <div class="card-body">
                                <h3 class="card-title">计划 PLANS</h3>

                                <ul class="list-group">
                                    <li class="list-group-item">
                                        <input class="form-check-input me-1" type="checkbox" value=""
                                            id="firstCheckboxStretched">
                                        <label class="form-check-label stretched-link"
                                            for="firstCheckboxStretched">First checkbox</label>
                                    </li>
                                    <li class="list-group-item">
                                        <input class="form-check-input me-1" type="checkbox" value=""
                                            id="secondCheckboxStretched">
                                        <label class="form-check-label stretched-link"
                                            for="secondCheckboxStretched">Second checkbox</label>
                                    </li>
                                    <li class="list-group-item">
                                        <input class="form-check-input me-1" type="checkbox" value=""
                                            id="thirdCheckboxStretched">
                                        <label class="form-check-label stretched-link"
                                            for="thirdCheckboxStretched">Third checkbox</label>
                                    </li>
                                </ul>
                            </div>
                        </div>
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