<template>
    <ContentField class="col-8">
        <div class="row justify-content-md-center">
            <div class="col-3">
                <form  @submit.prevent="login">
                    <div class="mb-3">
                        <label for="username" class="form-label">用户名：</label>
                        <input type="text" class="form-control" id="username" placeholder="请输入用户名">
                    </div>
                    <div class="mb-3">
                        <label for="password" class="form-label">密码：</label>
                        <input type="password" class="form-control" id="password" placeholder="请输入密码">
                    </div>
                    <div v-if="error" class="error">{{ error }}</div>
                    <div v-else> <br /> </div>
                    <button type="submit" class="btn btn-success">登录</button>
                </form>
            </div>
        </div>
    </ContentField>
</template>

  
<script>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import ContentField from '@/components/ContentField.vue'


export default {
    components: {
        ContentField,
    },
    setup() {
        const router = useRouter();
        const username = ref('');
        const password = ref('');
        const error = ref(null);

        const login = async () => {
            
            try {
                // 通过 API 进行登录验证
                const response = await fetch('/api/login', {
                    method: 'POST',
                    body: JSON.stringify({ username: username.value, password: password.value }),
                    headers: { 'Content-Type': 'application/json' }
                });
                const data = await response.json();
                if (response.ok) {
                    // 登录成功，跳转到主页
                    router.push('/');
                } else {
                    // 登录失败，显示错误信息
                    error.value = data.message;
                }
            } catch (err) {
                console.error(err);
                error.value = '登录失败，请稍后重试。';
            }
        };

        return {
            username,
            password,
            error,
            login
        };
    }
};
</script>

  
<style scoped>
.btn {
    margin-right: auto;
}

.error {
    margin-top: 1rem;
    color: red;
    font-weight: bold;
}
</style>