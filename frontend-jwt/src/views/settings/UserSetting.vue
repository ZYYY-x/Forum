<script setup>

import Card from "@/components/Card.vue";
import {Message, Refresh, Select, User} from "@element-plus/icons-vue";
import {useStore} from "@/stores";
import {computed,reactive,ref} from "vue";
import {accessHeader, get, post} from "@/net";
import {ElMessage} from "element-plus";
import axios from "axios";

const store=useStore()
const registerTime = computed(()=>new Date(store.user.registerTime).toLocaleString())

const desc = ref('')
const baseFromRef = ref()
const emailFromRef = ref()
const baseFrom = reactive({
  username: '',
  gender: 1,
  phone: '',
  qq: '',
  wx: '',
  desc: ''
})

const emailFrom =  reactive({
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

const rules = {
  username:[
    { validator: validateUsername , trigger: ['blur' , 'change'] },
    { min: 2, max: 8, message: '用户名长度应该在2~8字符', trigger: ['blur' , 'change']},
  ],email:[
    { required: true, message: '请输入电子邮件地址', trigger: ['blur' , 'change'] },
    {
      type: 'email', message: '电子邮件地址不合法', trigger: ['blur', 'change'],
    }
  ],code: [
    { required: true, message: '请输入获取的验证码', trigger: ['blur'] },
  ]
}
const loading = reactive({
  form: true,
  base: false
})
function saveDetails() {
  baseFromRef.value.validate(isValid =>{
    if (isValid){
      loading.base=true
      post('/api/user/save-details',baseFrom,()=>{
        ElMessage.success('用户信息保存成功')
        store.user.username=baseFrom.username
        desc.value = baseFrom.desc
        loading.base=false
      },(message)=>{
        ElMessage.warning(message)
        loading.base=false
      })
    }
  })
}
const coldTime =ref(0)
const isEmailValid = ref(true)
const onValidate = (prop, isValid) => {
  if (prop === 'email')
    isEmailValid.value = isValid
}
function sendEmailCode(){
  emailFromRef.value.validate(isValid=>{
    if (isValid){
      coldTime.value=60
      get(`/api/auth/ask-code?email=${emailFrom.email}&type=modify`,()=>{
        ElMessage.success(`验证码正在发送${emailFrom.email}，请稍等`)
        const handle = setInterval(()=>{
          coldTime.value--
          if (coldTime.value===0){
            clearInterval(handle)
          }
        },1000)
      },(message)=>{
        ElMessage.warning(message)
        coldTime.value=0
      })
    }
  })
}

function modifyEmail(){
  emailFromRef.value.validate(isValid=>{
    if (isValid){
      post('/api/user/modify-email',emailFrom,()=>{
        ElMessage.success("邮件修改成功")
        store.user.email = emailFrom.email
        emailFrom.code =''
      })
    }
  })

}

get('/api/user/details',data=>{
  baseFrom.username=store.user.username
  baseFrom.gender=data.gender
  baseFrom.phone=data.phone
  baseFrom.qq=data.qq
  baseFrom.wx=data.wx
  baseFrom.desc = desc.value = data.desc
  emailFrom.email = store.user.email
  loading.form=false
})

const privacy = reactive({
  phone: false,
  wx: false,
  qq: false,
  email: false,
  gender: false,
})
function beforeAvatarUpload(rawFile){
  if (rawFile.type!=='image/jpeg'&&rawFile.type!=='image/png'){
    ElMessage.error('头像仅支持上传jpg或png格式')
    return false
  }else if (rawFile.size / 1024>1000){
    ElMessage.error("头像大小不能大于1MB")
    return false
  }
  return true
}

function uploadSuccess(response){
  ElMessage.success('头像上传成功')
  store.user.avatar = response.data
}
</script>

<template>
<div style="display: flex;max-width: 1050px;margin: auto" >
  <div class="setting-left">
    <card :icon="User" title="账号信息设置" desc="在这里编辑您的个人信息，您可以在隐私设置中选择是否展示这些信息" v-loading="loading.form">
      <el-form :model="baseFrom" :rules="rules" ref="baseFromRef" label-position="top" style="margin: 0 10px 10px 10px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="baseFrom.username" maxlength="10"/>
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="baseFrom.gender">
            <el-radio :label="0">男</el-radio>
            <el-radio :label="1">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="baseFrom.phone" maxlength="11"/>
        </el-form-item>
        <el-form-item label="QQ号" prop="qq">
          <el-input v-model="baseFrom.qq" maxlength="13"/>
        </el-form-item>
        <el-form-item label="微信号" prop="wx">
          <el-input v-model="baseFrom.wx" maxlength="20"/>
        </el-form-item>
        <el-form-item label="个人简介" prop="desc">
          <el-input v-model="baseFrom.desc" maxlength="200" type="textarea" :rows="6"/>
        </el-form-item>
        <div>
          <el-button :icon="Select" @click="saveDetails" :loading="loading.base"
                     type="success">保存信息</el-button>
        </div>
      </el-form>
    </card>
    <card style="margin-top: 10px" :icon="Message" title="电子邮件设置" desc="您可以在此修改默认绑定的电子邮件地址">
      <el-form :model="emailFrom" @validate="onValidate" :rules="rules" ref="emailFromRef" label-position="top" style="margin: 0 10px 10px 10px">
        <el-form-item prop="email" label="电子邮件">
          <el-input v-model="emailFrom.email"/>
        </el-form-item>
        <el-form-item prop="code">
          <el-row style="width: 100%" :gutter="10">
            <el-col :span="18">
              <el-input v-model="emailFrom.code" placeholder="请获取验证码"></el-input>
            </el-col>
            <el-col :span="6">
              <el-button type="success" :disabled="!isEmailValid||coldTime>0"
                         @click="sendEmailCode" style="width: 100%;" plain>
                {{ coldTime > 0 ? `请稍后 ${coldTime} 秒`:'获取验证码'}}
              </el-button>
            </el-col>
          </el-row>
        </el-form-item>
        <div>
          <el-button :icon="Refresh" @click="modifyEmail" type="success">更新电子邮件</el-button>
        </div>
      </el-form>
    </card>
  </div>
  <div class="setting-right">
    <div style="position: sticky;top: 20px">
      <card>
        <div style="text-align: center;padding: 5px 15px 0 15px">
          <el-avatar :size="70" :src="store.avatarUrl"/>
          <div style="margin: 5px 0">
            <el-upload
                :action="axios.defaults.baseURL + '/api/image/avatar'"
                :show-file-list="false"
                :before-upload="beforeAvatarUpload"
                :on-success="uploadSuccess"
                :headers="accessHeader()"
            >
              <el-button size="small" round>修改头像</el-button>
            </el-upload>
          </div>
          <div style="font-weight: bold">你好，{{ store.user.username }}</div>
        </div>
        <el-divider style="margin: 10px 0"/>
        <div style="font-size: 14px;color: grey;padding: 10px">
          {{ desc || '这个用户很懒，还没有填写个人简介~~'}}
        </div>
      </card>
      <card style="margin-top: 10px;font-size: 14px">
        <div>
          账号注册时间：{{ registerTime }}
        </div>
        <div style="color: grey">欢迎加入本论坛</div>
      </card>
    </div>
  </div>
</div>
</template>

<style lang="less" scoped>
.setting-left{
  flex: 1;
  margin: 20px;
}

.setting-right{
  width: 300px;
  margin: 20px;
}
</style>