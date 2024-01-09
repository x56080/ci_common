#!/bin/bash
set -x
hostlist="192.168.24.112,192.168.24.113,192.168.24.114"
rootpwd="Sdb@123123"
arch="aarch64"
curpath=$(cd .; pwd)
echo $curpath
function help()
{
  echo "install_dds is a utility to install sequoiadds."
  echo ""
  echo "Usage:"
  echo "Options:"
  echo "  -h, --help             output help message, then exit"
  echo "  -p, --password         password of hostlist"
  echo "  --hostlist             hostlist of install sequoiadds"
  echo "  -a, --arch             All hosts in the list and their architectures"
  echo "  -d, --dir              Location of the run package "
  echo ""
}

ARGS=`getopt -o hp:a:d: --long help,password:,hostlist:,arch:,dir -- "$@"`

eval set -- "${ARGS}"

while true
do
  case "$1" in
    -h | --help )                           help
                                            exit 0
                                            ;;
    -p | --password)                        rootpwd=$2
                                            shift 2
                                            ;;
    -d | --dir)                             runpackagepath=$2
                                            shift 2
                                            ;;
    --hostlist)                             hostlist=$2 
                                            shift 2
                                            ;;
    -a | --arch)                            arch=$2
                                            shift 2
                                            ;;
    --)                                     shift
                                            break
                                            ;;
    *)                                      echo "ERROR: Internal error!"
                                            exit 64
                                            ;;
  esac
done

IFS=',' read -ra parts <<< "$hostlist"

 >./inventory.ini
echo "[db_servers]" >>./inventory.ini
for part in "${parts[@]}"; do
   echo "$part" >>./inventory.ini
done


if [ -f ${runpackagepath}/*.run ];then
   test -d ${curpath}/release || mkdir ${curpath}/release
   mv ${runpackagepath}/*.run ${curpath}/release
else
  echo "run package is not exist !!!"
  exit 1
fi


ansible-playbook ansible/install_playbook.yml -i ./inventory.ini -l db_servers --extra-vars "source_file_path=${curpath}/release/" --extra-vars "ansible_ssh_pass=${rootpwd}" --extra-vars "required_arch=${arch}" --extra-vars "ansible_ssh_user=root"

