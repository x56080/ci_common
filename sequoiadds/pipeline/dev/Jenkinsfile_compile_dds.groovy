pipeline {
    agent { label 'master' } 
    parameters {
        string(name: 'BRANCH', description: 'Git Branch',defaultValue: 'main')
        string(name: 'git_repository', description: 'Git Repository URL',defaultValue: 'http://gitlab.sequoiadb.com/sequoiadb/dds.git')
        string(name: 'JOB_NUMBER', defaultValue: '16')
        booleanParam(name: 'BUILD_POC', defaultValue: false, description: 'set this parameter to build POC run package')
    }
    
    options {
        disableConcurrentBuilds()
        timestamps()
        buildDiscarder(logRotator(artifactDaysToKeepStr: '7', artifactNumToKeepStr: '7', daysToKeepStr: '7', numToKeepStr: '7'))
    }

    triggers {cron('H 23 * * *')}

    environment {
        git_repository ="${params.git_repository}"
        host_arch ="${params.COMPILE_ARCH}"
        branch = "${params.BRANCH}"
        jobs = "${params.JOB_NUMBER}"
    }

    stages {
        
        stage('Build') {
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
                    if(params.BUILD_POC){
                        sh "cd dds; python3 package.py --output-path ./release --tools-path ../dds_tools_archive --pigz --pack-dbg --poc"
                    }else{
                        sh "cd dds; python3 package.py --output-path ./release --tools-path ../dds_tools_archive --pigz --pack-dbg"
                    }
                }
            }
            post {
                success {
                    // 只有在构建成功时执行以下归档操作
                    archiveArtifacts artifacts: 'dds/release/*.tar.gz', allowEmptyArchive: true
                }
            }
            
        }
        
        stage('Pack'){
            agent {
                label 'build_run'
            }
        
            steps {
                script {
                    cleanWs()
                    // Get some code from a GitHub repository
                    git 'http://gitlab.sequoiadb.com/sequoiadb/build_run.git'
                    unarchive mapping: ['dds/release/*.tar.gz':'./']
                    if(params.BUILD_POC){
                        sh "bash callbuildpackage.sh -p sequoiadds -t dds/release/sequoiadb-dds.tar.gz -u ${git_repository} -b ${branch} -a ${host_arch} -s --poc"
                    }else{
                        sh "bash callbuildpackage.sh -p sequoiadds -t dds/release/sequoiadb-dds.tar.gz -u ${git_repository} -b ${branch} -a ${host_arch} -s"
                    }
                }
            }
            
            post {
                success {
                    archiveArtifacts 'release/*.run'
                    archiveArtifacts 'release/sequoiadds/VERSION'
                    archiveArtifacts 'release/*.run.sha256'
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
                        emailext body: '$DEFAULT_CONTENT', subject: '$DEFAULT_SUBJECT', to: '$DEFAULT_RECIPIENTS,wuyun@sequoiadb.com,heguoming@sequoiadb.com,liuyuchen@sequoiadb.com'
                    }
                }
            }
        }
    }
}