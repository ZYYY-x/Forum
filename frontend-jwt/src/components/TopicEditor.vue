<script setup>
import {Check, Document} from "@element-plus/icons-vue";
import {ref,computed,reactive} from "vue";
import {Delta, Quill, QuillEditor} from "@vueup/vue-quill";
import ImageResize from "quill-image-resize-vue";
import {ImageExtend, QuillWatch} from "quill-image-super-solution-module";
import '@vueup/vue-quill/dist/vue-quill.snow.css'
import {ElMessage} from "element-plus";
import axios from "axios";
import {accessHeader, post} from "@/net";
import ColorDot from "@/components/ColorDot.vue";
import {useStore} from "@/stores";

const props = defineProps({
    show: Boolean,
    defaultTitle: {
        default: '',
        type: String
    },
    defaultText: {
        default: '',
        type: String
    },
    defaultType: {
        default: null,
        type: Number
    },
    submitButton: {
        default: '发表帖子',
        type: String
    },
    submit: {
        default: (editor, success) => {
            post('/api/forum/create-topic', {
                type: editor.type.id,
                title: editor.title,
                content: editor.text
            }, () => {
                ElMessage.success("帖子发表成功！")
                success()
            })
        },
        type: Function
    }
})

const refEditor = ref()
const store = useStore()

const emit = defineEmits(['close','success'])
const editor = reactive({
    type: null,
    title: '',
    text: '',
    loading: false
})
function initEditor(){
    if(props.defaultText)
        editor.text = new Delta(JSON.parse(props.defaultText))
    else
        refEditor.value.setContents('','user')
    editor.title = props.defaultTitle
    editor.type = findTypeById(props.defaultType)
}


Quill.register('modules/imageResize', ImageResize)
Quill.register('modules/ImageExtend', ImageExtend)

function deltaToText(delta){
    if (!delta.ops) return ""
    let str = ""
    for (let op of delta.ops)
        str+=op.insert
    return str.replace(/\s/g,"")
}
const contentLength = computed(()=>deltaToText(editor.text).length)

function findTypeById(id){
    for (let type of store.forum.types) {
        if (type.id === id)
            return type
    }
}

function submitTopic(){
    const text = deltaToText(editor.text)
    if(text.length > 10000) {
        ElMessage.warning('字数超出限制，无法发布主题！')
        return
    }
    if(!editor.title) {
        ElMessage.warning('请填写标题！')
        return
    }
    if(!editor.type) {
        ElMessage.warning('请选择一个合适的帖子类型！')
        return
    }
    props.submit(editor,()=>emit('success'))

}



const editorOption = {
    modules: {
        toolbar: {
            container: [
                "bold", "italic", "underline", "strike", "clean",
                {color: []}, {'background': []},
                {size: ["small", false, "large", "huge"]},
                {header: [1, 2, 3, 4, 5, 6, false]},
                {list: "ordered"}, {list: "bullet"}, {align: []},
                "blockquote", "code-block", "link", "image",
                {indent: '-1'}, {indent: '+1'}
            ],
            handlers: {
                'image': function () {
                    QuillWatch.emit(this.quill.id)
                }
            }
        },
        imageResize: {
            modules: ['Resize', 'DisplaySize']
        },
        ImageExtend: {
            action: axios.defaults.baseURL + '/api/image/cache',
            name: 'file',
            size: 5,
            loading: true,
            accept: 'image/png, image/jpeg',
            response: (resp) => {
                if (resp.data) {
                    return axios.defaults.baseURL + '/images' + resp.data
                } else {
                    return null
                }
            },
            methods: 'POST',
            headers: xhr => {
                xhr.setRequestHeader('Authorization', accessHeader().Authorization);
            },
            start: () => editor.uploading = true,
            success: () => {
                ElMessage.success('图片上传成功!')
                editor.uploading = false
            },
            error: () => {
                ElMessage.warning('图片上传失败，请联系管理员!')
                editor.uploading = false
            }
        }
    }
}

</script>

<template>
    <div>
        <el-drawer :model-value="show"
                   direction="btt"
                   :close-on-click-modal="false"
                   @close="emit('close')"
                   @open="initEditor"
                   :size="650">
            <template #header>
                <div>
                    <div style="font-weight: bold">发表新的帖子</div>
                    <div style="font-size: 13px">文明发帖</div>
                </div>
            </template>
            <div style="display: flex;gap: 10px">
                <div style="width: 150px">
                    <el-select v-model="editor.type" value-key="id" placeholder="选择发帖主题..." :disabled="!store.forum.types.length">
                        <el-option v-for="item in store.forum.types.filter(type=>type.id>0)" :value="item" :label="item.name">
                            <div>
                                <color-dot :color="item.color"/>
                                <span style="margin-left: 5px">{{ item.name }}</span>
                            </div>
                        </el-option>
                    </el-select>
                </div>
                <div style="flex: 1">
                    <el-input v-model="editor.title" placeholder="在此输入您的帖子标题..." :prefix-icon="Document"
                              style="height: 100%" maxlength="30"/>
                </div>
            </div>
            <div style="margin-top: 5px;font-size: 13px;color: grey">
                <color-dot :color="editor.type?editor.type.color: '#727070'"/>
                <span style="margin-left: 5px">{{ editor.type ? editor.type.desc :  '请在上方选择发帖主题' }}</span>
            </div>
            <div style=" margin-top: 10px;height: 440px;overflow: hidden ;border-radius: 5px"
                 v-loading="editor.uploading" element-loading-text="正在上传图片,请稍后...">
                <quill-editor v-model:content="editor.text" style="height: calc(100% - 45px)"
                              content-type="delta" ref="refEditor"
                              placeholder="在此输入内容..." :options="editorOption"/>
            </div>
            <div style="display: flex;justify-content: space-between;margin-top: 5px">
                <div style="color: grey;font-size: 13px">
                    当前字数（{{ contentLength }}）最大字数10000
                </div>
                <div>
                    <el-button @click="submitTopic" :icon="Check" type="success" plain>{{ submitButton }}</el-button>
                </div>
            </div>
        </el-drawer>
    </div>
</template>

<style scoped>
:deep(.el-drawer) {
    width: 800px;
    margin: auto;
    border-radius: 10px 10px 0 0;
}

:deep(.el-drawer__header) {
    margin: 0;
}
</style>