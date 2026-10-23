import { createRouter, createWebHistory } from 'vue-router'
import {unauthorized} from "@/net";


const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'welcome',
      component: ()=>import('@/views/WelcomeView.vue'),
      children:[
        {
          path: 'register',
          name: 'welcome-register',
          component: ()=>import('@/components/welcome/RegisterPage.vue')
        },{
          path: '',
          name: 'welcome-login',
          component: ()=>import('@/components/welcome/LoginPage.vue')
        },{
          path: 'forget',
          name: 'welcome-forget',
          component: ()=>import('@/components/welcome/ForgetPage.vue')
        }
      ]
    },{
      path: '/index',
      name: 'index',
      component: ()=>import('@/views/IndexView.vue'),
      children: [
        {
          path: '',
          name: 'topics',
          component: ()=> import('@/views/forum/Forum.vue'),
          children: [{
            path: '',
            name: 'topic-list',
            component: () =>import('@/views/forum/TopicList.vue')
          }, {
            path: 'topic-detail/:tid',
            name: 'topic-detail',
            component: () =>import('@/views/forum/TopicDetail.vue')
          }]
        }, {
          path: 'user-setting',
          name: 'user-setting',
          component: () =>import('@/views/settings/UserSetting.vue')
        },
        {
          path: 'privacy-setting',
          name: 'privacy-setting',
          component: () =>import('@/views/settings/PrivacySetting.vue')
        }
      ]
    }
  ]
})

router.beforeEach((to, from, next) => {
  const isUnauthorized = unauthorized()
  if (to.name.startsWith('welcome-') && !isUnauthorized){
    next('/index')
  }else if (to.fullPath.startsWith('/index')&&isUnauthorized){
    next('/')
  }else {
    next()
  }
})

export default router
