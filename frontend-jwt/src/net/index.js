import axios from  "axios";
import {ElMessage} from "element-plus";

const authItemName = "access_token"

const defaultError = (error)=> {
    ElMessage.error('o.O,发生了一些错误！')
    console.warn(error)
}
const defaultFailure=(message,code,url)=> {
    ElMessage.warning(message)
    console.warn(`请求地址: ${url}, 状态码,${code},错误信息：${message}`)
}

function takeAccessToken (){
    const str = localStorage.getItem(authItemName)  || sessionStorage.getItem(authItemName)
    if (!str) return null;
    const authObj = JSON.parse(str)
    if (authObj.expire <= new Date()){
        deleteAccessToken()
        ElMessage.warning('登录状态过期！请重新登录')
        return null
    }
    return authObj.token
}

function accessHeader(){
    const token = takeAccessToken();
    return token ? {
        'Authorization': `Bearer ${takeAccessToken()}`
    } :{}
}

function deleteAccessToken(){
    localStorage.removeItem(authItemName)
    sessionStorage.removeItem(authItemName)
}

function storeAccessToken(token,remember,expire){
    const authObj ={
        token: token,
        expire: expire
    }
    const str = JSON.stringify(authObj)
    if (remember)
        localStorage.setItem(authItemName,str)
    else
        sessionStorage.setItem(authItemName,str)
}

function internalPost(url,data,header,success,failure=defaultFailure,error=defaultError) {
    axios.post(url,data,{
        headers: header}).then(({data})=>{
        if (data.code === 200){
            success(data.data)
        }
        else
            failure(data.message,data.code,url)
    }).catch(error)
}


function internalGet(url,header,success,failure=defaultFailure,error=defaultError) {
    axios.get(url,{headers: header}).then(({data})=>{
        if (data.code === 200){
            success(data.data)
        }
        else
            failure(data.message,data.code,url)
    }).catch(error)
}

function login(username,password,remember,success,failure = defaultFailure){
    internalPost('/api/auth/login',{
        username: username,
        password: password
    },{
        'Content-Type': 'application/x-www-form-urlencoded'
    },(data) => {
        storeAccessToken(data.token,remember,data.expire)
         ElMessage.success(`登录成功，欢迎${data.username}`)
        success(data)
    },failure)
}

function get(url,success,failure=defaultFailure){
    internalGet(url,accessHeader(),success,failure)
}

function post(url,data,success,failure=defaultFailure){
    internalPost(url,data,accessHeader(),success,failure)
}

function logout(success,failure){
    get('/api/auth/logout',()=>{
        deleteAccessToken()
        ElMessage.success('退出登录成功')
        success()
    },failure)
}
function unauthorized(){
    return !takeAccessToken()
}

export { get, post, login, logout,unauthorized , accessHeader}
