pipeline {
    agent { label 'master' }
    options {
        disableConcurrentBuilds()
        timestamps()
        buildDiscarder(logRotator(artifactDaysToKeepStr: '5', artifactNumToKeepStr: '5', daysToKeepStr: '5', numToKeepStr: '5'))
    }

    stages {
        stage('check and clear disk space') {
            steps {
                checkout scmGit(branches: [[name: '*/main']], extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'misc'], [$class: 'SparseCheckoutPaths', sparseCheckoutPaths: [[path: 'shared_utils/script']]]], userRemoteConfigs: [[url: 'http://gitlab.sequoiadb.com/sequoiadb/ci/ci_common.git']])
                sh 'cd misc/shared_utils/script; ./checkandclear.sh'
            }
        }
    }
}
