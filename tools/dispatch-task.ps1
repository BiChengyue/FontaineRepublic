param(
    [Parameter(Mandatory = $true)][string]$TaskName,
    [Parameter(Mandatory = $true)][string]$PromptFile,
    [string]$Wave = (Get-Date -Format 'yyyyMMdd-HHmmss'),
    [string]$Model = 'opencode-go/deepseek-v4-flash',
    [int]$MaxSteps = 60,
    [string]$Cli = 'E:\Reasonix\versions\v1.25.0\reasonix-cli.exe'
)

# FontaineRepublic dispatch runner.
# Usage:
#   powershell -File tools\dispatch-task.ps1 -TaskName tXX-xxx -PromptFile <TASK>-PROMPT.md
# Logs and metrics land in deepseek-worktrees-v2\dispatch-logs\<wave>\.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$logDir = Join-Path $root "deepseek-worktrees-v2\dispatch-logs\$Wave"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

if (-not (Test-Path -LiteralPath $PromptFile)) {
    throw "Prompt file not found: $PromptFile"
}
$prompt = Get-Content -LiteralPath $PromptFile -Raw -Encoding UTF8

$env:REASONIX_METRICS_PATH = Join-Path $logDir "$TaskName.metrics.json"
Write-Output "[Dispatch] task=$TaskName model=$Model maxSteps=$MaxSteps wave=$Wave"
Write-Output "[Dispatch] log=$logDir"

& $Cli --permission-mode auto run --model $Model --max-steps $MaxSteps $prompt *> (Join-Path $logDir "$TaskName.out.log")
$code = $LASTEXITCODE
Write-Output "[Dispatch] EXIT=$code"
exit $code
