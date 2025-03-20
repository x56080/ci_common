pipeline {
    agent {label 'test_sac_master'}
    parameters {
        string(name: 'dev_repository', defaultValue: 'http://gitlab.sequoiadb.com/sequoiadb/sac.git', description: '')
        string(name: 'test_repository', defaultValue: 'http://gitlab.sequoiadb.com/test/sac-auto-test', description: '')        
        string(name: 'test_branch', defaultValue: "${params.test_branch}", description: '')
        choice(name: 'host_arch', choices: ['x86_64','aarch64'], description: 'Select target architecture')
        choice(name: 'testtype', choices: ['runbase','runtest'], description: 'Select test type')
    }

    options {
        disableConcurrentBuilds()
        timestamps()
        buildDiscarder(logRotator(artifactDaysToKeepStr: '5', artifactNumToKeepStr: '7', daysToKeepStr: '7', numToKeepStr: '5'))
        ansiColor("xterm")
    }

    environment {
        PRODUCT_PACKAGE_LOCALPATH="/data/product_package"
        TEST_HOSTLIST="192.168.29.162,192.168.29.163,192.168.29.164,192.168.29.165,192.168.29.166"
        TARGET_DIR="sac/localbuild/package"
    }


    stages {
        stage('pull code') {
            steps {
                cleanWs()
                checkout scmGit(branches: [[name: "${params.dev_branch}"]], extensions: [checkoutOption(30),[$class: 'RelativeTargetDirectory', relativeTargetDir: 'sac']], userRemoteConfigs: [[url: "${params.dev_repository}"]])
            }
        }
        
        stage('copy package') {
            parallel{
                stage("copy product package"){
                    steps {
                        sh "cp -r ${PRODUCT_PACKAGE_LOCALPATH}/* ${TARGET_DIR}"
                    }
                }
                
                stage("copy sac package"){
                    steps{
                        script{
                            def runpackage="**/sequoiasac*-linux_x86_64-enterprise-installer.run"
                            def tarpackage="**/sequoiasac*-linux_x86_64-enterprise.tar.gz"
                            if (params.BuildId != ""){
                                copyArtifacts filter: "${runpackage}", fingerprintArtifacts: true, flatten: true, projectName: "${params.ProjectName}", selector: specific("${params.BuildId}"), target: "${TARGET_DIR}"
                                copyArtifacts filter: "${tarpackage}", fingerprintArtifacts: true, flatten: true, projectName: "${params.ProjectName}", selector: specific("${params.BuildId}"), target: "${TARGET_DIR}"
                            }else{
                                copyArtifacts filter: "${runpackage}", fingerprintArtifacts: true, flatten: true, projectName: "${params.ProjectName}", selector: lastSuccessful(), target: "${TARGET_DIR}"
                                copyArtifacts filter: "${tarpackage}", fingerprintArtifacts: true, flatten: true, projectName: "${params.ProjectName}", selector: lastSuccessful(), target: "${TARGET_DIR}"
                            }
                        }
                        
                    }
                }
            }
        }
        
        stage('chmod with package') {
            steps {
                sh "chmod u+x ${TARGET_DIR}/*.run"
            }
        }
        
        stage('install and deploy') {
            steps {
                echo "install and deploy"
                sh ". ~/.virtualenvs/env/bin/activate;cd sac; python localbuild.py --host ${TEST_HOSTLIST} --clean --install; deactivate"
            }
        }
        
        stage('exec test') {
            steps {
                script{
                   def branch="${params.test_branch}"
                   checkout scmGit(branches: [[name: "${branch}"]], extensions: [checkoutOption(30),[$class: 'RelativeTargetDirectory', relativeTargetDir: 'sac-auto-test']], userRemoteConfigs: [[url: "${params.test_repository}"]])
                   sh ". ~/.virtualenvs/env/bin/activate;cd sac; python localbuild.py --${params.testtype} --branch ${params.test_branch};deactivate"
                }
            }
        }
        
        stage('parse test report') {
            steps {
                script{
                    def reportdir="sac-auto-testcase/mochawesome-report/"
                    def files="mochawesome.html"
                    publishHTML([allowMissing: false, alwaysLinkToLastBuild: true, keepAll: true, reportDir: "${reportdir}", reportFiles: "${files}", reportName: 'HTML Report', reportTitles: '', useWrapperFileDirectly: true])
                }
            }
        }
    }
    
    post{
        success{
            script{
                def htmlFile = "sac-auto-testcase/mochawesome-report/mochawesome.html"
                def ret = sh returnStdout: true, script: "grep -o 'failures&quot;:[0-9]\\+' $htmlFile |sed 's/.*://'"
       ret = ret.trim()
       println "ret value: ${ret}"
                if (ret != 0 ){
                    currentBuild.result = "UNSTABLE"
                    //def src_basedir="sac-auto-testcase/testcases/story/Cypress"
                    //def backup_dir="${src_basedir}/backup_screenshots"
                    
                    //sh "mkdir -p $backup_dir; cp -r ${src_basedir}/cypress-visual-screenshots/diff ${backup_dir}; cp -r ${src_basedir}/cypress/screenshots/ //${backup_dir}; cp -r ${src_basedir}/cypress/downloads ${backup_dir}"
                    //tar archive: true, compress: true, defaultExcludes: false, dir: "${backup_dir}", exclude: '', file: 'backup_screenshots.tar.gz', glob: '', //overwrite: true
                }
            }
        }
        
        failure {
    		    emailext body: '$DEFAULT_CONTENT', subject: '$DEFAULT_SUBJECT', to: '$DEFAULT_RECIPIENTS,yinxiaoxia@sequoiadb.com'
    	  }
    }
}
