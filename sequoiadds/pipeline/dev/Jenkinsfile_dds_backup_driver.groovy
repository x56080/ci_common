pipeline {
    agent {label 'compile_x86_dds'}
    
    parameters {
        string(name: 'repository', defaultValue: 'http://gitlab.sequoiadb.com/sequoiadb/dds-backup-driver.git', description: '')
        string(name: 'branch', defaultValue: 'main', description: '')
        choice(name: 'mode', choices: ['package','deploy'], description: '')
        booleanParam(name: 'is_release', defaultValue: true, description: '')
    }
    
    triggers { cron('H 6 * * *')}
    options {
        disableConcurrentBuilds()
        timestamps()
    }
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
                    if (params.is_release){
                        sh "bash compile.sh --mode ${params.mode} --release"
                    }else{
                        sh "bash compile.sh --mode ${params.mode}"
                    }
                }
            }
        }
    }
    
    post{
        success{
            archiveArtifacts allowEmptyArchive: true, artifacts: '**/*.jar', followSymlinks: false
        }
    }
}

