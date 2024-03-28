pipeline {
    agent { label 'master' }
    
    parameters {
        choice(name: 'BRANCH', choices: ['master','v3.4', 'v5.8'], description: '')
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
            steps {
                build job: 'Publish_SequoiaDB_sub', parameters: [string(name: 'BRANCH', value: "${params.BRANCH}"), string(name: 'GIT_SHA', value: "${params.GIT_SHA}"), booleanParam(name: 'EXECUTE_TEST', value: "${params.EXECUTE_TEST}"), booleanParam(name: 'ARCHIVE', value: "${params.ARCHIVE}")]
            }
        }
    
        stage('archive version') {
            steps {
                checkout scmGit(branches: [[name: '*/main']], extensions: [[$class: 'SparseCheckoutPaths', sparseCheckoutPaths: [[path: 'shared_utils']]], [$class: 'RelativeTargetDirectory', relativeTargetDir: 'misc']], userRemoteConfigs: [[url: "$CI_GIT_URL"]])
                sh "cp misc/shared_utils/public/script/archiveversion.sh $WORKSPACE"
                sh "./archiveversion.sh -p sequoiadb -b ${params.BRANCH}"
            }
        }
    }
}

