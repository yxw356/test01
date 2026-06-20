#Requires -RunAsAdministrator
param(
    [string]$ServerUrl = "http://127.0.0.1:8443",
    [int]$ListenPort = 5001,
    [string]$InstallPath = "C:\Program Files\IntranetFileShare\Agent"
)

$ErrorActionPreference = "Stop"

Write-Host "Installing IntranetFileShare Agent..."

New-Item -ItemType Directory -Force -Path $InstallPath | Out-Null
Copy-Item -Path "$PSScriptRoot\..\src\IntranetFileShare.Agent\bin\Release\net8.0\*" -Destination $InstallPath -Recurse -Force

$configPath = Join-Path $InstallPath "agent.json"
if (-not (Test-Path $configPath)) {
    @{
        ServerUrl = $ServerUrl
        ListenPort = $ListenPort
        AgentId = 0
        AgentKey = ""
        MachineName = $env:COMPUTERNAME
        Shares = @()
    } | ConvertTo-Json -Depth 5 | Set-Content $configPath -Encoding UTF8
}

$serviceName = "IntranetFileShareAgent"
$exePath = Join-Path $InstallPath "IntranetFileShare.Agent.exe"

if (Get-Service -Name $serviceName -ErrorAction SilentlyContinue) {
    Stop-Service -Name $serviceName -Force
    sc.exe delete $serviceName | Out-Null
    Start-Sleep -Seconds 2
}

sc.exe create $serviceName binPath= "`"$exePath`"" start= auto DisplayName= "Intranet File Share Agent"
sc.exe description $serviceName "内网文件共享 Agent - 代理本地目录访问"
Start-Service -Name $serviceName

# 防火墙放行 Agent 端口（内网）
New-NetFirewallRule -DisplayName "IntranetFileShare Agent" -Direction Inbound -Protocol TCP -LocalPort $ListenPort -Action Allow -ErrorAction SilentlyContinue | Out-Null

Write-Host "Agent installed and started on port $ListenPort"
Write-Host "Edit $configPath to bind ShareId -> LocalPath mappings"
