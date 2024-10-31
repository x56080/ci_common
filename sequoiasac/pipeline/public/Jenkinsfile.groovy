pipeline {
    agent {label 'master'}
    parameters {
        choice(name: 'BRANCH', description: '')
        string(name: 'GIT_SHA', defaultValue: '', description: '')
        booleanParam(name: 'SKIP_COMPILE', defaultValue: false, description: '')
        booleanParam(name: 'SKIP_TEST', defaultValue: false, description: '')
    }
    
    environment {
        CI_GIT_URL = 'http://gitlab.sequoiadb.com/sequoiadb/ci/ci_common.git'
        //CI_GIT_URL = 'http://gitlab.sequoiadb.com/wangwenjing/ci_common.git'
        
    }
    
    options {
        disableConcurrentBuilds()
        timestamps()
        buildDiscarder(logRotator(artifactDaysToKeepStr: '5', artifactNumToKeepStr: '5', daysToKeepStr: '5', numToKeepStr: '5'))
    }

    stages {
        stage("cleanWS"){
           steps{
             cleanWs()
           }
        }
        stage('compile') {
            when { expression { params.SKIP_COMPILE == false }}
            steps {
                build job: 'compile_master_sequoiasac', parameters: [string(name: 'BRANCH', value: "${params.BRANCH}"), string(name: 'GIT_SHA', value: "${params.GIT_SHA}")]
            }
        }

        stage('test') {
            when { expression { params.SKIP_TEST == false }}
            steps {
                build job: 'test_master_sequoiasac', parameters: [string(name: 'dev_repository', value: "${params.dev_repository}"), string(name: 'dev_branch', value: "${params.dev_branch}"), string(name: 'test_repository', value: "${params.test_repository}"), string(name: 'test_branch', value: "${params.test_branch}"), string(name: 'testtype', value: "${params.testtype}"), string(name: 'host_arch', value: "${params.host_arch}"),string(name: 'BuildId', value: "${params.BuildId}")], wait: true
            }
        }

        stage('archive') {
            steps {
                script{
                    def archive_path = "$JENKINS_HOME/jobs/compile_master_sequoiasac/builds"
                    checkout scmGit(branches: [[name: '*/CI-2906']], extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'misc']], userRemoteConfigs: [[url: "$CI_GIT_URL"]])
                    if (params.BuildId != ""){
                       sh "sudo bash misc/sequoiasac/script/archive_sac.sh -p ${archive_path} -b ${params.BRANCH} -n ${params.BuildId} "
                    }
                    else{
                        sh "sudo bash misc/sequoiasac/script/archive_sac.sh -p ${archive_path} -b ${params.BRANCH}"
                    }
                }
            }
        }
        
        stage('mv version') {
            steps {
                sh "cp misc/shared_utils/public/script/archiveversion.sh $WORKSPACE"
                sh "./archiveversion.sh -p sequoiasac -b \"${params.BRANCH}\""
            }
        }
    }
}
