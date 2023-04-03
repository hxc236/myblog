import { createRouter, createWebHistory } from 'vue-router'
import store from '@/store'

import HomePageView from '@/views/home/HomePageView'
import NewsAndPostView from '@/views/news/NewsAndPostView'
import ChatRoomView from '@/views/chat/ChatRoomView'
import CodingAndComplierView from '@/views/coding/CodingAndComplierView'
import UserAccountLoginView from '@/views/user/account/UserAccountLoginView'
import UserAccountRegisterView from '@/views/user/account/UserAccountRegisterView'
import NotFoundView from '@/views/error/NotFoundView'

const routes = [
  {
    path: "/",
    name: "home",
    redirect: "/home/",
  },
  {
    path: "/home/",
    name: "home_index",
    component: HomePageView,
    meta: {
      requestAuth: false,
    }
  },
  {
    path: "/news/",
    name: "news_index",
    component: NewsAndPostView,
    meta: {
      requestAuth: false,
    }
  },
  {
    path: "/chatroom/",
    name: "chatroom_index",
    component: ChatRoomView,
    meta: {
      requestAuth: false,
    }
  },
  {
    path: "/coding/",
    name: "coding_index",
    component: CodingAndComplierView,
    meta: {
      requestAuth: false,
    }
  },
  {
    path: "/account/login/",
    name: "login_index",
    component: UserAccountLoginView,
    meta: {
      requestAuth: false,
    }
  },
  {
    path: "/account/register/",
    name: "register_index",
    component: UserAccountRegisterView,
    meta: {
      requestAuth: false,
    }
  },
  {
    path: "/404/",
    name: "404",
    component: NotFoundView,
    meta: {
      requestAuth: false,
    }
  },
  {
    path: "/:catchAll(.*)",
    redirect: "/404/"
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => { //to 表示跳转到哪个页面，from表示从哪个页面跳转来的，next将页面要不要执行下一步操作
  if (to.meta.requestAuth && !store.state.user.is_login) {
    next({ name: "login_index" });
  } else {
    next();
  }
})

export default router
