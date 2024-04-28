pipeline {
    agent {label 'test_dds' }

    parameters {
        string(name: 'git_repository', defaultValue: 'http://192.168.20.200/sequoiadb/dds-test.git', description: '')
        string(name: 'branch', defaultValue: 'main', description: '')
        string(name: 'hostlist', defaultValue: "192.168.24.63,192.168.24.65,192.168.24.66", description: '')
        string(name: 'rootpwd', defaultValue: "Sdb@123123", description: '')
        choice(name: 'host_arch', choices: ['aarch64','x86_64'], description: 'Select target architecture')
        booleanParam(name: 'debug', defaultValue: false, description: 'Whether it is a release build')
    }
    
    options {
        disableConcurrentBuilds()
        timestamps()
        buildDiscarder(logRotator(artifactDaysToKeepStr: '5', artifactNumToKeepStr: '7', daysToKeepStr: '7', numToKeepStr: '5'))
    }
    
    environment{
        EXECSTATUS = "normal"
        project_name = ""
        TEST_REPO = "http://gitlab.sequoiadb.com/sequoiadb/ci/ci_common.git"
        MONGO_URL1 ="mongodb://192.168.24.63:28017,192.168.24.65:28017,192.168.24.66:28017"
        MONGO_URL2 = "mongodb://192.168.24.63:27017,192.168.24.65:27017,192.168.24.66:27017"
    }
    
    stages {
        stage('prepare') {
            when { expression { params.debug == false }}
            steps {
                cleanWs()
                script{
                    if (params.host_arch == "aarch64"){
                        project_name = "compile_dds_arm"
                    }else{
                        project_name = "compile_dds_x86"
                    }
                }
                
                copyArtifacts filter: '**/*.run', fingerprintArtifacts: true, flatten: true, projectName: "${project_name}", selector: lastSuccessful(), target: '.'
                copyArtifacts filter: '**/*.tar.gz', fingerprintArtifacts: true, flatten: true, projectName: "compile_dds_driver_java", selector: lastSuccessful(), target: '.'
                
                sh 'tar -xzvf $(ls *.tar.gz)'
                sh 'mkdir ssl_test ; mkdir nossl_test; mkdir compress_test'
            }
        }
        
        stage('install dds') {
            when { expression { params.debug == false }}
            steps {
                checkout scmGit(branches: [[name: '*/main']], extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'misc']], userRemoteConfigs: [[url: "${TEST_REPO}"]])
                sh "cd misc/shared_utils/script; ./install_runpackage.sh --hostlist ${params.hostlist}  -p ${params.rootpwd} -a ${params.host_arch} -d ${WORKSPACE}"
            }
        }
        
        stage('deploy dds') {
            when { expression { params.debug == false }}
            steps {
                sh "cd misc/sequoiadds/script/deploy; ./deploy_dds.sh --hostlist ${params.hostlist}  -u sdbadmin -p Admin@1024 -d /ssd/sequoiadds -t java"
                sh "cd misc/sequoiadds/script/deploy; ./deploy_dds.sh --hostlist ${params.hostlist}  -u sdbadmin -p Admin@1024 -d /ssd/sequoiadds -t java -k"
            }
        }
        
        stage('config cluster ssl') {
            when { expression { params.debug == false }}
            steps {
                sh "cd misc/sequoiadds/script/deploy; ./changeclusterconfig.sh -u sdbadmin -p Admin@1024"
            }
        }

        stage("exec nossl test"){
            when { expression { params.debug == false }}
            agent {
                docker {
                    image 'java_driver_test_exec'
                    label 'test_dds' 
                    args "-v $workspace/nossl_test:/dds -v $workspace/sequoiadb-dds-driver:/dds-java"
                }
            }
            
            steps {
                script{
                    sh returnStatus: true, script: "/script/run_driver_test.sh -d /dds -g ${params.git_repository} -m ${MONGO_URL1} -r java -b ${params.branch} -p /dds-java"
                }
            }            
        }
        
        stage('parallel exec driver test'){
            when { expression { params.debug == false }}
            parallel {
                stage("exec ssl test"){
                    agent {
                        docker {
                            image 'java_driver_test_exec'
                            label 'test_dds' 
                            args "-v $workspace/ssl_test:/dds -v $workspace/sequoiadb-dds-driver:/dds-java -v $workspace/misc/sequoiadds/script/deploy/x509:/x509"
                        }
                    }
                    
                    steps {
                        script{
                            sh returnStatus: true, script: "/script/run_driver_test.sh -d /dds -g ${params.git_repository} -m ${MONGO_URL2} -r java -b ${params.branch} -p /dds-java -c /x509 -s"
                        }
                    }
                }
                
                stage("exec  compress test "){
                    agent {
                        docker {
                            image 'java_driver_test_exec'
                            label 'test_dds' 
                            args "-v $workspace/compress_test:/dds -v $workspace/sequoiadb-dds-driver:/dds-java"
                        }
                    }
                    
                    steps {
                        script{
                            sh returnStatus: true, script: "/script/run_driver_test.sh -d /dds -g ${params.git_repository} -m ${MONGO_URL1} -r java -b ${params.branch} -p /dds-java -z"
                        }
                    }
                }
            }
        }
    }
    post{
        always{
            junit testResults:'**/dds-test/drivers/java/**/build/test-results/test/*.xml',allowEmptyResults: true, keepLongStdio: true
            sh "cd $workspace/nossl_test/dds-test/scripts/; python3 backuplog.py -hl 192.168.24.63 192.168.24.65 192.168.24.66 -u sdbadmin -p Admin@1024 -k /ssd/sequoiadds/27017/log/ /ssd/sequoiadds/28017/log/ -b ${WORKSPACE}/backup" 
            tar archive: true, compress: true, defaultExcludes: false, dir: 'backup', exclude: '', file: "${JOB_NAME}_${BUILD_NUMBER}.tar.gz", glob: '', overwrite: true
        }
        failure {
            script {
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
