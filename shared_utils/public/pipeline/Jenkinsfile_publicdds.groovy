pipeline { 
    agent {label 'master'}
    
    parameters {
        string(name: 'product_repository', defaultValue: 'http://gitlab.sequoiadb.com/sequoiadb/dds.git', description: '')
        string(name: 'test_repository', defaultValue: 'http://gitlab.sequoiadb.com/sequoiadb/dds-test.git', description: '')
        string(name: 'product_branch', defaultValue: 'main', description: '')
        string(name: 'test_branch', defaultValue: 'main', description: '')
        string(name: 'jobs', defaultValue: '16', description: '')
        booleanParam(name: 'exec_compile', defaultValue: false, description: '')
        booleanParam(name: 'exec_test', defaultValue: true, description: '')
        booleanParam(name: 'public_image', defaultValue: true, description: '')
    }
    
    environment {
        CI_GIT_URL = 'http://gitlab.sequoiadb.com/sequoiadb/ci/ci_common.git'
    }
    
    options {
        disableConcurrentBuilds()
        timestamps()
        buildDiscarder(logRotator(artifactDaysToKeepStr: '7', artifactNumToKeepStr: '1', daysToKeepStr: '7', numToKeepStr: '1'))
    }

    stages {
        stage('compile') {
            when {expression { params.exec_compile == true }}
            parallel {
                stage('compile x86'){
                    steps{
                        script{
                            def buildResultX86 = build job: 'compile_dds_x86', parameters: [string(name: 'BRANCH', value: "${params.product_branch}"), string(name: 'GIT_SHA', value: ''), string(name: 'COMPILE_ARCH', value: 'x86_64'), string(name: 'JOB_NUMBER', value: "${params.jobs}")]
                            if (buildResultX86.resultIsBetterOrEqualTo('SUCCESS')) {
                                echo "Build x86 succeeded!"
                                // Save the job value for later use
                                env.JOB_X86 = buildResultX86.displayName
                            } else {
                                error "Build x86 failed!"
                            }
                        }
                    }
                }
                
                stage('compile arm'){
                    steps{
                        script{
                            def buildResultArm = build job: 'compile_dds_arm', parameters: [string(name: 'BRANCH', value: "${params.product_branch}"), string(name: 'GIT_SHA', value: ''), string(name: 'COMPILE_ARCH', value: 'arm64'), string(name: 'JOB_NUMBER', value: "${params.jobs}")]
                            if (buildResultArm.resultIsBetterOrEqualTo('SUCCESS')) {
                                echo "Build arm succeeded!"
                                // Save the job value for later use
                                env.JOB_ARM = buildResultArm.displayName
                            } else {
                                error "Build arm failed!"
                            }
                        }
                    }
                }
            }
        }
        
        stage('exec test') {
            when {
                expression { params.exec_test == true }
            }
            
            parallel {
                stage('test x86'){
                    steps{
                        script{
                            def testResultX86 = build job: 'test_master_dds_x86', parameters: [string(name: 'git_repository', value: "${params.test_repository}"), string(name: 'branch', value: "${params.test_branch}"), string(name: 'host_arch', value: 'x86_64')]
                            if (testResultX86.resultIsBetterOrEqualTo('SUCCESS')) {
                                echo "Build image succeeded!"
                            } else {
                                error "Build image failed!"
                            }
                        }
                    }
                }
                stage('test arm'){
                    steps{
                        script{
                            def testResultArm = build job: 'test_master_dds_arm', parameters: [string(name: 'git_repository', value: "${params.test_repository}"), string(name: 'branch', value: "${params.test_branch}"), string(name: 'host_arch', value: 'aarch64')]
                            if (testResultArm.resultIsBetterOrEqualTo('SUCCESS')) {
                                echo "Build image succeeded!"
                            } else {
                                error "Build image failed!"
                            }
                        }
                    }
                }
            }
        }
        
        stage('build images') {
            when {
                expression { params.public_image == true }
            }
            
            steps {
                script{
                    def buildResult = build job: 'build_dds_image', parameters: [string(name: 'git_repository', value: "${params.product_repository}"), string(name: 'branch', value: "${params.product_branch}"), string(name: 'docker_repository', value: '192.168.20.106'), string(name: 'jobs', value: "${params.jobs}"), string(name: 'image_name', value: 'sequoiadb-dds'), string(name: 'docker_user', value: 'wangwenjing'), booleanParam(name: 'is_release', value: true), string(name: 'project_name', value: 'sequoiadb')]
                    if (buildResult.resultIsBetterOrEqualTo('SUCCESS')) {
                        echo "Build image succeeded!"
                    }else {
                        error "Build image failed!"
                    }
                }
            }
        }
        
        stage('copy version') {
            steps {
                script{
                    def buildUrlX86 = env.BUILD_URL + env.JOB_X86
                    echo "Build URL for x86: ${buildUrlX86}"
                    sh "rm -rf release"
                    copyArtifacts filter: '**/*.run,**/*.run.sha256,**/VERSION,**/*-linux_x86_64.tar.gz', fingerprintArtifacts: true, flatten: true, projectName: 'compile_dds_x86', selector: lastSuccessful(), target: 'release/x86_64'
                    copyArtifacts filter: '**/*.run,**/*.run.sha256,**/*-linux_aarch64.tar.gz', fingerprintArtifacts: true, flatten: true, projectName: 'compile_dds_arm', selector: lastSuccessful(), target: 'release/aarch64'
                }
            }
        }
        
        stage('archive version') {
            steps {
                script {
                    checkout scmGit(branches: [[name: '*/main']], extensions: [[$class: 'SparseCheckoutPaths', sparseCheckoutPaths: [[path: 'shared_utils']]], [$class: 'RelativeTargetDirectory', relativeTargetDir: 'misc']], userRemoteConfigs: [[url: "$CI_GIT_URL"]])
                    sh "cp misc/shared_utils/public/script/archiveversion.sh $WORKSPACE"
                    sh "./archiveversion.sh -p sequoiadds "
                }
            }
        }
    }
}

