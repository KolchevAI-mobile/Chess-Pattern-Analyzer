<#
.SYNOPSIS
    Скачивает официальную сборку Stockfish для Windows в ml/tools.

.DESCRIPTION
    Бинарник большой (~80 МБ) и распространяется под GPLv3, поэтому в репозиторий
    он не попадает (ml/tools в .gitignore). Скрипт печатает готовую строку для ml/.env.

.EXAMPLE
    .\scripts\fetch_stockfish.ps1
#>
param(
    [string]$Version = "sf_19",
    [string]$Asset = "stockfish-windows-x86-64-universal.zip"
)

$ErrorActionPreference = "Stop"

$mlRoot = Split-Path -Parent $PSScriptRoot
$tools = Join-Path $mlRoot "tools"
$zip = Join-Path $tools "stockfish.zip"
$dest = Join-Path $tools "stockfish"
$url = "https://github.com/official-stockfish/Stockfish/releases/download/$Version/$Asset"

New-Item -ItemType Directory -Force $tools | Out-Null

Write-Host "Скачивание: $url"
Invoke-WebRequest -Uri $url -OutFile $zip

if (Test-Path $dest) { Remove-Item -LiteralPath $dest -Recurse -Force }
Expand-Archive -LiteralPath $zip -DestinationPath $dest -Force

$exe = Get-ChildItem $dest -Recurse -Filter "stockfish*.exe" | Select-Object -First 1
if (-not $exe) { throw "В архиве не найден исполняемый файл Stockfish" }

Write-Host ""
Write-Host "Готово: $($exe.FullName)"
Write-Host "Добавьте в ml/.env:"
Write-Host "STOCKFISH_PATH=$($exe.FullName)"
