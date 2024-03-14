# -*- coding: utf-8 -*-
param(
    [string]$productName,
    [string]$version,
    [string]$targetPath
)

# 定义一个函数来获取版本号前缀
function Get-VersionPrefix {
    param (
        [string]$version
    )

    # 使用正则表达式匹配版本号前缀
    $match = $version -match '^(\d+\.\d+)'
    
    # 如果匹配成功，返回匹配的结果
    if ($match) {
        return $matches[1]
    }

    # 如果没有匹配到前缀，返回空字符串或其他适当的值
    return ""
}

$env:LANG = 'en_US.UTF-8'
$OutputEncoding = [System.Text.Encoding]::UTF8

# 解析主版本号（Major.Minor）
$majorMinorVersion = Get-VersionPrefix -version $version
$sourcePath = "\\192.168.20.253\share_new\7.版本归档_NEW\"
Write-Host "Source path : $sourcePath"

# 替换非法字符并构建目标路径
#$targetPath = "C:\upx\public"


# 如果 productName 为 "cc" 或 "m2s"，在产品名前加一层 "misc" 目录
if ($productName -eq "cc" -or $productName -eq "m2s") {
    $productName = "SequoiaMisc\$productName"
}

# 构建源路径
$sourceVersionPath = Join-Path -Path $sourcePath -ChildPath $productName
$sourceVersionPath = Join-Path -Path $sourceVersionPath -ChildPath "\"
$sourceVersionPath = Join-Path -Path $sourceVersionPath -ChildPath $majorMinorVersion
$sourceVersionPath = Join-Path -Path $sourceVersionPath -ChildPath "\"
$sourceVersionPath = Join-Path -Path $sourceVersionPath -ChildPath $version

# 如果目标路径不存在，创建它
if (-not (Test-Path -Path $targetPath)) {
    New-Item -ItemType Directory -Path $targetPath -Force
}


Write-Host "Source path : $sourceVersionPath"

# 复制特定版本的内容到目标路径
if (Test-Path $sourceVersionPath) {
    Copy-Item -LiteralPath $sourceVersionPath -Destination $targetPath -Recurse
} else {
    Write-Host "Source path does not exist: $sourceVersionPath"
	exit 1
}
