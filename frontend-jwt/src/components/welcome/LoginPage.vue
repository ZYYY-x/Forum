<script setup>
import {Lock, User} from "@element-plus/icons-vue";
import {ElMessage} from "element-plus";
import {login} from "@/net";
import {reactive} from "vue";
import router from "@/router";


const from=reactive({
  username: '',
  password: '',
  remember: false
})


function  nLogin(){
  if (!from.username ||!from.password){
    ElMessage.warning('请填写用户名和密码')
  }else {
    login(from.username, from.password, from.remember,()=>{
      router.push('/index')
    })
  }
}

</script>

<template>
  <div style="margin: 30px;text-align: center">
    <div style=" margin-top: 150px" >
      <div style="font-size: 25px;font-weight: bold">登录</div>
      <div style="font-size: 14px;color: grey">请输入用户名和密码</div>
    </div>
    <div style="margin-top: 30px">
      <el-input v-model="from.username" type="text" placeholder="用户名/邮箱">
        <template #prefix>
          <el-icon><User /></el-icon>
        </template>
      </el-input>
      <el-input v-model="from.password" type="password" style="margin-top: 10px" placeholder="密码">
        <template #prefix>
          <el-icon><Lock /></el-icon>
        </template>
      </el-input>
    </div>
    <el-row style="margin-top: 5px">
      <el-col :span="12" style="text-align: left">
        <el-checkbox  v-model="from.remember" label="记住我" size="large" />
      </el-col>
      <el-col :span="12" style="text-align: right">
        <el-link @click="router.push('/forget')">忘记密码?</el-link>
      </el-col>
    </el-row>


    <div style="margin-top: 30px">
      <el-button @click="nLogin()" style="width: 270px" type="success" plain>立即登录</el-button>
    </div>
    <el-divider>
      <span style="color: grey;font-size: 13px">没有账号</span>
    </el-divider>
    <div>
      <el-button @click="router.push('/register')" style="width: 270px" type="warning" plain>注册账号</el-button>
    </div>
  </div>
</template>

<style scoped>

</style>