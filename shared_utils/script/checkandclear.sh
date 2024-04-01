#!/bin/bash
# 获取当前脚本所在盘的可用空间，单位为字节
available_space=$(df --output=avail -B 1 "$(dirname "$0")" | tail -n 1)

# 将可用空间转换为GB
available_space_gb=$((available_space / 1024 / 1024 / 1024))
echo "Available space: $available_space_gb GB"

if [ "$available_space_gb" -lt 100 ]; then
    echo "Starting cleanup..."
    find /hdd/jenkins/home/jobs/*/builds -maxdepth 1 -mindepth 1 -type d -ctime +5 -exec rm -r {} \;

    # 清理完成后再次获取可用空间
    available_space=$(df --output=avail -B 1 "$(dirname "$0")" | tail -n 1)
    available_space_gb=$((available_space / 1024 / 1024 / 1024))
    echo "Available space after cleanup: $available_space_gb GB"

    # 如果清理后仍然不足，继续调整清理策略并清理
    if [ "$available_space_gb" -lt 100 ]; then
        echo "Adjusting cleanup strategy and continuing cleanup..."

        # 在这里添加额外的清理逻辑
        cleardirs=$(du --max-depth=0 /hdd/jenkins/home/jobs/*/builds/*|sort -n|tail -n 10|awk '{print $2}')
        for dir in ${cleardirs}
        do
          echo "rm -rf $dir"
        done
        # 清理完成后再次获取可用空间
        available_space=$(df --output=avail -B 1 "$(dirname "$0")" | tail -n 1)
        available_space_gb=$((available_space / 1024 / 1024 / 1024))

        echo "Available space after adjusted cleanup: $available_space_gb GB"
    fi
fi

echo "Cleanup completed."
