pipeline {
    agent { label 'master' }
    
    parameters {
        choice(name: 'BRANCH', choices: ['master','v3.4', 'v5.8'], description: '')
        choice(name: 'SQL_BRANCH', choices: ['','master','v3.4', 'v5.8'], description: '')
        string(name: 'GIT_SHA', defaultValue: '', description: '')
        booleanParam(name: 'EXECUTE_TEST', defaultValue: true, description: '')
        booleanParam(name: 'ARCHIVE', defaultValue: true, description: '')
        booleanParam(name: 'SKIP_MAKE', defaultValue: false, description: '')
    }
    environment {
        CI_GIT_URL = 'http://gitlab.sequoiadb.com/sequoiadb/ci/ci_common.git'
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

        stage('call sub project') {
            when {expression{params.SKIP_MAKE == false}}
            parallel {
                stage("make sequoiadb") {
                   steps {
                     build job: 'Publish_SequoiaDB_sub', parameters: [string(name: 'BRANCH', value: "${params.BRANCH}"), string(name: 'GIT_SHA', value: "${params.GIT_SHA}"), booleanParam(name: 'EXECUTE_TEST', value: "${params.EXECUTE_TEST}"), booleanParam(name: 'ARCHIVE', value: "${params.ARCHIVE}")]
                   }
                }
                stage("make MySQL") {
                   steps {
                       script{
                          if (params.SQL_BRANCH == ""){
                             build job: 'Publish_SequoiaSQL_MySQL', parameters: [string(name: 'BRANCH', value: "master"), string(name: 'COMPILE_SQL', value: 'mysql'), string(name: 'GIT_SHA', value: ''), string(name: 'SDB_DEPNAME', value: ''), booleanParam(name: 'EXECUTE_TEST', value: "${params.EXECUTE_TEST}")]
                         }
                      }
                   }
                }
                stage("make MariaDB") {
                   steps {
                     script{
                        if (params.SQL_BRANCH == "v3.4"){
                           build job: 'Publish_SequoiaSQL_MariaDB', parameters: [string(name: 'BRANCH', value: 'v3.4'), string(name: 'COMPILE_SQL', value: 'mariadb'), string(name: 'GIT_SHA', value: ''), string(name: 'SDB_DEPNAME', value: ''), booleanParam(name: 'EXECUTE_TEST', value: "${params.EXECUTE_TEST}")]
                       }
                     }
                   }
                }

            }
        }
    
        stage('archive version') {
            steps {
                script{
                   if (params.SQL_BRANCH 1= ""){
                      copyArtifacts filter: '**/*-linux_x86_64-*.run', fingerprintArtifacts: true, flatten: true, projectName: 'Publish_SequoiaDB_sub', selector: lastSuccessful(), target: '.'
                   }
                   copyArtifacts filter: '**/*-linux_x86_64-enterprise-*.run', fingerprintArtifacts: true, flatten: true, projectName: 'Publish_SequoiaDB_sub', selector: lastSuccessful(), target: '.'
                   checkout scmGit(branches: [[name: '*/main']], extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'misc'], [$class: 'SparseCheckoutPaths', sparseCheckoutPaths: [[path: 'shared_utils'], [path: 'sequoiadb/partofpublictar']]]], userRemoteConfigs: [[url: "$CI_GIT_URL"]])
                   sh "cp misc/shared_utils/public/script/archiveversion.sh $WORKSPACE"
                   sh "cp misc/sequoiadb/partofpublictar/* $WORKSPACE"
                   sh "./archiveversion.sh -p sequoiadb -b ${params.BRANCH}"
                }
            }
        }
    }
}

