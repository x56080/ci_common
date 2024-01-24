#!/bin/bash

function help()
{
  echo "install_dds is a utility to install sequoiadds."
  echo ""
  echo "Usage:"
  echo "Options:"
  echo "  -h, --help             output help message, then exit"
  echo "  -u, --user             user of hostlist"
  echo "  -p, --password         password of user"
  echo "  -c, --config           config of deploy"
  echo "  --hostlist             hostlist of install sequoiadds"
  echo "  -d, --default-path     default deployment path"
  echo ""
}

# 默认的部署路径
default_path="."

ARGS=`getopt -o hu:p:c:d: --long help,user:,password:,config:,hostlist:,default-path: -- "$@"`

eval set -- "${ARGS}"

while true
do
  case "$1" in
    -h | --help )                           help
                                            exit 0
                                            ;;
    -u | user)
                                            user=$2
                                            shift 2
                                            ;;
    -p | --password)                        userpwd=$2
                                            shift 2
                                            ;;
    -c | --config)                          deployconfig=$2
                                            shift 2
                                            ;;
    --hostlist)                             hostlist=$2
                                            shift 2
                                            ;;
    -d | --default-path)                   default_path=$2
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

# 设置默认值
user=${user:-root}
userpwd=${userpwd:-Sdb@123123}
deployconfig=${deployconfig:-deployconfig.yml}
hostlist=${hostlist:-192.168.24.112,192.168.24.113,192.168.24.114}

isneedsetupssh=0
IFS=',' read -ra parts <<< "$hostlist"
pos=1
 >"${default_path}/inventory.ini"
echo "[db_servers]" >>"${default_path}/inventory.ini"
for part in "${parts[@]}"; do
    echo "$part" >>"${default_path}/inventory.ini"
    grep -q $part ~/.ssh/known_hosts
    if [ $? -ne 0 ];then
       ssh-keyscan -H $part >> ~/.ssh/known_hosts
    fi
    sed -i "s/\${HOST${pos}}/$part/g" deployconfig.yml
    let pos=pos+1

    ssh -o BatchMode=yes -o ConnectTimeout=5 sdbadmin@$part echo "SSH access successful"
    if [ $? -ne 0 ];then
        isneedsetupssh=1
    fi
done

if [ -n "${default_path}" ];then
  sed -i "s#/data/sequoiadds#${default_path}#g" deployconfig.yml
fi

if [ $isneedsetupssh -eq 1 ];then
   # 构造 ansible-playbook 命令
   ansible-playbook -i "${default_path}/inventory.ini" setup_ssh_key.yml --extra-vars "ansible_ssh_pass=${userpwd}" --extra-vars "ansible_ssh_user=${user}"
   # 检查 setup_ssh_key.yml 是否执行成功，如果失败则报错退出
   test $? -ne 0 && echo "setup ssh nopassword access failed" && exit 1
fi

# 构造 ansible-playbook 命令
ansible-playbook -i "${default_path}/inventory.ini" remove_ddscluster.yml -l db_servers --extra-vars "ansible_ssh_pass=${userpwd}" --extra-vars "ansible_ssh_user=${user}"
test $? -ne 0 && echo "remove cluster failed!" && exit 1

latest_tool_package=$(wget -qO- http://192.168.29.80:8080/view/daily_tools/job/dailybuild_clusterconfig/lastSuccessfulBuild/api/json | grep -oE 'sdb-dds-cc_v[0-9]+\.[0-9]+\.[0-9]+\.tar\.gz' | tail -n 1)
# 下载最新版本的工具包
wget "http://192.168.29.80:8080/view/daily_tools/job/dailybuild_clusterconfig/lastSuccessfulBuild/artifact/build/$latest_tool_package"
test $? -ne 0 && echo "download cc failed!" && exit 1

test -d cc && rm -rf cc
mkdir -p cc

# 解压缩工具包
tar -zxvf "$latest_tool_package" -C cc --strip-components=1
test $? -ne 0 && echo "unpackage cc failed!" && exit 1

# 如果外部传入 deployconfig，则使用该文件执行部署，否则使用默认部署路径下的 deployconfig.yml
cc/sdb-dds-cc -c "$deployconfig"

