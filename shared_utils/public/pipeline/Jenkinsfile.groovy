pipeline {
    agent {label 'upload'}
    parameters {
        choice(name: 'product', choices: ['SequoiaDDS','SequoiaSAC','SequoiaDB','SequoiaCM','cc','SequoiaSQL','m2s', 'Connector', 'dds_diagnostic'], description: '')
        string(name: 'version', defaultValue: '3.4.4', description: '')
    }
    
    options {
        disableConcurrentBuilds()
        timestamps()
        buildDiscarder(logRotator(artifactDaysToKeepStr: '5', artifactNumToKeepStr: '5', daysToKeepStr: '5', numToKeepStr: '5'))
    }

    stages {
        stage('prepare env'){
            environment{
                CI_GIT_URL = "http://gitlab.sequoiadb.com/sequoiadb/ci/ci_common.git"
            }
            steps{    
                cleanWs()
                checkout scmGit(branches: [[name: '*/main']], extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'misc'], [$class: 'SparseCheckoutPaths', sparseCheckoutPaths: [[path: 'shared_utils/public']]]], userRemoteConfigs: [[url:"${CI_GIT_URL}"]])
                bat '''copy misc\\shared_utils\\public\\tools\\*.* .
                      copy misc\\shared_utils\\public\\script\\*.ps1 .
                      copy misc\\shared_utils\\public\\script\\*.py .'''
            }
        }
        stage('copy version') {
            steps {
                script{
                    def scriptExit = bat returnStatus: true, script: "powershell.exe -File .\\copyversion.ps1 -productName \"${params.product}\" -version \"${params.version}\" -targetPath $WORKSPACE"
                    echo "${scriptExit}"
                    if (scriptExit != 0){
                        error 'copy version failed!'
                    }
                }
            }
        }
        stage('Approval') {
            steps {
                timeout(time: 600, unit: 'SECONDS') {
                    input message: "是否发布${params.product} ${params.version}版本？", ok: 'yes', submitter: 'wangwenjing'
                }
            }
        }
        
        stage('upload version') {
            steps {
                script{
                    def scriptExit = bat returnStatus: true, script: "powershell.exe -File .\\upload.ps1 -productName \"${params.product}\" -version \"${params.version}\" -srcBasePath $WORKSPACE"
                    if (scriptExit != 0){
                        error 'upload version failed!'
                    }
                }
            }
        }
    }
}
