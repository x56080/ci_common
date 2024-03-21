pipeline {
    agent {label 'compile_x86_3'}
    parameters {
        string(name: 'git_repository', defaultValue: 'http://gitlab.sequoiadb.com/sequoiadb/sdbconnectors.git', description: '')
        string(name: 'branch', defaultValue: 'master', description: '')
        choice(name: 'branch', choices: ['master','flink1.14'], description: '')
        booleanParam(name: 'is_release', defaultValue: 'false', description: '')
    }
    
    options {
        disableConcurrentBuilds()
        timestamps()
        buildDiscarder(logRotator(artifactDaysToKeepStr: '5', artifactNumToKeepStr: '5', daysToKeepStr: '5', numToKeepStr: '5'))
    }
    
    environment {
        COMPILE_ARGS="--type all"
    }
    triggers {cron("H 2 * * *")}

    stages {
        stage('set compile args'){
           steps {
               script {
                  if (params.branch != "master")
                  {
                       COMPILE_ARGS="--type flink"
                  }
               }
           }
        }
        stage('pull code') {
            steps {
                cleanWs()
                checkout scmGit(branches: [[name: "${params.branch}"]], extensions: [], userRemoteConfigs: [[url: "${params.git_repository}"]])
            }
        }
        
        stage('compile') {
            steps {
                script {
                    if (params.is_release){
                        sh "./compile.sh ${COMPILE_ARGS} --release"
                    }else{
                        sh "./compile.sh ${COMPILE_ARGS}"
                    }
                   
                }
            }
        }
        
        stage('archive') {
            steps {
                archiveArtifacts artifacts: 'build/*.jar', followSymlinks: false
            }
        }
    }
}
