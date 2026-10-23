<script setup>
import {ref,reactive} from "vue";
import {InfoFilled, Lock, Message} from "@element-plus/icons-vue";
import {ElMessage} from "element-plus";
import {get, post} from "@/net";
import router from "@/router";

const form = reactive({
  email: '',
  code: '',
  password: '',
  password_repeat: ''
})

const isEmailValid = ref(true)
const  coldTime =ref(0)
const formRef = ref()
const validatePassword = (rule, value, callback) => {
  if (value === '') {
    callback(new Error('请重新输入'))
  } else if (value !== form.password) {
    callback(new Error("两次输入的密码不一致!"))
  } else {
    callback()
  }
}

const onValidate = (prop,isValid) => {
  if (prop === 'email')
    isEmailValid.value = !isValid
}

const validateEmail = () =>{
  coldTime.value = 60

  ElMessage.warning('邮件发送中...请稍等')
  post('api/auth/valid-reset-email',{
    email: form.email
  },(message) => {
    ElMessage.success(message)
    setInterval(() => coldTime.value--,1000)
  },(message) =>{
    ElMessage.warning(message)
    coldTime.value = 0
  })
}
const rules={
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

const active=ref(0)


const startReset = ()=>{
  formRef.value.validate((isValid) => {
    if (isValid){
      post('api/auth/start-reset',{
        email: form.email,
        code: form.code
      },()=>{
        active.value++
      })
    }else {
      ElMessage.warning('请填写电子邮件地址和验证码！')
    }
  })
}


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

function confirmRest(){
  formRef.value.validate((valid)=>{
    if (valid){
      post('/api/auth/reset-confirm',{
        email: form.email,
        code: form.code
      },()=>{
        active.value++
      })
    }
  })
}

function doReset(){
  formRef.value.validate((valid)=>{
    if (valid){
      post('/api/auth/reset-password',{...form},()=>{
        ElMessage.success("密码重置成功，请重新登录！")
        router.push('/')
      })
    }
  })
}
</script>

<template>
  <div>
    <transition mode="out-in" name="el-fade-in-linear">
      <div style="margin: 30px;text-align: center ; height: 100%" v-if="active === 0">
        <div style=" margin-top: 130px" >
          <div style="font-size: 25px;font-weight: bold">重置密码</div>
          <div style="font-size: 14px;color: grey ; margin-top: 10px">输入并验证您的电子邮件地址，以重置用户密码</div>
          <div style="margin-top: 20px">
            <el-steps :active="active" finish-status="success" align-center>
              <el-step title="验证电子邮件" />
              <el-step title="重置账户密码" />
            </el-steps>
          </div>
        </div>
        <div style="margin-top: 20px">
          <el-form :rules="rules" :model="form" @validate="onValidate" ref="formRef" >
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
        <div style="margin-top: 50px">
          <el-button @click="confirmRest" style="width: 270px" type="danger" plain>重置密码</el-button>
        </div>
      </div>
    </transition>



    <transition mode="out-in" name="el-fade-in-linear">
      <div style="margin: 30px;text-align: center ; height: 100%"  v-if="active === 1">
        <div style=" margin-top: 130px" >
          <div style="font-size: 25px;font-weight: bold">重置密码</div>
          <div style="font-size: 14px;color: grey ; margin-top: 10px">输入新密码</div>
          <div style="margin-top: 20px">
            <el-steps :active="active" finish-status="success" align-center>
              <el-step title="验证电子邮件" />
              <el-step title="重置账户密码" />
            </el-steps>
          </div>
          <div style="margin-top: 10px">
            <el-form :rules="rules" :model="form" @validate="onValidate" ref="formRef">
              <el-form-item prop="password">
                <el-input v-model="form.password" :maxlength="16" type="password" placeholder="新密码" >
                  <template #prefix>
                    <el-icon><Lock /></el-icon>
                  </template>
                </el-input>
              </el-form-item>
              <el-form-item prop="password_repeat">
                <el-input v-model="form.password_repeat" :maxlength="16" type="password" placeholder="重复新密码" >
                  <template #prefix>
                    <el-icon><Lock /></el-icon>
                  </template>
                </el-input>
              </el-form-item>
            </el-form>
          </div>
        </div>
        <div style="margin-top: 30px">
          <el-button style="width: 270px" @click="doReset" type="success" plain>重置密码</el-button>
        </div>
      </div>
    </transition>
  </div>


</template>

<style scoped>

</style>