pipeline {
    agent {label 'master'}
    stages {
        stage('compile') {
            when {expression { params.skip_compile == false }}
            steps {
                build job: 'compile_master_sequoiasac', parameters: [string(name: 'BRANCH', value: "${params.BRANCH}"), string(name: 'GIT_SHA', value: "${params.GIT_SHA}")], wait: true
            }
        }
        
        stage('test') {
            steps {
                build job: 'test_master_sequoiasac', parameters: [string(name: 'dev_repository', value: "${params.dev_repository}"), string(name: 'dev_branch', value: "${params.dev_branch}"), string(name: 'test_repository', value: "${params.test_repository}"), string(name: 'test_branch', value: "${params.test_branch}"), string(name: 'testtype', value: "${params.testtype}"), string(name: 'host_arch', value: "${params.host_arch}")], wait: true
            }
        }
    }
}
