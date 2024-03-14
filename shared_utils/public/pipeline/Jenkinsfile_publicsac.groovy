pipeline {
    agent {label 'master'}
    parameters {
        choice(name: 'BRANCH', choices: ['master','3.4','3.6','4.0','4.0.1','4.2','4.2.1'], description: '')
        string(name: 'GIT_SHA', defaultValue: '', description: '')
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
        stage('call sub project') {
            when { expression { params.SKIP_MAKE == false }}
            steps {
                build job: 'Publish_sequoiaSAC_sub', parameters: [string(name: 'BRANCH', value: "${params.BRANCH}"), string(name: 'GIT_SHA', value: "${params.GIT_SHA}")]
            }
        }
        
        stage('mv version') {
            steps {
                checkout scmGit(branches: [[name: '*/main']], extensions: [[$class: 'SparseCheckoutPaths', sparseCheckoutPaths: [[path: 'shared_utils']]], [$class: 'RelativeTargetDirectory', relativeTargetDir: 'misc']], userRemoteConfigs: [[url: "$CI_GIT_URL"]])
                sh "cp misc/shared_utils/public/script/archiveversion.sh $WORKSPACE"
                sh "./archiveversion.sh -p sequoiasac -b \"${params.BRANCH}\""
            }
        }
    }
}

