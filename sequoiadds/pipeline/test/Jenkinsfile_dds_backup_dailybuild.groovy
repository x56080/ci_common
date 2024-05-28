pipeline {
    agent { label 'master' }
    options {
        disableConcurrentBuilds()
        timestamps()
    }
    
    triggers { cron('H 6 * * *')}
    
    parameters{
        string(name: 'branch', defaultValue: 'main', description: '')
        string(name: 'cc_version', defaultValue: '1.0.4', description: '')
        booleanParam(name: 'is_enterprise', defaultValue: 'false', description: '')
        booleanParam(name: 'is_release', defaultValue: 'false', description: '')
    }
    
    stages {
        stage('compile') {
            parallel {
                stage("compile dds-backup") {
                    steps {
                        build job: 'compile_dds-backup', parameters: [string(name: 'branch', value: "${params.branch}"),booleanParam(name: 'is_enterprise', value: "${params.is_enterprise}")], wait: true
                    }
                }
                
                stage("compile dds-backup-driver") {
                    steps {
                        build job: 'compile_dds-backup-driver', parameters: [string(name: 'branch', value: "${params.branch}"),booleanParam(name: 'is_release', value: "${params.is_release}")], wait: true
                    }
                }
            }
        }
        
        stage('exec test') {
            parallel {
                stage("x86 test") {
                    steps {
                        build job: 'test_dds_backup_driver_x86', parameters: [string(name: 'branch', value: "${params.branch}"),string(name: 'dds_version', value: "${params.dds_version}"),string(name: 'cc_version', value: "${params.cc_version}")], wait: true
                    }
                }
                
                stage("arm test") {
                    steps {
                        build job: 'test_dds_backup_driver_arm', parameters: [string(name: 'branch', value: "${params.branch}"),string(name: 'dds_version', value: "${params.dds_version}"),string(name: 'cc_version', value: "${params.cc_version}")], wait: true
                    }
                }
            }
        }
    }
}
