# 发布到 GitHub 的辅助脚本（仅本地使用，不会被 MOD 打包进 jar）
#
# 用法：
#   pwsh -File publish.ps1            # 只提交本地改动
#   pwsh -File publish.ps1 -Push      # 提交后推送到 GitHub
#
# 前置：目标仓库 https://github.com/GNX001/dimensionhome 必须已经在 GitHub 上创建。
# 若尚未创建，脚本会给出明确提示（远端返回 Repository not found）。

param(
    [switch]$Push,
    [string]$Message = ''
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$remote = 'https://github.com/GNX001/dimensionhome.git'

# ---------- 检查工作区 ----------
$dirty = git status --porcelain
if (-not $dirty) {
    Write-Host '工作区干净，没有需要提交的改动。' -ForegroundColor Yellow
} else {
    if (-not $Message) {
        $Message = "更新：$(Get-Date -Format 'yyyy-MM-dd HH:mm')"
    }
    Write-Host "=== 待提交的改动 ===" -ForegroundColor Cyan
    git status --short
    git add -A
    git commit -q -m $Message
    Write-Host "已提交：$Message" -ForegroundColor Green
}

if (-not $Push) {
    Write-Host ''
    Write-Host '（未推送。加 -Push 参数即可推送到 GitHub。）' -ForegroundColor Yellow
    exit 0
}

# ---------- 推送 ----------
Write-Host ''
Write-Host "=== 推送到 $remote ===" -ForegroundColor Cyan

# 先探测远端是否存在，给出比 git 原始报错更明确的提示。
$env:GIT_TERMINAL_PROMPT = '0'
$probe = git ls-remote --exit-code $remote 2>&1
if ($LASTEXITCODE -ne 0) {
    if ($probe -match 'not found|Not Found') {
        Write-Host ''
        Write-Host '远端仓库不存在。请先在 GitHub 上创建：' -ForegroundColor Red
        Write-Host '  https://github.com/new?name=dimensionhome' -ForegroundColor Red
        Write-Host '（Owner 选 GNX001，Repository name 填 dimensionhome，不要勾选任何初始化文件）' -ForegroundColor Red
        exit 1
    }
    Write-Host "远端探测失败：$probe" -ForegroundColor Red
    exit 1
}

Remove-Item Env:\GIT_TERMINAL_PROMPT -ErrorAction SilentlyContinue
git push -u origin main
if ($LASTEXITCODE -ne 0) {
    Write-Host ''
    Write-Host '推送失败。若提示需要认证，请完成浏览器登录；' -ForegroundColor Red
    Write-Host '若提示仓库不存在，请先在 GitHub 创建该仓库。' -ForegroundColor Red
    exit 1
}

Write-Host ''
Write-Host '推送完成。' -ForegroundColor Green
Write-Host '接下来创建 Release：' -ForegroundColor Cyan
Write-Host "  https://github.com/GNX001/dimensionhome/releases/new?tag=v1.0.0" -ForegroundColor Cyan
Write-Host '附件请上传 build\libs\dimensionhome-1.0.0.jar' -ForegroundColor Cyan
