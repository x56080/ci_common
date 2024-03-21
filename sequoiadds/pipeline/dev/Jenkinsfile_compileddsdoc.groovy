pipeline {
    agent { label 'compile_x86_dds' }
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

    environment {
       BRANCH="main"
    }

    stages{
        stage("select branch"){
            steps {
               script{
                   if ( JOB_NAME =~ "dev" ){
                      BRANCH="dds_doc"
                   }
               }
            }
        }

        stage("pull code"){
            steps{
                cleanWs()
                checkout scmGit(branches:[[name: "${BRANCH}"]],extensions:[[$class: 'RelativeTargetDirectory', relativeTargetDir: 'dds-doc']],userRemoteConfigs:[[url:"${params.git_rep}"]])
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
                    label 'compile_x86_dds'
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
    		emailext body: '$DEFAULT_CONTENT', subject: '$DEFAULT_SUBJECT', to: '$DEFAULT_RECIPIENTS,liuyuchen@sequoiadb.com,zhouhongye@sequoiadb.com'
    	}
    }
}
