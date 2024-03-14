param (
    [string]$productName,
    [string]$version,
    [string]$srcBasePath
)

$productMap = @{
    "SequoiaCM" = "sequoiacm"
	"SequoiaSAC" = "sequoiasac"
	"SequoiaDB" = "sequoiadb"
	"SequoiaDDS" = "sequoiadds"
    "SequoiaSQL" = "sequoiasql/mysql"
    "m2s" = "tools/m2s"
	"cc" = "tools/dds-cc"
    # 添加更多产品名和对应目录的映射
}

# 设置变量
#$upxPath = "C:\upx\upx.exe"
$serviceName = "sequoiadb"
$operator = "wangwenjing"
$password = "c0m17OPxzoBbviQlYQVRWEbaCCp09MP2"
$command = ".\upx.exe"

#$srcBasePath = "C:\upx\public\"
$destBasePath = "/images/"

$srcPath = Join-Path -Path $srcBasePath -ChildPath $version

if ($productMap.ContainsKey($productName)) {
    $productDirectory = $productMap[$productName]
	$destPath = $destBasePath + $productDirectory
	# 构造登录命令
    $loginCommand = "$command login $serviceName $operator $password"
    # 执行登录
    Invoke-Expression -Command $loginCommand
    # 检查登录结果
    if ($?) {
	    Write-Host "srcPath:$srcPath"
	    Write-Host "destPath:$destPath"
		
        # 登录成功后执行命令
        $lsCommand = "$command ls $destPath"
        $putCommand = "$command put $srcPath $destPath"

		Write-Host "lsCommand:$lsCommand"
        Invoke-Expression -Command $lsCommand
	    
		Write-Host "putCommand:$putCommand"
        Invoke-Expression -Command $putCommand
	    Invoke-Expression -Command $lsCommand
    } else {
        Write-Host "Login failed. Please check your credentials."
		exit 1
    }
}else {
    Write-Host "Invalid product name: $productName"
	exit 1
}



