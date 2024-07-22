pipeline {
    agent {node 'compile_x86_1'}

    parameters {
        string(name: 'repository', defaultValue: 'http://gitlab.sequoiadb.com/sequoiadb/m2s.git', description: '')
        string(name: 'branch', defaultValue: 'main', description: '')
    }
    
    options {
        disableConcurrentBuilds()
        timestamps()
    }

    triggers {cron('H 14 * * *')}

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
                    sh "python3 build.py"
                }
            }
        }
    }
    
    post{
        success{
            archiveArtifacts allowEmptyArchive: true, artifacts: '**/*.tar.gz', followSymlinks: false
        }
        failure {
         script{
            checkout scmGit(branches:[[name: "main"]],extensions:[[$class: 'RelativeTargetDirectory', relativeTargetDir: 'misc']],userRemoteConfigs:[[url:"http://gitlab.sequoiadb.com/sequoiadb/ci/ci_common.git"]])
            def csvContent=readCSV file: 'misc/shared_utils/config/dev_groups.csv'
            def members=""
            csvContent.each { row ->
               def group = row[0]
               if (group == "dds_group" || group == "ci_group"){
                  if ( members != "" ){
                     members = members + ","
                  }
                  members = members + row[1];
               }
            }
            emailext body: '$DEFAULT_CONTENT', subject: '$DEFAULT_SUBJECT', to: "${members}"
        }
      }
    }
}

