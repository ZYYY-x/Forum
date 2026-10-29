// ============================================================================
//  Forum 微服务 CI —— Jenkins 声明式流水线
// ----------------------------------------------------------------------------
//  流程：检出源码 → 集群内 Kaniko 构建 7 个镜像 → 推送 Harbor
//        → 回写 Forum-deploy 的镜像 tag → 等待 ArgoCD 同步
//
//  Jenkins 侧前置条件（一次性）：
//    * 凭据 `Harbor`     : Username/Password，Harbor 账号
//    * 凭据 `github-pat` : Username/Password，对 ZYYY-x/Forum-deploy 有
//                          contents:write 的 PAT（username 填 GitHub 账号）
//    * 节点已配置 Harbor 明文仓库，且 ci 命名空间存在 kubectl 可用权限
//      （RBAC 见 Forum-deploy 的 bootstrap/README.md）
//    * 集群需可访问 github.com / rockylinux 等基础镜像源
// ============================================================================

def SERVICES = [
    [name: 'forum-gateway',              ctx: 'forum-cloud',  dockerfile: 'forum-gateway/Dockerfile'],
    [name: 'forum-user-service',         ctx: 'forum-cloud',  dockerfile: 'forum-user-service/Dockerfile'],
    [name: 'forum-content-service',      ctx: 'forum-cloud',  dockerfile: 'forum-content-service/Dockerfile'],
    [name: 'forum-notification-service', ctx: 'forum-cloud',  dockerfile: 'forum-notification-service/Dockerfile'],
    [name: 'forum-media-service',        ctx: 'forum-cloud',  dockerfile: 'forum-media-service/Dockerfile'],
    [name: 'forum-weather-service',      ctx: 'forum-cloud',  dockerfile: 'forum-weather-service/Dockerfile'],
    [name: 'forum-frontend',             ctx: 'frontend-jwt', dockerfile: 'Dockerfile'],
]

