pipeline {
    agent {label 'compile_x86_dds'}
    parameters {
        string(name: 'git_repository', defaultValue: 'http://gitlab.sequoiadb.com/sequoiadb/sequoiadb-dds-kubernetes-operator.git', description: '')
        string(name: 'branch', defaultValue: 'main', description: '')
        string(name: 'dock_repository', defaultValue: '192.168.20.106', description: '')
        choice(name: 'project_name', choices: ['ci_dev', 'sequoiadb'], description: 'Select project name')
        string(name: 'docker_image', defaultValue: '192.168.20.106/sequoiadb/dds-doc-builder:0.2.0', description: '')
    }
    
    options {
        disableConcurrentBuilds()
        timestamps()
        buildDiscarder(logRotator(artifactDaysToKeepStr: '5', artifactNumToKeepStr: '7', daysToKeepStr: '7', numToKeepStr: '5'))
    }

    stages {
        stage('pull code') {
            steps {
                cleanWs()
                checkout scmGit(branches: [[name: "${params.branch}"]], extensions: [], userRemoteConfigs: [[url: "${params.git_repository}"]])
                sh 'docker login -u wangwenjing -p wangwj_2012 192.168.20.106'
            }
        }
        
        stage('make') {
            parallel {
                stage('make build') {
                    steps {
                        script{
                            try{ 
                                def ret = sh(script: "docker pull 192.168.40.10:10010/tools/golang:1.20", returnStatus: true)
                                if (ret != 0) {
                                    error "docker pull failed with exit code ${ret}."
                                }
                                
                                ret = sh(script: "docker tag 192.168.40.10:10010/tools/golang:1.20 docker.io/library/golang:1.20", returnStatus: true)
                                if (ret != 0) {
                                    error "docker tag failed with exit code ${ret}."
                                }
                        
                                ret = sh(script: "docker pull ${params.dock_repository}/docker.io/library/ubuntu:jammy", returnStatus: true)
                                if (ret != 0) {
                                    error "docker pull failed with exit code ${ret}."
                                }
                        
                                ret = sh(script: "docker tag ${params.dock_repository}/docker.io/library/ubuntu:jammy ubuntu:jammy", returnStatus: true)
                                if (ret != 0) {
                                    error "docker tag failed with exit code ${ret}."
                                }
                        
                                ret = sh(script: "docker pull ${params.dock_repository}/sequoiadb/sequoaidb-dds-kubernetes-operator-base:1.0", returnStatus: true)
                                if (ret != 0) {
                                    error "docker pull failed with exit code ${ret}."
                                }
                        
                                ret = sh(script: "docker tag ${params.dock_repository}/sequoiadb/sequoaidb-dds-kubernetes-operator-base:1.0 sequoiadb/sequoaidb-dds-kubernetes-operator-base:1.0", returnStatus: true)
                                if (ret != 0) {
                                    error "docker tag failed with exit code ${ret}."
                                }
                        
                                ret = sh(script: "go env -w GO111MODULE=on", returnStatus: true)
                                if (ret != 0) {
                                    error "go env failed with exit code ${ret}."
                                }
                        
                                ret = sh(script: "go env -w GOPROXY=https://goproxy.cn,direct", returnStatus: true)
                                if (ret != 0) {
                                    error "go env failed with exit code ${ret}."
                                }
                        
                                ret = sh(script: "export IMAGE_TAG_OWNER=${params.project_name};export VERSION=${params.branch};export DOCKER_REGISTRY=${params.dock_repository}; export DOCKER_PUSH=1 && make build", returnStatus: true)
                                if (ret != 0) {
                                    error "make build failed with exit code ${ret}."
                                }
                            } catch (Exception e) {
                                echo "Caught exception: ${e}"
                                echo "Exception message: ${e.getMessage()}"
                                currentBuild.result = 'FAILURE'
                                error("Error in make build ${e}")
                            }
                        }
                    }
                }
                
                stage('make doc') {
                    agent{
                        docker{
                            image "${params.docker_image}"
                            label 'compile_x86_dds'
                            args "-v $WORKSPACE/:/usr/src/dds-operator"
                        }
                    }
                    
                    steps{
                        sh "cd /usr/src; python3 build.py /usr/src/dds-operator/manual"
                    }
                }
            }
        }
    }
    
    post{
        success{
            archiveArtifacts artifacts: '**/*.pdf', followSymlinks: false
        }
    }
}
