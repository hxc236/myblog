<template>
    <nav class="navbar navbar-expand-lg navbar-light bg-light fixed-top">
        <div class="container">
            <router-link class="navbar-brand" :to="{ name: 'home' }">导航</router-link>
            <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#myBlogNavbar"
                aria-controls="myBlogNavbar" aria-expanded="false" aria-label="Toggle navigation">
                <span class="navbar-toggler-icon"></span>
            </button>

            <div class="collapse navbar-collapse" id="myBlogNavbar">

                <ul class="navbar-nav me-auto mb-2 mb-lg-0">
                    <li class="nav-item">
                        <router-link class="nav-link" active-class="active" aria-current="page"
                            :to="{ name: 'news_index' }">新鲜事</router-link>
                    </li>
                    <li class="nav-item">
                        <router-link class="nav-link" active-class="active" aria-current="page"
                            :to="{ name: 'chatroom_index' }">聊天室</router-link>
                    </li>
                    <li class="nav-item">
                        <router-link class="nav-link" active-class="active" aria-current="page"
                            :to="{ name: 'coding_index' }">写写代码吧！</router-link>
                    </li>
                </ul>

                <ul class="navbar-nav" v-if="$store.state.user.is_login">
                    <li class="nav-item dropdown">
                        <a class="nav-link dropdown-toggle" href="#" role="button" data-bs-toggle="dropdown"
                            aria-expanded="false">
                            {{ $store.state.user.username }} 
                            <!-- <img src="$store.state.user.photo" class="full " alt=""> -->
                            <!-- <img :src="$store.state.user.photo" alt="" class="rounded-circle full">    -->
                        </a>
                            
                        <ul class="dropdown-menu">
                            <li>
                                <router-link class="dropdown-item" :to="{ name: '404' }">我的信息</router-link>
                            </li>
                            <li>
                                <hr class="dropdown-divider">
                            </li>
                            <li><a class="dropdown-item" href="#" @click="logout">退出</a></li>
                        </ul>
                    </li>
                </ul>

                <ul class="navbar-nav" v-else-if="!$store.state.user.is_login">
                    <li class="nav-item">
                        <router-link class="nav-link" active-class="active" aria-current="page"
                            :to="{ name: 'login_index' }">登录</router-link>
                    </li>

                    <li class="nav-item">
                        <router-link class="nav-link" active-class="active" aria-current="page"
                            :to="{ name: 'register_index' }">注册</router-link>
                    </li>
                </ul>



            </div>
        </div>
    </nav>
</template>
  
<script>
import { useStore } from 'vuex';

export default {
    setup() {
        const store = useStore();
        const logout = () => {
            store.dispatch("logout");
        }

        return {
            logout,
        }
    }
}

</script>
  
<style scoped>
imgs.full {
    width: 5%;

}

div.top_dist {
    margin-top: 20px;
}
</style>