#!/bin/bash

function show_help() {
  cat << EOF
Usage: $0 -b <branch> -p <archive path> -n <build number> [-h]

Options:
  -b    Specify the product branch.
  -n    Specify the build number.
  -p    Specify the archive path.
  -h    Display help for host_list.

Example:
  $0 -b master -p $JENKINS_HOME/jobs/$JOB_NAME/builds/98/archive
EOF
}

buildnumber=""

# 解析命令行参数
while getopts ":b:p:h" opt; do
  case $opt in
    p)
      src_archive_path="$OPTARG"
      ;;
    b)
      branch="$OPTARG"
      ;;
    n)
      buildnumber="$OPTARG"
      ;;
    h)
      show_help
      exit 0
      ;;
    \?)
      echo "Invalid option: -$OPTARG" >&2
      show_help
      exit 1
      ;;
    :)
      echo "Option -$OPTARG requires an argument." >&2
      show_help
      exit 1
      ;;
  esac
done

if [ ! -d "${src_archive_path}" ];then
   echo "archive path:$src_archive_path is not exist!"
   exit 1
fi

if [ -z "${buildnumber}" ];then
   parentdir=$(dirname $src_archive_path)
   nextBuildNumber=${parentdir}/nextBuildNumber
   buildnumber=$(($(cat ${nextBuildNumber}) - 1))
fi

src_archive_path="${src_archive_path}/${buildnumber}/archive"
if [ ! -d "${src_archive_path}" ];then
   echo "archive path:$src_archive_path is not exist!"
   exit 1
fi

function compress_runpackage()
{
    run_package=$(basename $(find ./ -name "*.run"))
    test -z "${run_package}" && echo "run package is not exist!" && exit 1
    tar_packagename=$(echo $run_package|sed 's/.run//g')
    tar -czvf ${tar_packagename}.tar.gz ${run_package}   
    test $? -ne 0 &&  echo "tar -czvf ${tar_packagename}.tar.gz ${run_package} failed!" && exit 1
}


dest_archive_path=/ssd/jenkins_archive/publish_archive/版本归档_NEW/SequoiaSAC/
version_file=$(find $src_archive_path -name VERSION)
if [ -z "$version_file" ];then
   echo "VERSION file is not exist!!!"
   exit 1
fi

version=$(cat ${version_file}|grep "SAC version"|awk -F ':' '{print $2}')
version=$(echo ${version})

dest_archive_path="${dest_archive_path}/${branch}/${version}"
if [ -d "${dest_archive_path}" ];then
   rm -rf "${dest_archive_path}"
fi

aarch64="${dest_archive_path}/aarch64"
mkdir -p $aarch64
x86_64="${dest_archive_path}/x86_64"
mkdir -p $x86_64

find $src_archive_path -name "*linux_aarch64*" -exec cp {} ${aarch64} \;
test $? -ne 0 && echo "find $src_archive_path -name "*linux_aarch64*" -exec cp {} ${aarch64} \; failed" && exit 1
find $src_archive_path -name "*linux_x86_64*"  -exec cp {} ${x86_64} \;
test $? -ne 0 && echo "find $src_archive_path -name "*linux_x86_64*"  -exec cp {} ${x86_64} \; failed" && exit 1
find $src_archive_path -name sac-*-release.tar.gz -exec cp {} ${x86_64} \;
test $? -ne 0 && echo "find $src_archive_path -name sac-*-release.tar.gz -exec cp {} ${x86_64} \; failed" && exit 1
cp ${version_file} ${dest_archive_path}
test $? -ne 0 && echo "cp ${version_file} ${dest_archive_path} failed" && exit 1

cd $x86_64
compress_runpackage

cd $aarch64
compress_runpackage

exit 0

