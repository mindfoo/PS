# test-windows.ps1 — PowerShell script for testing SCRIPT tasks
# Usage: powershell -File test-windows.ps1 -Name "World"

param(
    [string]$Name = "Guest"
)

Write-Host "Hello from PowerShell, $Name!"
Write-Host "Arguments: $args"
Write-Host "Working directory: $PWD"
Write-Host "PowerShell version: $($PSVersionTable.PSVersion)"
Write-Host "OS: $([System.Environment]::OSVersion.VersionString)"

# Return JSON output
$result = @{
    greeting = "Hello, $Name"
    timestamp = Get-Date -Format "o"
    platform = "Windows PowerShell"
}
$result | ConvertTo-Json

exit 0

