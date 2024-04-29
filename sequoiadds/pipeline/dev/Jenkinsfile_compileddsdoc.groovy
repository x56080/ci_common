pipeline {
    agent { label 'compile_dds_x86_doc' }
    options {
        disableConcurrentBuilds()
        timestamps()
    }

    parameters{
        string(name: 'git_rep', defaultValue: 'http://gitlab.sequoiadb.com/sequoiadb/dds-doc.git', description: '')
        choice(name: 'branch', choices: ['main','dds_doc'], description: 'Select target architecture')
        string(name: 'docker_image', defaultValue: '192.168.20.106/sequoiadb/dds-doc-builder:0.3.0', description: '')
        string(name: 'build_script_arguments', defaultValue: '--pdf --pdf-name-prefix sequoiadb-dds-manual', description: 'arguments pass to build.py')
    }

    stages{
        stage("set condition"){
            steps {
               script{
                   def sleepTimeLen = (int)(Math.random() * 60) +1
                   sleep time: "$sleepTimeLen"
                   if (currentBuild.getBuildCauses()[0].userId  || currentBuild.getBuildCauses()[0].upstreamProject){
                        env.BRANCH = params.branch
                   }else{
                       if (ref != ""){
                           env.BRANCH = ref.tokenize('/').last()
                       }else{
                           env.BRANCH = params.branch
                       }
                   }
                   
                   echo "${BRANCH}"
                   if ( JOB_NAME =~ "dev" && env.BRANCH == "dds_doc" ){
                       env.EXEC_COMPILE = "true"
                   }
                   
                   if (JOB_NAME == "compile_dds_doc" && env.BRANCH == "main"){
                       env.EXEC_COMPILE = "true"
                   }
               }
            }
        }

        stage("pull code"){
            when {
                environment name:'EXEC_COMPILE', value: 'true'
            }
            steps{
                cleanWs()
                checkout scmGit(branches:[[name: "${env.BRANCH}"]],extensions:[[$class: 'RelativeTargetDirectory', relativeTargetDir: 'dds-doc']],userRemoteConfigs:[[url:"${params.git_rep}"]])
            }
        }

        stage("pull image"){
            when {
                environment name:'EXEC_COMPILE', value: 'true'
            }
            steps{
                sh "docker pull ${params.docker_image}"
            }
        }

        stage("build doc"){
            when {
                environment name:'EXEC_COMPILE', value: 'true'
            }
            agent{
                docker{
                    image "${params.docker_image}"
                    label 'compile_dds_x86_doc'
                    args "--entrypoint= -v $WORKSPACE/dds-doc:/book"
                }
            }
            steps{
            sh "python3 /usr/src/build.py /book ${build_script_arguments}"
          }
        }
    }

    post{
      success{
        archiveArtifacts allowEmptyArchive: true, artifacts: 'dds-doc/book/*.pdf'
      }

      failure {
         script{
            checkout scmGit(branches:[[name: "main"]],extensions:[[$class: 'RelativeTargetDirectory', relativeTargetDir: 'misc']],userRemoteConfigs:[[url:"http://gitlab.sequoiadb.com/sequoiadb/ci/ci_common.git"]])
            def csvContent=readCSV file: 'misc/shared_utils/config/dev_groups.csv'
            def members=""
            csvContent.each { row ->
               def group = row[0]
               if (group == "dds_group"){
                  members = row[1];
               }
            }
            emailext body: '$DEFAULT_CONTENT', subject: '$DEFAULT_SUBJECT', to: "$DEFAULT_RECIPIENTS,${members}"
        }
      }
    }
}
