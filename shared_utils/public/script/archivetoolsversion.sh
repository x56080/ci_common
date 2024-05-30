#!/bin/bash

set -x
function show_help() {
  cat <<EOF
Usage: $(basename "$0") [options]

Options:
  -p, --product    Specify the product(cc|m2s|sequoiashake|Connector|dds_java)
  -v, --version    Specify the version 
  -h, --help       Show this help message
EOF
}

function get_cc_release_notes()
{
   version=$1
   source /etc/profile # 为了使用新版git
   release_note="release.notes"
   tar -xzf *.tar.gz
   $(find ./ -name sdb-dds-cc) --version >>$release_note
   echo "" >>$release_note
   echo "sdb-dds-cc 是用于快速部署 SequoiaDB(DDS) 的工具" >>$release_note
   echo "" >>$release_note
   echo "## sdb-dds-cc ${version} 版本说明" >>$release_note
   test -d cluster-config && rm -rf cluster-config
   git clone http://gitlab.sequoiadb.com/sequoiadb/cluster-config.git
   test $? -ne 0 && echo "http://gitlab.sequoiadb.com/sequoiadb/cluster-config.git" && exit 1
   cd cluster-config
   tag=$(git describe --tags --abbrev=0)
   msg=$(git tag -l --format='%(contents)' $tag)
   cd ..
   echo "">>$release_note
   echo "$msg">>$release_note
   iconv -f utf-8 -t gb18030 $release_note > release_note_${version}.txt
}

function archive_cc()
{
   version=$1
   get_cc_release_notes $version
   mkdir -p $version
   cp *.tar.gz $version
   cp *.txt $version
}

function get_m2s_release_notes()
{
  release_note="release.notes"
  tar -xzf $(find ./ -name *_linux_x86_64.tar.gz)
  $(find . -type f -name m2s-analyzer) --version 1>>$release_note 2>&1
  echo "" >>$release_note
  $(find . -type f -name m2s-collector) --version 1>>$release_note 2>&1
  echo "" >>$release_note
  $(find . -type f -name sniffer.sh) --version 1>>$release_note 2>&1
  echo "" >>$release_note
}

function archive_m2s()
{
   version=$1
   mkdir -p $version/x86_64
   mkdir -p $version/aarch64
   get_m2s_release_notes
   cp $(find ./ -name *_linux_x86_64.tar.gz) $version/x86_64
   cp $(find ./ -name *_linux_aarch64.tar.gz) $version/aarch64
   cp release.notes $version
}

declare -A mapdestDir
mapdestDir[cc]="/sequoiadb/7.版本归档_NEW/SequoiaMisc/cc/"
mapdestDir[m2s]="/sequoiadb/7.版本归档_NEW/SequoiaMisc/m2s/"
mapdestDir[sequoiashake]="/sequoiadb/7.版本归档_NEW/SequoiaMisc/sequoiashake/"
mapdestDir[Connector]="/sequoiadb/7.版本归档_NEW/Connector/"
mapdestDir[dds_java]="/sequoiadb/7.版本归档_NEW/SequoiaDDS/driver/Java/"
mapdestDir[dds_backup_agent]="/sequoiadb/7.版本归档_NEW/SequoiaMisc/dds_backup_agent/"
version=""
opts=$(getopt -o p:v:h --long product:version:,help -n 'parse-options' -- "$@")
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
    -v | --version)
      version="$2"
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


if [ "$version"M = ""M ];then 
   filename="$(ls *.tar.gz)"
   if [[ $filename =~ ([0-9]+\.[0-9]+\.[0-9]+) ]]; then
      version=${BASH_REMATCH[1]}
      echo $version
   fi
fi

if [ "$version"M = ""M ];then
   echo "Version parameter cannot be empty!"
   exit 1
fi

if [ "$product" = "cc" ];then
   archive_cc $version
elif [ "$product" = "m2s" ];then
   archive_m2s $version
else
   mkdir -p $version
   cp *.tar.gz $version
fi

destDir=${mapdestDir[$product]}
major_version=$(echo $version | grep -oE '^[0-9]+\.[0-9]+')
destDir="${destDir}${major_version}"

if [ ! -d "${destDir}" ];then
   mkdir -p "${destDir}"
fi

sudo mv "$version" "$destDir"


