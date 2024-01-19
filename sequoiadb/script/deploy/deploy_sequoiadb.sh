#!/bin/bash

hostlist="192.168.24.112,192.168.24.113,192.168.24.114"
user=sdbadmin
userpwd="Admin@1024"
curpath=$(cd .; pwd)

function help()
{
  echo "deploy_sequoiadb is a utility to deploy sequoiadb."
  echo ""
  echo "Usage:"
  echo "Options:"
  echo "  -h, --help             output help message, then exit"
  echo "  -u, --user             user of hostlist,request install user"
  echo "  -p, --password         password of user"
  echo "  --hostlist             hostlist of install sequoiadds"
  echo "  -c, --config           config of  "
  echo ""
}

config=""
ARGS=`getopt -o hu:p:c: --long help,user:,password:,hostlist:,config: -- "$@"`

eval set -- "${ARGS}"

while true
do
  case "$1" in
    -h | --help )                           help
                                            exit 0
                                            ;;
    -u | --user)       
                                            user=$2
                                            shift 2
                                            ;;
    -p | --password)                        userpwd=$2
                                            shift 2
                                            ;;
    -c | --config)                          config=$2
                                            shift 2
                                            ;;
    --hostlist)                             hostlist=$2
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

if [ -n "$config" ] && [ -f "$config" ];then
  cp $config $curpath
fi
IFS=',' read -ra parts <<< "$hostlist"

 >./inventory.ini
pos=1
echo "[db_servers]" >>./inventory.ini
for part in "${parts[@]}"; do
   echo "$part" >>./inventory.ini
   grep -q $part ~/.ssh/known_hosts
   if [ $? -ne 0 ];then
      ssh-keyscan -H $part >> ~/.ssh/known_hosts
   fi

   sed -i "s/\[host${pos}\]/$part/g" sequoiadb.conf
   let pos=pos+1
done


ansible-playbook -i inventory.ini --extra-vars "ansible_ssh_pass=${userpwd}" --extra-vars "ansible_ssh_user=${user}" deploy_sequoiadb.yml
