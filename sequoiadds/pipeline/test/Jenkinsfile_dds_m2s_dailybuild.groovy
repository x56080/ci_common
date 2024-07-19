pipeline {
    agent { label 'master' }
    options {
        disableConcurrentBuilds()
        timestamps()
    }
    
    triggers {cron('H 14 * * *')}
    
    parameters{
        string(name: 'branch', defaultValue: 'main', description: '')
        string(name: 'dds_version', defaultValue: '3.4.14', description: '')
        string(name: 'cc_version', defaultValue: '1.1.0', description: '')
    }
    
    stages {
        stage('compile') {
            parallel {
                stage("compile dds m2s") {
                    steps {
                        build job: 'compile_dds_m2s', parameters: [string(name: 'branch', value: "${params.branch}")], wait: true
                    }
                }
            }
        }
        
        stage('exec test') {
            parallel {
                // stage("x86 test") {
                //     steps {
                //         build job: 'test_dds_backup_driver_x86', 
                //         parameters: [
                //             string(name: 'branch', value: "${params.branch}"),
                //             string(name: 'dds_version', value: "${params.dds_version}"),
                //             string(name: 'cc_version', value: "${params.cc_version}"),
                //             string(name: 'limit_memory_mb', value: "${params.limit_memory_mb}"),
                //             string(name: 'cache_size_gb', value: "${params.cache_size_gb}"),
                //             ],
                //         wait: true
                //     }
                // }

                stage("arm test") {
                    steps {
                        build job: 'test_dds_m2s_arm', 
                        parameters: [
                            string(name: 'branch', value: "${params.branch}"),
                            string(name: 'dds_version', value: "${params.dds_version}"),
                            string(name: 'cc_version', value: "${params.cc_version}"),
                            ],
                        wait: true
                    }
                }
            }
        }
    }
}
