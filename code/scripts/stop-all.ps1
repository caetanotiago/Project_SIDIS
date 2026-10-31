# Pára todas as réplicas arrancadas por start-all.ps1, a partir dos ficheiros code\logs\*.pid.
# Cada PID é o processo powershell de run-instance.ps1: é terminada a árvore inteira (inclui a JVM).
# Uso (a partir de code\):  .\scripts\stop-all.ps1
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
$pidFiles = Get-ChildItem "logs\*.pid" -ErrorAction SilentlyContinue
if (-not $pidFiles) { Write-Host "Nada a parar (nao ha ficheiros logs\*.pid)."; return }
foreach ($f in $pidFiles) {
    $id = [int](Get-Content $f.FullName)
    if (Get-Process -Id $id -ErrorAction SilentlyContinue) {
        & taskkill.exe /PID $id /T /F | Out-Null
        Write-Host "Parado $($f.BaseName) (PID $id)"
    } else {
        Write-Host "$($f.BaseName) ja nao estava a correr (PID $id)"
    }
    Remove-Item $f.FullName
}
