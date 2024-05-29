pipeline {
    agent { label 'compile_x86_dds' } 
    parameters {
        string(name: 'BRANCH', description: 'Git Branch',defaultValue: 'main')
        string(name: 'git_repository', description: 'Git Repository URL',defaultValue: 'http://gitlab.sequoiadb.com/sequoiadb/dds.git')
        string(name: 'JOB_NUMBER', defaultValue: '16')
    }
    
    options {
        disableConcurrentBuilds()
        timestamps()
        buildDiscarder(logRotator(artifactDaysToKeepStr: '5', artifactNumToKeepStr: '5', daysToKeepStr: '5', numToKeepStr: '5'))
    }

    environment {
        git_repository ="${params.git_repository}"
        host_arch ="${params.COMPILE_ARCH}"
        branch = "${params.BRANCH}"
        jobs = "${params.JOB_NUMBER}"
    }

    stages {
        
        stage('Pull code and make') {
            agent {
               label "${params.agent}"
            }
            steps {
                script {
                    cleanWs()
                    checkout scmGit(branches: [[name: "${branch}"]],  extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'dds']], userRemoteConfigs: [[url: 'http://gitlab.sequoiadb.com/sequoiadb/dds.git']])
                    checkout scmGit(branches: [[name: "${branch}"]], extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'dds_tools_archive']], userRemoteConfigs: [[url: 'http://gitlab.sequoiadb.com/sequoiadb/dds-tools-archive.git']])
                    sh "source /opt/mongodbtoolchain/py3env/bin/activate;cd dds; scl enable devtoolset-9 'python3 build.py install-devcore --separate-debug -j $jobs';deactivate"
                    sh "cd dds; git fetch --tags $git_repository"
                    sh "cd dds; python3 package.py --output-path ./release --tools-path ../dds_tools_archive --pigz --pack-dbg"
                }
            }
            post {
                success {
                    // 只有在构建成功时执行以下归档操作
                    archiveArtifacts artifacts: 'dds/release/*.tar.gz', allowEmptyArchive: true
                }
            }
            
        }
        
        stage('package'){
            agent {
                label 'build_run'
            }
        
            steps {
                cleanWs()
                // Get some code from a GitHub repository
                git 'http://gitlab.sequoiadb.com/sequoiadb/build_run.git'
                unarchive mapping: ['dds/release/*.tar.gz':'./']
                sh "bash callbuildpackage.sh -p sequoiadds -t dds/release/sequoiadb-dds.tar.gz -u ${git_repository} -b ${branch} -a ${host_arch} -s "
            }
            
            post {
                success {
                    archiveArtifacts 'release/*.run'
                    archiveArtifacts 'release/sequoiadds/VERSION'
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
    }
}
