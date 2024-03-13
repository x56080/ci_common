pipeline {
    agent {label 'master'}
    parameters {
        choice(name: 'BRANCH', choices: ['master','hengfeng'], description: '')
        string(name: 'GIT_SHA', defaultValue: '', description: '')
        booleanParam(name: 'EXECUTE_TEST', defaultValue: true, description: '')
        booleanParam(name: 'SKIP_MAKE', defaultValue: false, description: '')
    }
    options {
        disableConcurrentBuilds()
        timestamps()
        buildDiscarder(logRotator(artifactDaysToKeepStr: '5', artifactNumToKeepStr: '5', daysToKeepStr: '5', numToKeepStr: '5'))
    }
    environment {
        CI_GIT_URL = 'http://gitlab.sequoiadb.com/sequoiadb/ci/ci_common.git'
    }
    

    stages {
        stage('call sub project') {
            when { expression {params.SKIP_MAKE == false} }
            steps {
               build job: 'Publish_SequoiaCM_sub', parameters: [string(name: 'BRANCH', value: "${params.BRANCH}"), string(name: 'GIT_SHA', value: "${params.GIT_SHA}"), booleanParam(name: 'EXECUTE_TEST', value: "${params.EXECUTE_TEST}"), string(name: 'TEST_SITE', value: 'fourSite'), string(name: 'TEST_PROJECT', value: 'all')]
            }
        }
        
        stage('mv archive version') {
            steps {
                checkout scmGit(branches: [[name: '*/main']], extensions: [[$class: 'SparseCheckoutPaths', sparseCheckoutPaths: [[path: 'shared_utils']]], [$class: 'RelativeTargetDirectory', relativeTargetDir: 'misc']], userRemoteConfigs: [[url: "$CI_GIT_URL"]])
                sh "cp misc/shared_utils/public/script/archiveversion.sh $WORKSPACE"
                sh "./archiveversion.sh -p sequoiascm -b ${params.BRANCH}"
            }
        }
    }
}
}
