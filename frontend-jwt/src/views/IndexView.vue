<script setup>
import {get, logout} from "@/net";
import {ref, reactive, watchEffect} from "vue";
import router from "@/router";
import {useStore} from "@/stores";
import {
    Back,
    Bell,
    ChatDotSquare, Check, Collection, DataLine,
    Document, Files,
    Location, Lock, Message, Monitor,
    Notification, Operation,
    Position,
    School, Search,
    Umbrella, User
} from "@element-plus/icons-vue";
import LightCard from "@/components/LightCard.vue";

const store = useStore()
const loading = ref(true)
const searchInput = reactive({
    type: '1',
    text: ''
})
const notification = ref([])



get('/api/user/info', (data) => {
    store.user = data
    loading.value = false
})
const loadNotification =
        () => get('/api/notification/list', data => notification.value = data)
loadNotification()
function userLogout() {
    logout(() => {
        router.push('/')
    })
}
function confirmNotification(id, url) {
    get(`/api/notification/delete?id=${id}`, () => {
        loadNotification()
        window.open(url)
    })
}

function deleteAllNotification() {
    get(`/api/notification/delete-all`, loadNotification)
}

const isMenuCollapsed = ref(false);
const isMobile = ref(false);
watchEffect(() => {
    isMobile.value = window.innerWidth <= 768;
});
const toggleMenuCollapse = () => {
    isMenuCollapsed.value = !isMenuCollapsed.value;
};


</script>

