pipeline {
    agent {
        label 'test_sac'
    }
    
    parameters {
        string(name: 'dev_repository', defaultValue: 'http://gitlab.sequoiadb.com/sequoiadb/sac.git', description: '')
        string(name: 'dev_branch', defaultValue: 'master', description: '')
        string(name: 'test_repository', defaultValue: 'http://gitlab.sequoiadb.com/test/sac-auto-test', description: '')
        string(name: 'test_branch', defaultValue: 'master', description: '')
        choice(name: 'host_arch', choices: ['x86_64','aarch64'], description: 'Select target architecture')
    }

    environment {
        debug = false
        PRODUCT_PACKAGE_LOCALPATH=“/data/product_package”
        TEST_HOSTLIST="192.168.29.24,192.168.29.104,192.168.29.154,192.168.29.98,192.168.29.21"
    }
    
    options {
        disableConcurrentBuilds()
        timestamps()
        buildDiscarder(logRotator(artifactDaysToKeepStr: '5', artifactNumToKeepStr: '7', daysToKeepStr: '7', numToKeepStr: '5'))
        ansiColor("xterm")
    }
    
    
    stages {
        stage('pull code') {
            when {
                environment name: 'debug', value: 'false'
            }
            
            steps {
                cleanWs()
                checkout scmGit(branches: [[name: "${params.dev_branch}"]], extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'sac']], userRemoteConfigs: [[url: "${params.dev_repository}"]])
            }
        }
        
        stage('copy package') {
            when {
                environment name: 'debug', value: 'false'
            }
            
            parallel{
                stage("copy product package"){
                    steps {
                        sh 'cp -r ${PRODUCT_PACKAGE_LOCALPATH}/*.run sac/localbuild/package'
                    }
                }
                
                stage("copy sac package"){
                    steps{
                        copyArtifacts filter: '**/sequoiasac*-linux_x86_64-enterprise-installer.run', fingerprintArtifacts: true, flatten: true, projectName: 'compile_master_sequoiasac', selector: lastSuccessful(), target: 'sac/localbuild/package'
                        copyArtifacts filter: '**/sequoiasac*-linux_x86_64-enterprise.tar.gz', fingerprintArtifacts: true, flatten: true, projectName: 'compile_master_sequoiasac', selector: lastSuccessful(), target: 'sac/localbuild/package'
                    }
                }
            }
        }
        
        stage('chmod with package') {
            steps {
                sh 'chmod u+x sac/localbuild/package/*.run'
            }
        }
        
        stage('install and deploy') {
            steps {
                sh ". ~/.virtualenvs/env/bin/activate;cd sac; python localbuild.py --host ${TEST_HOSTLIST}  --clean --install; deactivate"
            }
        }
        
        stage('exec test') {
            steps {
                checkout scmGit(branches: [[name: "${params.test_branch}"]], extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'sac-auto-test']], userRemoteConfigs: [[credentialsId: 'dab86296-bf9a-4eaa-969c-43935a154dbf', url: "${params.test_repository}"]])
                sh ". ~/.virtualenvs/env/bin/activate;cd sac; python localbuild.py --runtest;deactivate"
            }
        }
        
        stage('parse test report') {
            steps {
                publishHTML([allowMissing: false, alwaysLinkToLastBuild: true, keepAll: true, reportDir: 'sac-auto-testcase/testcases/story/Cypress/mochawesome-report/', reportFiles: '_all-test-result.html', reportName: 'HTML Report', reportTitles: '', useWrapperFileDirectly: true])
            }
        }
    }
    
    post{
        success{
            script{
                def htmlFile = "sac-auto-testcase/testcases/story/Cypress/mochawesome-report/_all-test-result.html"
                def ret = sh returnStdout: true, script: "grep '失败用例:' $htmlFile |sed 's/<[^>]*>//g'|awk -F ':' '{print \$2}'"
                echo "$ret"
                if ( ret != "0"){
                    currentBuild.result = "UNSTABLE"
                    tar archive: true, compress: true, defaultExcludes: false, dir: 'sac-auto-testcase/testcases/story/Cypress/cypress/storyPreData', exclude: '', file: 'toryPreData.tar.gz', glob: '', overwrite: true
                }
            }
        }
        
        failure {
    		emailext body: '$DEFAULT_CONTENT', subject: '$DEFAULT_SUBJECT', to: '$DEFAULT_RECIPIENTS,yinxiaoxia@sequoiadb.com'
    	}

    }
}
