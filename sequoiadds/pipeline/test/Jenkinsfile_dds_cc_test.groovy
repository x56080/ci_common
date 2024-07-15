pipeline {
    agent {
        label 'test_dds_tool'
    }
    
    parameters {
        string(name: 'repository', defaultValue: 'http://gitlab.sequoiadb.com/sequoiadb/cluster-config.git', description: '')
        string(name: 'branch', defaultValue: 'add-testcase', description: '')
        string(name: 'dds_version', defaultValue: '3.4.10', description: '')
        string(name: 'upgrade_version', defaultValue: '3.4.14', description: '')
    }
    
    options {
        // disableConcurrentBuilds()
        timestamps()
    }
    
    environment {
        ARCHIVE_PATH="/sequoiadb/7.版本归档_NEW/"
        // X86_TESTHOSTS="root:Sdb@123123:192.168.29.101;root:Sdb@123123:192.168.29.26"
        ARM_TESTHOSTS="root:Sdb@123123:192.168.24.27;root:Sdb@123123:192.168.24.28"
        PROJECT_NAME="compile_dds_clusterconfig"
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
                    def script_path = sh returnStdout: true, script: "pwd"
                    script_path = script_path.split("\n")[0]

                    // TODO
                    // if (host_arch == "x86_64"){
                    //     TESTHOSTS=X86_TESTHOSTS
                    //     DDS_PROJECT_NAME = "compile_dds_x86"
                    // }else{
                    //     TESTHOSTS=ARM_TESTHOSTS
                    //     DDS_PROJECT_NAME = "compile_dds_arm"
                    // }
                    TESTHOSTS=ARM_TESTHOSTS
                    DDS_PROJECT_NAME = "compile_dds_arm"
                    
                    def dds_maj_ver = dds_version.substring(0,dds_version.lastIndexOf("."))
                    def upgrade_maj_ver = upgrade_version.substring(0,upgrade_version.lastIndexOf("."))
                    def cc_package = sh returnStdout: true, script: "basename \$(find ./ -name sdb-dds-cc*.tar.gz)"
                    
                    // TODO
                    def execpara = " --mode deploy"
                    execpara += " --tmp-path \"${WORKSPACE}/tmp\""
                    execpara += " --dds-package \"${ARCHIVE_PATH}/SequoiaDDS/${dds_maj_ver}/${dds_version}/${host_arch}/sequoiadb-dds-${dds_version}-linux_${host_arch}-installer.run\""
                    execpara += " --upgrade-package \"${ARCHIVE_PATH}/SequoiaDDS/${upgrade_maj_ver}/${upgrade_version}/${host_arch}/sequoiadb-dds-${upgrade_version}-linux_${host_arch}-installer.run\""
                    execpara += " --cc-package \"http://192.168.29.80:8080/view/daily_tools/job/compile_dds_clusterconfig/lastSuccessfulBuild/artifact/build/${cc_package}\""
                    execpara += " --ssh-user \"${TESTHOSTS}\""

                    def exec_file = "${script_path}/testcase/run.sh"
                    sh "${exec_file} ${execpara}"
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
                        // TODO 本地测试无需发送邮件
                        // emailext body: '$DEFAULT_CONTENT', subject: '$DEFAULT_SUBJECT', to: "${members}"
                    }
                }
            }
        }
    }
}
