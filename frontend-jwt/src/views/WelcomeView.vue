<script setup>

</script>

<template>
  <div style="=width: 100vw;height: 100vh;overflow: hidden;display: flex">
    <div style="flex: 1">
      <el-image fit="cover" style="width: 100%;height: 100%"
                src="https://img1.baidu.com/it/u=3316152189,1021450819&fm=253&fmt=auto&app=120&f=JPEG?w=1422&h=800"/>
    </div>
    <div class="welcome-title">
      <div style="font-size: 30px;font-weight: bold">系统登录页</div>
      <div style="margin-top: 5px">by. Ziyuan</div>
    </div>
    <div class="right-card">
      <router-view v-slot="{ Component }">
        <transition mode="out-in" name="el-fade-in-linear" >
          <component :is="Component"/>
        </transition>
      </router-view>
    </div>
  </div>
    <div>
        <p style="font-size: 14px;
            background:none;
            height: 5%;
            position: fixed;
            bottom: 0;
            text-align: center;
            width: 100%">
            <a style="color: grey; text-decoration: none; z-index: 999;"
               href="https://beian.miit.gov.cn/"
               target="_blank">吉ICP备2024014716号</a>
        </p>
    </div>
</template>

<style scoped>
.welcome-title{
  position: absolute;
  bottom: 30px;
  left: 30px;
  color: white;
  text-shadow: 0 0 10px black;
}
.right-card{
  width: 400px;
  background-color: var(--el-bg-color);
  z-index: 1
}
</style>
