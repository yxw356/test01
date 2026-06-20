# 重新打包 Windows x64 发布版（在 Windows 或 macOS 上运行）
param(
    [string]$Configuration = "Release",
    [string]$Runtime = "win-x64"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Release = Join-Path $Root "release"

Write-Host "Publishing to $Release ..."

$projects = @(
    @{ Name = "Server"; Path = "src/IntranetFileShare.Server/IntranetFileShare.Server.csproj" },
    @{ Name = "Agent";  Path = "src/IntranetFileShare.Agent/IntranetFileShare.Agent.csproj" },
    @{ Name = "Admin";  Path = "src/IntranetFileShare.Admin/IntranetFileShare.Admin.csproj" },
    @{ Name = "Client"; Path = "src/IntranetFileShare.Client/IntranetFileShare.Client.csproj" }
)

Push-Location $Root
try {
    foreach ($p in $projects) {
        $out = Join-Path $Release $p.Name
        Write-Host ">> $($p.Name)"
        dotnet publish $p.Path -c $Configuration -r $Runtime `
            --self-contained true `
            -p:PublishSingleFile=true `
            -p:IncludeNativeLibrariesForSelfExtract=true `
            -o $out
    }

    Copy-Item "src/IntranetFileShare.Agent/agent.json.example" (Join-Path $Release "Agent/") -Force

    Push-Location $Release
    foreach ($d in @("Server","Admin","Client","Agent")) {
        if (Test-Path "IntranetFileShare-$d-win-x64.zip") { Remove-Item "IntranetFileShare-$d-win-x64.zip" }
        Compress-Archive -Path $d -DestinationPath "IntranetFileShare-$d-win-x64.zip"
    }
    if (Test-Path "IntranetFileShare-Full-win-x64.zip") { Remove-Item "IntranetFileShare-Full-win-x64.zip" }
    Compress-Archive -Path @("Server","Admin","Client","Agent","install.bat","README.md") `
        -DestinationPath "IntranetFileShare-Full-win-x64.zip"

    Write-Host "Done. See release/ folder."
}
finally {
    Pop-Location
    Pop-Location
}
