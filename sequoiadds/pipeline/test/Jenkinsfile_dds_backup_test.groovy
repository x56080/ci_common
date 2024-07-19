pipeline {
    agent {
        label 'test-dds-backup'
    }
    
    parameters {
        string(name: 'repository', defaultValue: 'http://gitlab.sequoiadb.com/sequoiadb/dds-backup-driver.git', description: '')
        string(name: 'branch', defaultValue: 'main', description: '')
        string(name: 'cc_version', defaultValue: '1.0.4', description: '')
        string(name: 'dds_version', defaultValue: '3.4.15', description: '')
        string(name: 'limit_memory_mb', defaultValue: '2048', description: '')
        string(name: 'cache_size_gb', defaultValue: '1', description: '')
    }
    
    options {
        // disableConcurrentBuilds()
        timestamps()
    }
    
    environment {
        ARCHIVE_PATH="/sequoiadb/7.版本归档_NEW/"
        X86_TESTHOSTS="root:Sdb@123123:192.168.29.101,root:Sdb@123123:192.168.29.26"
        ARM_TESTHOSTS="root:Sdb@123123:192.168.24.71,root:Sdb@123123:192.168.24.70"
        PROJECT_NAME="compile_dds-backup_agent"
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
                // general testcase
                script{
                    def host_arch = sh returnStdout: true, script: "uname -p"
                    host_arch = host_arch.trim()
                    def TESTHOSTS = ""
                    def DDS_PROJECT_NAME=""
                    if (host_arch == "x86_64"){
                        TESTHOSTS=X86_TESTHOSTS
                        DDS_PROJECT_NAME = "compile_dds_x86"
                    }else{
                        TESTHOSTS=ARM_TESTHOSTS
                        DDS_PROJECT_NAME = "compile_dds_arm"
                    }
                    
                    def cc_maj_ver = cc_version.substring(0,cc_version.lastIndexOf("."))
                    def backup_package_agent = sh returnStdout: true, script: "basename \$(find ./ -name *.tar.gz)"

                    def execpara = " -DsshUsers=\"${TESTHOSTS}\""
                    execpara += " -DtmpDir=\"${WORKSPACE}/tmp\""
                    execpara += " -DddsPackagePath=\"http://192.168.29.80:8080/view/daily_dds/job/${DDS_PROJECT_NAME}/lastSuccessfulBuild/artifact/build_run_dds/sequoiadb-dds-${dds_version}-linux_${host_arch}-installer.run\""
                    execpara += " -DccPackagePath=\"${ARCHIVE_PATH}/SequoiaMisc/cc/${cc_maj_ver}/${cc_version}/sdb-dds-cc_v${cc_version}.tar.gz\""
                    execpara += " -DddsBackupDownloadUrl=\"http://192.168.29.80:8080/view/daily_dds/job/${PROJECT_NAME}/lastSuccessfulBuild/artifact/build/${backup_package_agent}\""
                    execpara += " -DtimeSyncCmd=\"chronyc -a makestep\""
                    execpara += " -DlimitMemoryMB=\"${limit_memory_mb}\""
                    execpara += " -DcacheSizeGB=\"${cache_size_gb}\""
                    execpara += " -Dtestnames=\"General\""
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
    }
}
