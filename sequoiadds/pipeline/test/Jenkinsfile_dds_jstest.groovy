def prepare(){
    deleteDir()
    dir('/ssd/sequoiadds/db'){
        deleteDir()
    }
    sh 'mkdir -p /ssd/sequoiadds/db'
    sh "rm -rf /tmp/*.run"
    copyArtifacts filter: '**/*.run', fingerprintArtifacts: true, flatten: true, projectName: "${project_name}", selector: lastSuccessful(), target: '/tmp'
    sh ' . /etc/default/sequoiadb-dds; sudo $INSTALL_DIR/uninstall --mode unattended'
    sh 'sudo $(ls /tmp/*.run) --mode unattended'
    checkout scmGit(branches: [[name: "${params.branch}"]], extensions: [], userRemoteConfigs: [[url: "${params.git_repository}"]])
}

pipeline {
    agent {label 'test_dds'}

    parameters {
        string(name: 'git_repository', defaultValue: 'http://gitlab.sequoiadb.com/sequoiadb/dds-test.git', description: '')
        string(name: 'branch', defaultValue: 'main', description: '')
        choice(name: 'host_arch', choices: ['aarch64','x86_64'], description: 'Select target architecture')
    }
    
    options {
        disableConcurrentBuilds()
        timestamps()
        buildDiscarder(logRotator(artifactDaysToKeepStr: '7', artifactNumToKeepStr: '5', daysToKeepStr: '7', numToKeepStr: '7'))
    }
    
    environment{
        project_name=''
        agent_label = ""
        first_agent_label = ""
        host_list=""
    }
    
    stages {
        stage('select project'){
            steps {
                script {
                    if (params.host_arch == "aarch64"){
                        project_name = "compile_dds_arm"
                        agent_label = "test_dds_arm" 
                        first_agent_label = "test_dds_arm_1"
                        host_list="192.168.24.61,192.168.24.62,192.168.24.64,192.168.24.68"
                    }else{
                        project_name = "compile_dds_x86"
                        agent_label = "test_dds_x86" 
                        first_agent_label = "test_dds_x86_1"
                        host_list="192.168.29.99,192.168.29.156,192.168.29.158,192.168.29.27"
                    }
                }
            }
        }
        stage('Parallel prepare test') {
            parallel {
                stage('prepare suite1'){
                    agent {
                       label "${agent_label}" 
                    }
                    steps {
                        prepare()
                    }
                    
                }
                
                stage('prepare suite2'){
                    agent {
                       label "${agent_label}"  
                    }
                    steps {
                        prepare()
                    }
                }
                
                stage('prepare suite3'){
                    agent {
                       label "${agent_label}" 
                    }
                    steps {
                        prepare()
                    }
                }
                
                stage('prepare suite4'){
                    agent {
                       label "${agent_label}" 
                    }
                    steps {
                        prepare()
                    }
                }
            }
        }
        
        stage('Parallel exec test') {
            parallel {
                stage('exec suite1'){
                    agent {
                        docker {
                            image '192.168.40.10:10010/dds_test/dds_test:v3.4.2'
                            label "${agent_label}" 
                            args "--pid='host' -v /opt/sequoiadds/:/opt/sequoiadds/ -v /hdd/jenkins/workspace/${JOB_NAME}/engine/jstests/:/dds -v /ssd/sequoiadds/db:/data/db"
                        }
                    }
                    steps {
                        sh 'cd /dds && ./runTest.py run --configfile ./testconfig1.json'
                    }
                }
                
                stage('exec suite2'){
                    agent {
                        docker {
                            image '192.168.40.10:10010/dds_test/dds_test:v3.4.2'
                            label "${agent_label}"  
                            args "--pid='host' -v /opt/sequoiadds/:/opt/sequoiadds/ -v /hdd/jenkins/workspace/${JOB_NAME}/engine/jstests/:/dds -v /ssd/sequoiadds/db:/data/db"
                        }
                    }
                    steps {
                        sh 'cd /dds && ./runTest.py run --configfile ./testconfig2.json'
                    }
                }

                stage('exec suite3'){
                    agent {
                        docker {
                            image '192.168.40.10:10010/dds_test/dds_test:v3.4.2'
                            label "${agent_label}" 
                            args "--pid='host' -v /opt/sequoiadds/:/opt/sequoiadds/ -v /hdd/jenkins/workspace/${JOB_NAME}/engine/jstests/:/dds -v /ssd/sequoiadds/db:/data/db"
                        }
                    }
                    steps {
                        sh 'cd /dds && ./runTest.py run --configfile ./testconfig2.json'
                    }
                }
                
                stage('exec suite4'){
                    agent {
                        docker {
                            image '192.168.40.10:10010/dds_test/dds_test:v3.4.2'
                            label "${agent_label}" 
                            args "--pid='host' -v /opt/sequoiadds/:/opt/sequoiadds/ -v /hdd/jenkins/workspace/${JOB_NAME}/engine/jstests/:/dds -v /ssd/sequoiadds/db:/data/db"
                        }
                    }
                    steps {
                        sh 'cd /dds && ./runTest.py run --configfile ./testconfig2.json'
                    }
                }
            
            }
        }
        
        stage('proc test result') {
            agent {
                label "${first_agent_label}"
            }
            
            steps {
                script{
                    checkout scmGit(branches: [[name: '*/main']], extensions: [[$class: 'SparseCheckoutPaths', sparseCheckoutPaths: [[path: 'sequoiadds']]], [$class: 'RelativeTargetDirectory', relativeTargetDir: 'misc']], userRemoteConfigs: [[url: 'http://gitlab.sequoiadb.com/sequoiadb/ci/ci_common.git']])
                    sh "misc/sequoiadds/script/modjsfile.sh -h ${host_list} -d /ssd/sequoiadds/db -b ${WORKSPACE}/backup -p ${WORKSPACE}/ -f ${WORKSPACE}/scripts/ddsjstestconf.json "
                    sh "source /opt/python3.9/bin/activate ; python scripts/backuplogsub.py --configfile scripts/ddsjstestconf.json;deactivate"                    
                    def scriptexit = sh returnStatus: true, script: "grep -r -E \"Summary of .* suite: .* \\\\(([1-9][0-9]*|[1-9]) succeeded, [0-9]+ were skipped, [1-9]+ failed, [0-9]+ errored\\\\)\$\" ${WORKSPACE}/backup/*/hdd/*  -A 20"
                    if ( scriptexit == 0){
                        currentBuild.result = 'UNSTABLE'
                    }
                }
            }
            
            post{
                unstable {
                    tar archive: true, compress: true, defaultExcludes: false, dir: 'backup', exclude: '', file: "${JOB_NAME}_${BUILD_NUMBER}.tar.gz", glob: '', overwrite: true
                }
            }    
        }
    }
}