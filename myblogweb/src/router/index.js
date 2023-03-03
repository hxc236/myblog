import { createRouter, createWebHistory } from 'vue-router'

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
  },
  {
    path: "/news/",
    name: "news_index",
    component: NewsAndPostView,
  },
  {
    path: "/chatroom/",
    name: "chatroom_index",
    component: ChatRoomView,
  },
  {
    path: "/coding/",
    name: "coding_index",
    component: CodingAndComplierView,
  },
  {
    path: "/account/login/",
    name: "login_index",
    component: UserAccountLoginView,
  },
  {
    path: "/account/register/",
    name: "register_index",
    component: UserAccountRegisterView,
  },
  {
    path: "/404/",
    name: "404",
    component: NotFoundView,
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

export default router
