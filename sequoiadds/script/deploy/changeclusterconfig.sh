#!/bin/bash

function help()
{
  echo "changeclusterconfig.sh is a utility to change sequoiadds cluster ssl config."
  echo ""
  echo "Usage:"
  echo "Options:"
  echo "  -h, --help             output help message, then exit"
  echo "  -u, --username         username of hostlist"
  echo "  -p, --password         password of user"
  echo "  --hostlist             hostlist of install sequoiadds"
  echo ""
}

# 解析命令行参数
while [[ $# -gt 0 ]]; do
    case "$1" in
        -u|--username)
            username="$2"
            shift 2
            ;;
        -p|--password)
            password="$2"
            shift 2
            ;;
        --hostlist)                             
            hostlist=$2
            shift 2
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

hostlist=${hostlist:-192.168.24.63,192.168.24.65,192.168.24.66}

IFS=',' read -ra parts <<< "$hostlist"
 >./inventory.ini
echo "[db_servers]" >>./inventory.ini
for part in "${parts[@]}"; do
    echo "$part" >>"./inventory.ini"
    grep -q $part ~/.ssh/known_hosts
    if [ $? -ne 0 ];then
       ssh-keyscan -H $part >> ~/.ssh/known_hosts
    fi
done



# 检查必要参数是否提供
if [ -z "$username" ] || [ -z "$password" ] ; then
    echo "Usage: $0 -u|--username <username> -p|--password <password> "
    exit 1
fi

ansible-playbook -i inventory.ini  changeclusterconfig.yml --extra-vars "ansible_ssh_pass=${password}" --extra-vars "ansible_ssh_user=${username}"
