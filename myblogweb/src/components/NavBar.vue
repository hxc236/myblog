<template>
    <nav class="navbar navbar-expand-lg navbar-dark bg-dark fixed-top">
        <div class="container">
            <a class="navbar-brand" href="#">我的博客</a>
            <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#MyBlogNavbar"
                aria-controls="MyBlogNavbar" aria-expanded="false" aria-label="Toggle navigation">
                <span class="navbar-toggler-icon"></span>
            </button>
            <div class="collapse navbar-collapse" id="MyBlogNavbar">
                <ul class="navbar-nav">
                    <li class="nav-item">
                        <a class="nav-link active" aria-current="page" href="#">新鲜事</a>
                    </li>
                    <li class="nav-item">
                        <a class="nav-link" href="#">聊天室</a>
                    </li>
                    <li class="nav-item">
                        <a class="nav-link" href="#">写写代码吧！</a>
                    </li>
                    <li class="nav-item dropdown">
                        <a class="nav-link dropdown-toggle" href="#" role="button" data-bs-toggle="dropdown"
                            aria-expanded="false">
                            我的
                        </a>
                        <ul class="dropdown-menu">
                            <li><a class="dropdown-item" href="#">Action</a></li>
                            <li><a class="dropdown-item" href="#">Another action</a></li>
                            <li><a class="dropdown-item" href="#">Something else here</a></li>
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