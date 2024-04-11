pipeline {
    agent {label 'compile_x86_dds'}
    
    parameters {
        string(name: 'repository', defaultValue: 'http://gitlab.sequoiadb.com/sequoiadb/dds-backup.git', description: '')
        string(name: 'branch', defaultValue: 'main', description: '')
        booleanParam(name: 'is_enterprise', defaultValue: false, description: '') 
    }
    
    options {
        disableConcurrentBuilds()
        timestamps()
    }
    triggers { cron('H 5 * * *')}
    stages {
        stage('pull code') {
            steps {
                cleanWs()
                checkout scmGit(branches: [[name: "${params.branch}"]], extensions: [], userRemoteConfigs: [[url: "${params.repository}"]])
            }
        }
        
        stage('make') {
            steps {
                script{
                    if (params.is_enterprise == true){
                        sh "python3 build.py -e"
                    }else{
                        sh "python3 build.py"
                    }
                }
            }
        }
    }
    
    post{
        success{
            archiveArtifacts allowEmptyArchive: true, artifacts: '**/*.tar.gz', followSymlinks: false
        }
    }
}

