#!/bin/bash

function show_help() {
  cat <<EOF
Usage: $(basename "$0") [options]

Options:
  -p, --product    Specify the product(sequoiadb|sequoiadds|sequoiascm|sequoiasac)
  -b, --branch     Specify the branch (default: master)
  -h, --help       Show this help message
EOF
}

declare -A mapbaseDir
declare -A mapdestDir

mapbaseDir[sequoiadb]="/ssd/jenkins_archive/archive_new/SequoiaDB/"
mapbaseDir[sequoiadds]="/sequoiadb/7.版本归档_NEW/SequoiaDDS/"
mapbaseDir[sequoiascm]="/ssd/jenkins_archive/publish_archive/版本归档_NEW/SequoiaCM/"
mapbaseDir[sequoiasac]="/ssd/jenkins_archive/publish_archive/版本归档_NEW/SequoiaSAC/"

mapdestDir[sequoiadb]="/sequoiadb/7.版本归档_NEW/SequoiaDB/"
mapdestDir[sequoiadds]="/sequoiadb/7.版本归档_NEW/SequoiaDDS/"
mapdestDir[sequoiascm]="/sequoiadb/7.版本归档_NEW/SequoiaCM/"
mapdestDir[sequoiasac]="/sequoiadb/7.版本归档_NEW/SequoiaSAC/"


branch='master'
opts=$(getopt -o p:b:h --long product:branch:,help -n 'parse-options' -- "$@")
if [ $? -ne 0 ]; then
  show_help
  exit 1
fi

eval set -- "$opts"
while true; do
  case "$1" in
    -p | --product)
      product="$2"
      shift 2 ;; 
    -b | --branch)
      branch="$2"
      shift 2 ;;
    -h | --help)
      show_help
      exit 0 ;;
    --)
      shift
      break ;;
    *)
      echo "Invalid option: $1"
      show_help
      exit 1 ;;
  esac
done

baseDir=${mapbaseDir[$product]} 
destDir=${mapdestDir[$product]} 

if [ $product = "sequoiadds" ];then
   source /etc/profile
   git clone http://gitlab.sequoiadb.com/sequoiadb/dds.git
   cd dds
   tag=$(git describe --tags --abbrev=0)
   msg=$(git tag -l --format='%(contents)' $tag)

   cd ../release
   cat x86_64/VERSION >release.notes
   echo "" >>release.notes
   version=$(grep version x86_64/VERSION|head -n 1 |awk -F ':' '{print $2}')
   version=$(echo $version)
   rm -rf x86_64/VERSION
   echo "$version 版本特性说明" >>release.notes
   echo "$msg" >>release.notes

   iconv -f utf-8 -t gb18030 release.notes > readme.txt
   rm -rf release.notes
   cd ..
   mv release $version

   major_version=$(echo $version | grep -oE '^[0-9]+\.[0-9]+')
   destDir=${destDir}${major_version}
   if [ ! -d ${destDir} ];then
      mkdir -p ${destDir}
   fi
   sudo mv $version ${destDir}
else
   baseDir="${baseDir}${branch}/"
   if [ ! -d $baseDir ];then
      echo "version not exist"
      exit 1
   fi

   cd "$baseDir"
   subDir=$(ls -dt */ | head -n 1)

   if [ -z "$subDir" ];then
      echo "version not exist"
      exit 1
   fi

   baseDir="${baseDir}${subDir}"

   version=$(basename $subDir)
   major_version=$(echo $version | grep -oE '^[0-9]+\.[0-9]+')

   destDir="${destDir}${major_version}"
   if [ $product = "sequoiascm" ];then
     cd "$baseDir"
     mkdir x86_64
     find . -maxdepth 1 -type f -exec mv {} x86_64 \;      
   fi

   if [ ! -d "${destDir}" ];then
      mkdir -p "${destDir}"
   fi

   sudo mv "$baseDir" "$destDir"
fi

