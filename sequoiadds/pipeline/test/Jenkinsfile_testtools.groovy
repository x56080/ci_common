pipeline {
   agent { 
      label 'test_dds' 
   }

   triggers {
      cron('H 4 * * 5')
   }

   options {
      disableConcurrentBuilds()
      timestamps();
   }

   parameters{
      string(name: 'test_rep', defaultValue: 'http://gitlab.sequoiadb.com/sequoiadb/dds-test.git', description: '')  
      string(name: 'ci_rep', defaultValue: 'http://gitlab.sequoiadb.com/wangwenjing/ci_common.git', description: '')
      string(name: 'testbranch', defaultValue: 'main', description: '')
      string(name: 'ccbranch', defaultValue: 'main', description: '')  
      booleanParam(name: 'is_notify', defaultValue: true, description: '')
   }

   environment{
      hostlist="192.168.24.63,192.168.24.65,192.168.24.66"
      rootpwd="Sdb@123123"
      arch="aarch64"
   }

   stages{
      stage("build cc"){
         steps{
            // 编译CC
            build job: 'dailybuild_clusterconfig', parameters: [string(name: 'git_sha', value: "${params.ccbranch}")]
         }
      }
      
      stage("prepare tool and install package"){
         steps{
            cleanWs()
            // 从产品工程拉取安装包
            copyArtifacts filter: '**/*.run', fingerprintArtifacts: true, flatten: true, projectName: 'compile_dds_arm', selector: lastSuccessful(), target: '.'
            // 从CC工程拉取工具包
            copyArtifacts filter:'**/*.tar.gz',fingerprintArtifacts:true, flatten: true, projectName:'dailybuild_clusterconfig',selector:lastSuccessful(),target: '.'
         }
      }
      
      stage("install dds"){
         steps{
            // 执行dds安装
            checkout scmGit(branches: [[name: '*/main']], extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'misc']], userRemoteConfigs: [[url: "${params.ci_rep}"]])
            sh 'cd misc/shared_utils/script; ./install_runpackage.sh --hostlist $hostlist -p $rootpwd -a $arch -d $WORKSPACE'
         }
      }
      
      stage("pull test code"){
         steps{
            // 拉取测试代码
            checkout scmGit(branches: [[name: "${params.testbranch}"]], extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'test'],[$class: 'SparseCheckoutPaths', sparseCheckoutPaths: [[path: 'tools']]]], userRemoteConfigs: [[url: "${params.test_rep}"]])
         }
      }
      
      stage("Generate Test Configuration"){
         steps{
            // 生成测试配置
            sh 'cd misc/sequoiadds/script/tools/; ./gentestconf.sh --hostlist $hostlist --ccdir $WORKSPACE/ --conf $WORKSPACE/test/tools/python/conf/tools.ini'
         }
      }
      
      stage("exec test"){
         steps{
            //执行cc工具测试
            sh 'cd test/tools/python; python3 runtest.py'         
         }
      }
   }
   
   post{
      success {
         // 成功则发布解析测试报告
         junit 'test/tools/python/testReport/xmlReport/*.xml'
      }
   
      failure {
         script{
            if ( params.is_notify) {
               emailext body: '$DEFAULT_CONTENT', subject: '$DEFAULT_SUBJECT', to: '$DEFAULT_RECIPIENTS'
            }
         }
      }
   }
}