<template>
    <div class="main-content" v-loading="loading" element-loading-text="加载中，请稍后...">
        <el-container style="height: 100%" v-if="!loading">
            <el-header class="main-content-header">
                <el-image class="logo" src="https://element-plus.org/images/element-plus-logo.svg"></el-image>
                <div style="flex: 1; padding: 0 20px;text-align: center">
                    <el-input v-model="searchInput.text" style="width: 100%;max-width: 450px"
                              placeholder="搜索本站内容...">
                        <template #prefix>
                            <el-icon>
                                <Search/>
                            </el-icon>
                        </template>
                        <template #append>
                            <el-select v-model="searchInput.type" style="width: 120px">
                                <el-option value="1" label="帖子广场"/>
                                <el-option value="2" label="校园活动"/>
                                <el-option value="3" label="表白墙"/>
                                <el-option value="4" label="教务通知"/>
                            </el-select>
                        </template>
                    </el-input>
                </div>
                <div class="user-info">
                    <el-popover placement="bottom" :width="350" trigger="click">
                        <template #reference>
                            <el-badge style="margin-right: 15px" is-dot :hidden="!notification.length">
                                <div class="notification">
                                    <el-icon><Bell/></el-icon>
                                    <div style="font-size: 10px">消息</div>
                                </div>
                            </el-badge>
                        </template>
                        <el-empty :image-size="80" description="暂时没有未读消息哦~" v-if="!notification.length"/>
                        <el-scrollbar :max-height="500" v-else>
                            <light-card v-for="item in notification" class="notification-item"
                                        @click="confirmNotification(item.id, item.url)">
                                <div>
                                    <el-tag size="small" :type="item.type">消息</el-tag>&nbsp;
                                    <span style="font-weight: bold">{{item.title}}</span>
                                </div>
                                <el-divider style="margin: 7px 0 3px 0"/>
                                <div style="font-size: 13px;color: grey">
                                    {{item.content}}
                                </div>
                            </light-card>
                        </el-scrollbar>
                        <div style="margin-top: 10px">
                            <el-button size="small" type="info" :icon="Check" @click="deleteAllNotification"
                                       style="width: 100%" plain>清除全部未读消息</el-button>
                        </div>
                    </el-popover>
                    <div class="profile">
                        <div>{{ store.user.username }}</div>
                        <div>{{ store.user.email }}</div>
                    </div>
                    <el-dropdown>
                        <el-avatar :src="store.avatarUrl"/>
                        <template #dropdown>
                            <el-dropdown-item>
                                <el-icon>
                                    <operation/>
                                </el-icon>
                                个人设置
                            </el-dropdown-item>
                            <el-dropdown-item>
                                <el-icon>
                                    <message/>
                                </el-icon>
                                消息列表
                            </el-dropdown-item>
                            <el-dropdown-item @click="userLogout" divided>
                                <el-icon>
                                    <Back/>
                                </el-icon>
                                退出登录
                            </el-dropdown-item>
                        </template>
                    </el-dropdown>
                </div>
            </el-header>
            <el-container>
                <el-aside width="230px">
                    <el-scrollbar style="height: calc(100vh - 55px)">
                        <el-menu
                                router
                                :default-active="$route.path"
                                :default-openeds="['1','2','3']"
                                :collapse="isMobile ? isMenuCollapsed : false"
                                style="min-height: calc(100vh - 55px)">
                            <div style="text-align: center">
                                <el-button :icon="Location" class="toggle-menu-btn" icon="el-icon-s-fold" @click="toggleMenuCollapse" v-if="isMobile"></el-button>
                            </div>
                            <el-sub-menu index="1">
                                <template #title>
                                    <el-icon>
                                        <location/>
                                    </el-icon>
                                    <span><b>校园论坛</b></span>
                                </template>
                                <el-menu-item index="/index">
                                    <template #title>
                                        <el-icon>
                                            <chat-dot-square/>
                                        </el-icon>
                                        帖子广场
                                    </template>
                                </el-menu-item>
                                <el-menu-item>
                                    <template #title>
                                        <el-icon>
                                            <bell/>
                                        </el-icon>
                                        失物招领
                                    </template>
                                </el-menu-item>
                                <el-menu-item>
                                    <template #title>
                                        <el-icon>
                                            <Notification/>
                                        </el-icon>
                                        校园活动
                                    </template>
                                </el-menu-item>
                                <el-menu-item>
                                    <template #title>
                                        <el-icon>
                                            <umbrella/>
                                        </el-icon>
                                        表白墙
                                    </template>
                                </el-menu-item>
                                <el-menu-item>
                                    <template #title>
                                        <el-icon>
                                            <school/>
                                        </el-icon>
                                        xx考研
                                        <el-tag style="margin-left: 10px" size="small">合作机构</el-tag>
                                    </template>
                                </el-menu-item>
                            </el-sub-menu>

                            <el-sub-menu index="2">
                                <template #title>
                                    <el-icon>
                                        <Position/>
                                    </el-icon>
                                    <span><b>其他功能</b></span>
                                </template>
                                <el-menu-item>
                                    <template #title>
                                        <el-icon>
                                            <Document/>
                                        </el-icon>
                                        成绩查询
                                    </template>
                                </el-menu-item>
                                <el-menu-item>
                                    <template #title>
                                        <el-icon>
                                            <files/>
                                        </el-icon>
                                        课表查询
                                    </template>
                                </el-menu-item>
                                <el-menu-item>
                                    <template #title>
                                        <el-icon>
                                            <Monitor/>
                                        </el-icon>
                                        教务通知
                                    </template>
                                </el-menu-item>
                                <el-menu-item>
                                    <template #title>
                                        <el-icon>
                                            <Collection/>
                                        </el-icon>
                                        在线图书
                                    </template>
                                </el-menu-item>
                                <el-menu-item>
                                    <template #title>
                                        <el-icon>
                                            <DataLine/>
                                        </el-icon>
                                        教室预约
                                    </template>
                                </el-menu-item>
                            </el-sub-menu>

                            <el-sub-menu index="3">
                                <template #title>
                                    <el-icon>
                                        <operation/>
                                    </el-icon>
                                    <span><b>个人设置</b></span>
                                </template>
                                <el-menu-item index="/index/user-setting">
                                    <template #title>
                                        <el-icon>
                                            <user/>
                                        </el-icon>
                                        个人信息设置
                                    </template>
                                </el-menu-item>
                                <el-menu-item index="/index/privacy-setting">
                                    <template #title>
                                        <el-icon>
                                            <Lock/>
                                        </el-icon>
                                        账号安全设置
                                    </template>
                                </el-menu-item>
                            </el-sub-menu>
                        </el-menu>
                    </el-scrollbar>

                </el-aside>
                <el-main class="main-content-page">
                    <el-scrollbar style="height: calc(100vh - 55px)">
                        <router-view v-slot="{ Component }">
                            <transition name="el-fade-in-linear" mode="out-in">
                                <component :is="Component" style="height: 100%"/>
                            </transition>
                        </router-view>
                    </el-scrollbar>
                </el-main>
            </el-container>
        </el-container>
    </div>

    <div style="width: 100px;">
        <p style="
        font-size: 14px;
        background:none;
        height: 5%;
        position: fixed;
        bottom: 0;
        width: 100%;
        text-align: center;
        z-index: 999;
        ">
            <a style="color: grey; text-decoration: none"
               href="https://beian.miit.gov.cn/"
               target="_blank">吉ICP备2024014716号</a>
        </p>
    </div>
</template>

<style lang="less" scoped>
.notification-item {
    transition: .3s;
    &:hover {
        cursor: pointer;
        opacity: 0.7;
    }
}

.notification {
    font-size: 22px;
    line-height: 14px;
    text-align: center;
    transition: color .3s;

    &:hover {
        color: grey;
        cursor: pointer;
    }
}
.main-content {
    height: 100vh;
    width: 100vw;
}

.main-content-header {
    border-bottom: solid 1px var(--el-border-color);
    height: 55px;
    display: flex;
    align-items: center;
    box-sizing: border-box;

    .logo {
        height: 32px;
    }

    .user-info {
        display: flex;
        justify-content: flex-end;
        align-items: center;

        .el-avatar:hover {
            cursor: pointer;
        }

        .profile {
            text-align: right;
            margin-right: 20px;

            :first-child {
                font-size: 18px;
                font-weight: bold;
                line-height: 20px;
            }

            :last-child {
                font-size: 10px;
                color: grey;
            }
        }
    }
}

.main-content-page {
    padding: 0;
    background-color: #f7f8fa;
}

.dark .main-content-page {
    background-color: #242628;
}

</style>
