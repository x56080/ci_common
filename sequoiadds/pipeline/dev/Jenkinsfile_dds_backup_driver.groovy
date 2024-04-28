pipeline {
    agent {label 'compile_x86_dds'}
    
    parameters {
        string(name: 'repository', defaultValue: 'http://gitlab.sequoiadb.com/sequoiadb/dds-backup-driver.git', description: '')
        string(name: 'branch', defaultValue: 'main', description: '')
        choice(name: 'mode', choices: ['package','deploy'], description: '')
        booleanParam(name: 'is_release', defaultValue: true, description: '')
    }
    
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
        failure {
         script{
            checkout scmGit(branches:[[name: "main"]],extensions:[[$class: 'RelativeTargetDirectory', relativeTargetDir: 'misc']],userRemoteConfigs:[[url:"http://gitlab.sequoiadb.com/sequoiadb/ci/ci_common.git"]])
            def csvContent=readCSV file: 'shared_utils/config/dev_groups.csv'
            def members=""
            csvContent.each { row ->
               def group = row[0]
               if (group == "dds_group"){
                  members = row[1];
               }
            }
            emailext body: '$DEFAULT_CONTENT', subject: '$DEFAULT_SUBJECT', to: '$DEFAULT_RECIPIENTS,${members}'
        }
      }
    }
}

