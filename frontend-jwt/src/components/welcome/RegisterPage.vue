<script setup>

import {HomeFilled, InfoFilled, Lock, Message, User} from "@element-plus/icons-vue";
import router from "@/router";
import {ref,reactive} from "vue";
import {ElMessage} from "element-plus";
import {post,get} from "@/net";
const form = reactive({
  username: '',
  password: '',
  password_repeat: '',
  email: '',
  code: ''
})

const validateUsername = (rule, value, callback) => {
  if (value === '') {
    callback(new Error('请输入用户名'))
  } else  if (!/^[a-zA-Z0-9\u4e00-\u9fa5]{1,20}$/.test(value)){
    callback(new Error('用户名不能包含特殊字符，只能输入中/英文'))
  }else {
    callback();
  }
}

const validatePassword = (rule, value, callback) => {
  if (value === '') {
    callback(new Error('请重新输入'))
  } else if (value !== form.password) {
    callback(new Error("两次输入的密码不一致!"))
  } else {
    callback()
  }
}




const rules={
  username:[
    { validator: validateUsername , trigger: ['blur' , 'change'] },
    { min: 2, max: 8, message: '用户名长度应该在2~8字符', trigger: ['blur' , 'change']},
  ],
  password: [
    { required: true, message: '请输入密码', trigger: ['blur' , 'change'] },
    { min: 6, max: 16, message: '密码应该在6~16字符', trigger: ['blur' , 'change']},
  ],
  password_repeat: [
    { validator:  validatePassword, trigger: ['blur' , 'change']},
  ],
  email: [
    { required: true, message: '请输入电子邮件地址', trigger: ['blur' , 'change'] },
    {
      type: 'email', message: '电子邮件地址不合法', trigger: ['blur', 'change'],
    }
  ],
  code: [
    { required: true, message: '请输入获取的验证码', trigger: ['blur'] },
  ]
}
const isEmailValid = ref(true)
const  coldTime =ref(0)
function askCode(){
  if (/^[\w-]+@[a-zA-Z\d-]+\.[a-zA-Z]{2,}$/.test(form.email)){
    isEmailValid.value= false
    coldTime.value = 60
    get(`/api/auth/ask-code?email=${form.email}&type=register`,()=>{
      ElMessage.success(`正在发送验证码${form.email}`)
      setInterval(()=>coldTime.value-- ,1000)
    },(message)=>{
      ElMessage.warning(message)
      coldTime.value = 0
    })
  }else {
    ElMessage.warning("电子邮件格式不正确")
  }

}



const formRef = ref()


const onValidate = (prop,isValid) => {
  if (prop === 'email')
    isEmailValid.value = !isValid
}

function register(){
  formRef.value.validate((isValid)=>{
    if (isValid){
      post('/api/auth/register',{
        ...form
      },()=>{
        ElMessage.success("注册成功")
        router.push('/')
      })
    }else {
      ElMessage.warning("请完整填写表单")
    }
  })
}




const validateEmail = () => {
  coldTime.value = 60

  post('/api/auth/valid-register-email', {
    email: form.email
  }, (message) => {
    ElMessage.success(message)
    setInterval(() => coldTime.value--, 1000)
  }, (message) => {
    coldTime.value = 0
    ElMessage.warning(message)
  })
}

</script>

<template>
  <div style="margin: 30px;text-align: center">
    <div style=" margin-top: 100px" >
      <div style="font-size: 25px;font-weight: bold">用户注册</div>
      <div style="font-size: 14px;color: grey">请在此输入您的相关信息</div>
    </div>
    <div style="margin-top: 30px">
      <el-form :rules="rules" :model="form" @validate="onValidate" ref="formRef">
        <el-form-item prop="username">
          <el-input v-model="form.username" :maxlength="8" type="text" placeholder="用户名">
            <template #prefix>
              <el-icon><User /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" :maxlength="16" type="password" placeholder="密码" >
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item prop="password_repeat">
          <el-input v-model="form.password_repeat" :maxlength="16" type="password" placeholder="重复密码" >
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item prop="email">
            <el-row :gutter="5" style="width: 100%">
              <el-col :span="17">
                <el-input v-model="form.email" type="email" placeholder="请输入您的邮箱">
                  <template #prefix>
                    <el-icon><Message /></el-icon>
                  </template>
                </el-input>
              </el-col>
              <el-col :span="6" style="text-align: right">
                <el-button type="success" :disabled="isEmailValid || coldTime > 0 "  @click="askCode">
                  {{coldTime > 0 ? '请稍后' + coldTime + '秒' : '获取验证码' }}
                </el-button>
              </el-col>
            </el-row>
        </el-form-item>
        <el-form-item prop="code">
          <el-input v-model="form.code" :maxlength="6" type="text" placeholder="验证码" >
            <template #prefix>
              <el-icon><InfoFilled /></el-icon>
            </template>
          </el-input>
        </el-form-item>
      </el-form>



    </div>


    <div style="margin-top: 30px">
      <el-button  style="width: 270px" type="warning" @click="register" plain>注册用户</el-button>
    </div>
    <div style="margin-top: 20px">
      <el-button @click="router.push('/')" style="width: 270px" type="success">
        <el-icon><HomeFilled /></el-icon>
        返回登录
      </el-button>
    </div>
  </div>
</template>

<style scoped>

</style>
