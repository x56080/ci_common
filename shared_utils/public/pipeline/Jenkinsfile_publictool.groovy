pipeline {
    agent {label 'master'}
    
    options {
        disableConcurrentBuilds()
        timestamps()
    }

    parameters{
        choice(name: 'component', choices: ['connector','cc','m2s','sequoiashake','dds_java'], description: '')
        string(name: 'branch', defaultValue: 'main', description: '')
        string(name: 'git_sha', defaultValue: '', description: '')
        string(name: 'version', defaultValue: '', description: '')
        
    }

    stages {
        stage('make') {
            steps {
                cleanWs()
                script{
                    if (params.component == "connector"){
                        def branch = params.branch
                        if (branch == "main"){
                            branch = "master"
                        }
                        build job: 'compile_sdbconnectors', parameters: [string(name: 'git_repository', value: 'http://gitlab.sequoiadb.com/sequoiadb/sdbconnectors.git'), string(name: 'branch', value: "${branch}"), booleanParam(name: 'is_release', value: true), string(name: 'git_sha', value: "${params.git_sha}")]
                    }else if (params.component == "cc"){
                        build job: 'dailybuild_clusterconfig', parameters: [string(name: 'git_sha', value: "${params.git_sha}")]
                    }else if (params.component == "m2s"){
                        build job: 'dailybuild_m2s', parameters: [string(name: 'git_sha', value: "${params.git_sha}")]
                    }else if (params.component == "sequoiashake"){
                        build job: 'dailybuild_sequoiashake', parameters: [string(name: 'git_sha', value: '')]
                    }else if(params.component == "dds_java"){
                        build job: 'compile_dds_driver_java', parameters: [string(name: 'BRANCH', value: "${params.branch}"), string(name: 'GIT_SHA', value: "${params.git_sha}"), string(name: 'GIT_TAG', value: ''), string(name: 'COMPILE_TYPE', value: 'package')]
                    }
                }
            }
        }
        
        stage('archive') {
            steps {
                script{
                    checkout scmGit(branches: [[name: '*/main']], extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'misc'], [$class: 'SparseCheckoutPaths', sparseCheckoutPaths: [[path: 'shared_utils/public/script']]]], userRemoteConfigs: [[url: 'http://gitlab.sequoiadb.com/sequoiadb/ci/ci_common.git']])
                    sh 'cp misc/shared_utils/public/script/archivetoolsversion.sh archiveversion.sh'
                    if (params.component == "connector"){
                        copyArtifacts filter: '**/*.tar.gz', fingerprintArtifacts: true, flatten: true, projectName: 'compile_sdbconnectors', selector: lastSuccessful()
                        sh './archiveversion.sh -p Connector'
                    }else if (params.component == "cc"){
                        copyArtifacts filter: '**/*.tar.gz', fingerprintArtifacts: true, flatten: true, projectName: 'dailybuild_clusterconfig', selector: lastSuccessful()
                        sh './archiveversion.sh -p cc'
                        
                    }else if (params.component == "m2s"){
                        copyArtifacts filter: '**/*.tar.gz', fingerprintArtifacts: true, flatten: true, projectName: 'dailybuild_m2s', selector: lastSuccessful()
                        sh './archiveversion.sh -p m2s'
                        
                    }else if (params.component == "sequoiashake"){
                        copyArtifacts filter: '**/*.tar.gz', fingerprintArtifacts: true, flatten: true, projectName: 'dailybuild_sequoiashake', selector: lastSuccessful()
                        sh "./archiveversion.sh -p sequoiashake -v ${params.version}"
                        
                    }else if(params.component == "dds_java"){
                        copyArtifacts filter: '**/*.tar.gz', fingerprintArtifacts: true, flatten: true, projectName: 'compile_dds_driver_java', selector: lastSuccessful()
                        sh "./archiveversion.sh -p dds_java"
                        
                    }
                }
            }
        }
    }
}
