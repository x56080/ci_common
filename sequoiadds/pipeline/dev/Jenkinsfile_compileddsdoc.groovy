pipeline {
    agent { label 'compile_x86_dds' }
    options {
        disableConcurrentBuilds()
        timestamps()
    }

    parameters{
        string(name: 'git_rep', defaultValue: 'http://gitlab.sequoiadb.com/sequoiadb/dds-doc.git', description: '')
        string(name: 'branch', defaultValue: 'main', description: '')
        string(name: 'docker_rep', defaultValue: '192.168.20.106')
        string(name: 'docker_image', defaultValue: '192.168.20.106/sequoiadb/dds-doc-builder:0.2.0', description: '')
        string(name: 'docker_user', defaultValue: 'liuyuchen', description: '')
        string(name: 'docker_passwd', defaultValue: 'liuyc_2021', description: '')
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
        		sh "docker login ${params.docker_rep} -u ${params.docker_user} -p ${params.docker_passwd}"
        		sh "docker pull ${params.docker_image}"
        	}
        }

        stage("build doc"){
            agent{
                docker{
                    image "${params.docker_image}"
                    label 'compile_x86_dds'
                    args "-v $WORKSPACE/dds-doc:/opt/src/dds-doc"
                }
            }
        	steps{
        		sh "python3 /usr/src/build.py /opt/src/dds-doc"
        	}
        }
    }

    post{
    	success{
    		archiveArtifacts allowEmptyArchive: true, artifacts: 'dds-doc/site/*.pdf'
    	}

    	failure {
    		emailext body: '$DEFAULT_CONTENT', subject: '$DEFAULT_SUBJECT', to: '$DEFAULT_RECIPIENTS,liuyuchen@sequoiadb.com'
    	}
    }
}
