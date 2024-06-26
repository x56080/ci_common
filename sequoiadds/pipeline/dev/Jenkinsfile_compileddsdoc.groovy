pipeline {
    agent { label 'compile_dds_x86_doc' }
    options {
        disableConcurrentBuilds()
        timestamps()
    }

    parameters{
        string(name: 'git_rep', defaultValue: 'http://gitlab.sequoiadb.com/sequoiadb/dds-doc.git', description: '')
        string(name: 'branch', defaultValue: 'main', description: 'Select target architecture')
        string(name: 'docker_image', defaultValue: '192.168.20.106/sequoiadb/dds-doc-builder:0.3.0', description: '')
        string(name: 'build_script_arguments', defaultValue: '--pdf --pdf-name-prefix sequoiadb-dds-manual', description: 'arguments pass to build.py')
    }

    stages{
        stage("pull code"){
            steps{
                cleanWs()
                checkout scmGit(branches:[[name: "${params.branch}"]],extensions:[[$class: 'RelativeTargetDirectory', relativeTargetDir: 'dds-doc']],userRemoteConfigs:[[url:"${params.git_rep}"]])
            }
        }

        stage("pull image"){
            steps{
                sh "docker pull ${params.docker_image}"
            }
        }

        stage("build doc"){
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
        emailext body: '$DEFAULT_CONTENT', subject: '$DEFAULT_SUBJECT', to: '$DEFAULT_RECIPIENTS,wuyun@sequoiadb.com,heguoming@sequoiadb.com,zhangyansheng@sequoiadb.com,yangqincheng@sequoiadb.com,niezhibiao@sequoiadb.com'
      }
    }
}
