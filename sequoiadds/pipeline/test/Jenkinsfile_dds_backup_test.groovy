pipeline {
    agent {
        label 'test-dds-backup'
    }
    
    parameters {
        string(name: 'repository', defaultValue: 'http://gitlab.sequoiadb.com/sequoiadb/dds-backup-driver.git', description: '')
        string(name: 'branch', defaultValue: 'main', description: '')
        string(name: 'dds_version', defaultValue: '3.4.8', description: '')
        string(name: 'cc_version', defaultValue: '1.0.4', description: '')
    }
    
    options {
        // disableConcurrentBuilds()
        timestamps()
    }
    
    environment {
        ARCHIVE_PATH="/sequoiadb/7.版本归档_NEW/"
        X86_TESTHOSTS="root:Sdb@123123:192.168.29.101,root:Sdb@123123:192.168.29.26"
        ARM_TESTHOSTS="root:Sdb@123123:192.168.24.69,root:Sdb@123123:192.168.24.70"
        PROJECT_NAME="compile_dds-backup"
    }
    
    
    stages {
        stage('pull code') {
            steps {
                cleanWs()
                checkout scmGit(branches: [[name: "${params.branch}"]],  extensions: [], userRemoteConfigs: [[url: "${params.repository}"]])
                copyArtifacts filter: '**/*.tar.gz', fingerprintArtifacts: true, flatten: true, projectName: "${PROJECT_NAME}", selector: lastSuccessful()
            }
        }
        
        stage('exec test') {
            steps {
                script{
                    def host_arch = sh returnStdout: true, script: "uname -p"
                    host_arch = host_arch.trim()
                    def TESTHOSTS = ""
                    if (host_arch == "x86_64"){
                        TESTHOSTS=X86_TESTHOSTS
                    }else{
                        TESTHOSTS=ARM_TESTHOSTS
                    }
                    
                    def dds_maj_ver = dds_version.substring(0,dds_version.lastIndexOf("."))
                    def cc_maj_ver = cc_version.substring(0,cc_version.lastIndexOf("."))
                    def backup_package_agent = sh returnStdout: true, script: "basename \$(find ./ -name *.tar.gz)"
                    
                    def execpara = " -DsshUsers=\"${TESTHOSTS}\""
                    execpara += " -DtmpDir=\"${WORKSPACE}/tmp\""
                    execpara += " -DddsPackagePath=\"${ARCHIVE_PATH}/SequoiaDDS/${dds_maj_ver}/${dds_version}/${host_arch}/sequoiadb-dds-${dds_version}-linux_${host_arch}-installer.run\""
                    execpara += " -DccPackagePath=\"${ARCHIVE_PATH}/SequoiaMisc/cc/${cc_maj_ver}/${cc_version}/sdb-dds-cc_v${cc_version}.tar.gz\""
                    execpara += " -DddsBackupDownloadUrl=\"http://192.168.29.80:8080/view/daily_dds/job/${PROJECT_NAME}/lastSuccessfulBuild/artifact/build/${backup_package_agent}\""
                    execpara += " -DtimeSyncCmd=\"chronyc -a makestep\""
                    sh "mvn clean test ${execpara}"
                }
            }
            
            post {
                success{
                    junit skipOldReports: true, stdioRetention: '', testResults: '**/junitreports/*.xml'
                }
                failure {
                    script {
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
    }
}
