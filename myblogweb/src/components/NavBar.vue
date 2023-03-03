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

                <ul class="navbar-nav">
                    <li class="nav-item">
                        <router-link class="nav-link" active-class="active" aria-current="page"
                            :to="{ name: 'login_index' }">登录</router-link>
                    </li>

                    <li class="nav-item">
                        <router-link class="nav-link" active-class="active" aria-current="page"
                            :to="{ name: 'register_index' }">注册</router-link>
                    </li>
                </ul>

                <ul class="navbar-nav">
                    <li class="nav-item dropdown">
                        <a class="nav-link dropdown-toggle" href="#" role="button" data-bs-toggle="dropdown"
                            aria-expanded="false">
                            我的
                        </a>
                        <ul class="dropdown-menu">
                            <li><a class="dropdown-item" href="#">Action</a></li>
                        </ul>
                    </li>
                </ul>

            </div>
        </div>
    </nav>
</template>
  
<script>

</script>
  
<style scoped></style>