pipeline {
    agent any

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 45, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    environment {
        HARBOR_HOST    = '18.183.238.1'
        HARBOR_PROJECT = 'forum'
        GITOPS_REPO    = 'github.com/ZYYY-x/Forum-deploy.git'
        GITOPS_BRANCH  = 'main'
        CI_NAMESPACE   = 'ci'
        TARGET_TAG     = "${params.IMAGE_TAG ?: env.BUILD_NUMBER}"
    }

    parameters {
        string(name: 'IMAGE_TAG', defaultValue: '', description: '镜像 tag，留空使用 BUILD_NUMBER')
        booleanParam(name: 'SKIP_BUILD', defaultValue: false, description: '仅回写部署仓库，不重新构建')
    }

    stages {
        stage('Checkout') {
            steps {
                deleteDir()
                checkout scm
                sh 'git rev-parse --short HEAD | tee .git-sha'
            }
        }

        stage('Prepare') {
            steps {
                sh '''
                  set -e
                  mkdir -p "$WORKSPACE/bin"
                  if [ ! -x "$WORKSPACE/bin/kubectl" ]; then
                    K8S_VER=$(curl -fsSL https://dl.k8s.io/release/stable.txt)
                    curl -fsSL -o "$WORKSPACE/bin/kubectl" \
                      "https://dl.k8s.io/release/${K8S_VER}/bin/linux/amd64/kubectl"
                    chmod +x "$WORKSPACE/bin/kubectl"
                  fi
                  export PATH="$WORKSPACE/bin:$PATH"
                  kubectl version --client=true
                  kubectl -n "$CI_NAMESPACE" get sa >/dev/null
                '''
            }
        }

        stage('Sync Harbor pull secret') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'Harbor',
                                                  usernameVariable: 'HARBOR_USER',
                                                  passwordVariable: 'HARBOR_PASS')]) {
                    sh '''
                      set -e
                      export PATH="$WORKSPACE/bin:$PATH"
                      kubectl -n "$CI_NAMESPACE" create secret docker-registry harbor-regcred \
                        --docker-server="$HARBOR_HOST" \
                        --docker-username="$HARBOR_USER" \
                        --docker-password="$HARBOR_PASS" \
                        --dry-run=client -o yaml | kubectl apply -f -
                    '''
                }
            }
        }

        stage('Build & Push') {
            when { expression { !params.SKIP_BUILD } }
            steps {
                script {
                    for (svc in SERVICES) {
                        echo "==> ${svc.name}:${TARGET_TAG}"
                        sh """
                          set -e
                          export PATH="\$WORKSPACE/bin:\$PATH"
                          JOB="ci-build-${svc.name}-\$(date +%s)"
                          sed -e "s#__JOB__#\$JOB#g" \
                              -e "s#__CONTEXT__#${svc.ctx}#g" \
                              -e "s#__DOCKERFILE__#${svc.dockerfile}#g" \
                              -e "s#__DEST__#${HARBOR_HOST}/${HARBOR_PROJECT}/${svc.name}:${TARGET_TAG}#g" \
                              ci/kaniko-job.yaml.tmpl | kubectl apply -f -

                          if ! kubectl -n ${CI_NAMESPACE} wait --for=condition=complete \
                                 job/\$JOB --timeout=900s; then
                            echo "---- build failed, last logs ----"
                            kubectl -n ${CI_NAMESPACE} logs job/\$JOB --tail=80 || true
                            kubectl -n ${CI_NAMESPACE} delete job \$JOB --ignore-not-found
                            exit 1
                          fi
                          kubectl -n ${CI_NAMESPACE} logs job/\$JOB --tail=3 || true
                          kubectl -n ${CI_NAMESPACE} delete job \$JOB --ignore-not-found
                        """
                    }
                }
            }
        }

        stage('Update GitOps repo') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'github-pat',
                                                  usernameVariable: 'GIT_USER',
                                                  passwordVariable: 'GIT_PASS')]) {
                    sh '''
                      set -e
                      rm -rf .gitops
                      git clone --depth 1 -b "$GITOPS_BRANCH" \
                        "https://${GIT_USER}:${GIT_PASS}@${GITOPS_REPO}" .gitops
                      cd .gitops

                      sed -i -E 's#(newTag: ).*#\1"'${TARGET_TAG}'"#' apps/forum/kustomization.yaml

                      git config user.email "jenkins@forum.local"
                      git config user.name  "Jenkins CI"
                      if git diff --quiet; then
                        echo "no image tag change, skip commit"
                      else
                        git add apps/forum/kustomization.yaml
                        git commit -m "chore(forum): bump images to ${TARGET_TAG} [skip ci]"
                        git push origin HEAD:"$GITOPS_BRANCH"
                      fi
                    '''
                }
            }
        }

        stage('Wait ArgoCD') {
            when { expression { !params.SKIP_BUILD } }
            steps {
                sh '''
                  set -e
                  export PATH="$WORKSPACE/bin:$PATH"
                  for i in $(seq 1 60); do
                    SYNC=$(kubectl -n devops-tools get application forum -o jsonpath='{.status.sync.status}' 2>/dev/null || echo Unknown)
                    HEALTH=$(kubectl -n devops-tools get application forum -o jsonpath='{.status.health.status}' 2>/dev/null || echo Unknown)
                    echo "[$i] sync=$SYNC health=$HEALTH"
                    if [ "$SYNC" = "Synced" ] && [ "$HEALTH" = "Healthy" ]; then exit 0; fi
                    sleep 10
                  done
                  echo "WARN: ArgoCD 未在 10 分钟内收敛（不阻断构建）"
                '''
            }
        }
    }

    post {
        success { echo "images pushed: ${HARBOR_HOST}/${HARBOR_PROJECT}/*:${TARGET_TAG}" }
        failure { echo "FAILED —— 常见原因：节点 CPU requests 已满、Harbor 不可达、RBAC 不足" }
    }
}
