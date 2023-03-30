<template>
    <ContentField class="col-8">
        <div class="row justify-content-md-center">
            <div class="col-3">
                <form @submit.prevent="login">
                    <div class="mb-3">
                        <label for="username" class="form-label">用户名</label>
                        <input type="text" class="form-control" id="username" placeholder="请输入用户名">
                    </div>
                    <div class="mb-3">
                        <label for="password" class="form-label">密码</label>
                        <input type="password" class="form-control" id="password" placeholder="请输入密码">
                    </div>
                    <div class="error-message">{{ error_message }}</div>
                    <button type="submit" class="btn btn-primary">登录</button>
                </form>
            </div>
        </div>
    </ContentField>
</template>

  
<script>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import ContentField from '@/components/ContentField.vue'
import store from '@/store';


export default {
    components: {
        ContentField,
    },
    setup() {
        const router = useRouter();
        let username = ref("");
        let password = ref("");
        let error_message = ref("");

        //jwt授权验证
        // const jwt_token = localStorage.getItem("jwt_token");

        const login = () => {
            error_message = "";
            store.dispatch("login", {
                username: username.value,
                password: password.value,
                success() {
                    store.dispatch("getInfo", {
                        success() {
                            router.push({ name: "home" });
                        }
                    })
                },
                error() {
                    error_message.value = "用户名或密码错误";
                }
            })
        }

        return {
            username,
            password,
            error_message,
            login
        };
    }
};
</script>

  
<style scoped>
.btn {
    margin-right: auto;
}

div.error-message {
    margin-top: 1rem;
    color: red;
    font-weight: bold;
}
</style>