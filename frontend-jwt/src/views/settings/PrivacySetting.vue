<script setup>

import Card from "@/components/Card.vue";
import {Setting, Switch ,Lock} from "@element-plus/icons-vue";
import {ref, reactive} from "vue";
import {get, post} from "@/net";
import {ElMessage} from "element-plus";

const form = reactive({
  password: '',
  new_password: '',
  new_password_repeat: ''
})
const validatePassword = (rule, value, callback) => {
  if (value === '') {
    callback(new Error('请重新输入'))
  } else if (value !== form.new_password) {
    callback(new Error("两次输入的密码不一致!"))
  } else {
    callback()
  }
}
const rules = {
  password: [
    {
      required: true, message: '请输入原密码', trigger: 'blur'
    }
  ],
  new_password:[
    {required: true, message: '请输入新密码', trigger: 'blur'},
    { min: 6, max: 16, message: '密码应该在6~16字符', trigger: ['blur' , 'change']}
  ],
  new_password_repeat:[
    {required: true, message: '请重新输入新密码', trigger: 'blur'},
    {validator: validatePassword,trigger: ['blur' , 'change']}
  ]
}

const formRef = ref()
const valid = ref(false)
const onValidate = (prop,isValid) =>valid.value = isValid

function resetPassword(){
  formRef.value.validate(valid =>{
    if(valid){
      post('/api/user/change-password',form,()=>{
        ElMessage.success('修改密码成功！')
        formRef.value.resetFields();
      },(message)=>{
        ElMessage.warning(message)
      })
    }
  })
}

const saving= ref(true)
const privacy = reactive({
  phone: false,
  wx:false,
  qq:false,
  email: false,
  gender: false
})
get('/api/user/privacy',data=>{
  privacy.phone=data.phone
  privacy.wx=data.wx
  privacy.qq=data.qq
  privacy.email=data.email
  privacy.gender=data.gender
  saving.value=false
})

function savePrivacy(type,status){
  saving.value = true
  post('/api/user/save-privacy',{
    type: type,
    status: status
  },()=>{
    ElMessage.success('隐私设置修改成功')
    saving.value=false
  })
}
</script>

<template>
  <div style="margin: auto ;max-width: 600px;">
    <div style="margin-top: 20px">
      <card :icon="Setting" title="隐私设置" desc="在此处设置您的相关信息展示权限" v-loading="saving">
        <div class="checkbox-list">
          <el-checkbox @change="savePrivacy('phone',privacy.phone)" v-model="privacy.phone">公开展示我的手机号</el-checkbox>
          <el-checkbox @change="savePrivacy('email',privacy.email)" v-model="privacy.email">公开展示我的电子邮件</el-checkbox>
          <el-checkbox @change="savePrivacy('wx',privacy.wx)" v-model="privacy.wx">公开展示我的微信号</el-checkbox>
          <el-checkbox @change="savePrivacy('qq',privacy.qq)" v-model="privacy.qq">公开展示我的qq号</el-checkbox>
          <el-checkbox @change="savePrivacy('gender',privacy.gender)" v-model="privacy.gender">公开展示我的性别</el-checkbox>
        </div>
      </card>
      <card style="margin: 20px 0" :icon="Setting" title="修改密码" desc="在此处修改您的密码,务必牢记您的密码">
        <div style="margin-right: 10px">
          <el-form :rules="rules" :model="form" ref="formRef" @validate="onValidate" label-width="100px" style="margin-top: 20px; margin-bottom: 10px">
            <el-form-item label="当前密码：" prop="password">
              <el-input type="password" :prefix-icon="Lock" v-model="form.password" placeholder="当前密码" maxlength="16"/>
            </el-form-item>
            <el-form-item label="新密码：" prop="new_password">
              <el-input type="password" :prefix-icon="Lock" v-model="form.new_password" placeholder="新密码" maxlength="16"/>
            </el-form-item>
            <el-form-item label="重复密码：" prop="new_password_repeat">
              <el-input type="password" :prefix-icon="Lock" v-model="form.new_password_repeat" placeholder="重复新密码" maxlength="16"/>
            </el-form-item>
            <div style="text-align: center">
              <el-button @click="resetPassword" :icon="Switch" type="success">立即重置密码</el-button>
            </div>
          </el-form>
        </div>
      </card>
    </div>
  </div>
</template>

<style scoped>
.checkbox-list{
  margin: 10px 0 0 10px;
  display: flex;
  flex-direction: column;
}
</style>