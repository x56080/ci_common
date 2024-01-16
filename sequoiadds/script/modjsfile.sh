#!/bin/bash

# 默认的JSON文件路径
json_file="config.json"

# 默认的参数值
host_list=""
dbpath=""
backup_file=""
ddstest_path=""

# 显示帮助信息
function show_help() {
  cat << EOF
Usage: $0 -h <host_list> -d <dbpath> -b <backup_file> -p <ddstest_path> [-f <json_file>] [-n]

Options:
  -h    Specify the host list (comma-separated).
  -d    Specify the dbpath.
  -b    Specify the backup file.
  -p    Specify the ddstest path.
  -f    Specify the JSON file path (default: config.json).
  -n    Display help for host_list.

Example:
  $0 -h "CI-W-2,CI-W-3" -d "/ssd/sequoiadds/db" -b "/hdd/jenkins/workspace/test_master_dds_arm/backup" -p "/hdd/jenkins/workspace/test_master_dds_arm/engine/jstests/"
EOF
}

# 解析命令行参数
while getopts ":f:h:d:b:p:n" opt; do
  case $opt in
    f)
      json_file="$OPTARG"
      ;;
    h)
      host_list="$OPTARG"
      ;;
    d)
      dbpath="$OPTARG"
      ;;
    b)
      backup_file="$OPTARG"
      ;;
    p)
      ddstest_path="$OPTARG"
      ;;
    n)
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

# 检查必要参数是否提供
if [ -z "$host_list" ] || [ -z "$dbpath" ] || [ -z "$backup_file" ] || [ -z "$ddstest_path" ]; then
  echo "Error: Missing required parameters."
  show_help
  exit 1
fi

# 使用 jq 更新 JSON 文件
jq --arg host_list "$host_list" \
   --arg dbpath "$dbpath" \
   --arg backup_file "$backup_file" \
   --arg ddstest_path "$ddstest_path" \
   '.host_list = ($host_list | split(","))
    | .dbpath = $dbpath
    | .backup_file = $backup_file
    | .ddstest_path = $ddstest_path' "$json_file" > tmp_config.json

# 替换原始文件
mv tmp_config.json "$json_file"

echo "JSON 文件已更新。"

