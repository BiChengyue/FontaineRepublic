param(
    [Parameter(Mandatory = $true)][string]$TaskName,
    [Parameter(Mandatory = $true)][string]$PromptFile,
    [string]$Wave = (Get-Date -Format 'yyyyMMdd-HHmmss'),
    [ValidateSet('dsh', 'reasonix')][string]$Engine = 'dsh',
    [string]$Model = 'opencode-go/deepseek-v4-flash',
    [int]$MaxSteps = 60,
    [string]$Cli = 'E:\Reasonix\versions\v1.21.2\reasonix-cli.exe',
    [string]$DshCli = '',
    [string]$DshHome = '',
    [string]$Worktree = ''
)

# FontaineRepublic dispatch runner.
# Usage:
#   powershell -File tools\dispatch-task.ps1 -TaskName tXX-xxx -PromptFile <TASK>-PROMPT.md
# Engines:
#   dsh      - DeepSeek Harness headless (default), OpenCode Go deepseek-v4-flash.
#   reasonix - legacy reasonix-cli run.
# Logs and metrics land in deepseek-worktrees-v2\dispatch-logs\<wave>\.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$logDir = Join-Path $root "deepseek-worktrees-v2\dispatch-logs\$Wave"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

if (-not (Test-Path -LiteralPath $PromptFile)) {
    throw "Prompt file not found: $PromptFile"
}
$prompt = Get-Content -LiteralPath $PromptFile -Raw -Encoding UTF8

Write-Output "[Dispatch] task=$TaskName model=$Model maxSteps=$MaxSteps wave=$Wave"
Write-Output "[Dispatch] log=$logDir"

if ($Engine -eq 'dsh') {
    if ($Model -notmatch 'deepseek-v4-flash') {
        Write-Output "[Dispatch] warning: dsh engine ignores -Model; the DSH_HOME settings pick the model (deepseek-v4-flash)"
    }
    if (-not $DshCli) {
        $candidate = Join-Path $env:USERPROFILE '.dsh\profiles\node_modules\@deepseek-ai\dsh\lib\bin.js'
        if (Test-Path -LiteralPath $candidate) {
            $DshCli = $candidate
        }
        else {
            $cmd = Get-Command dsh -ErrorAction SilentlyContinue
            if ($cmd) { $DshCli = $cmd.Source }
            else { throw "dsh CLI not found; pass -DshCli or install @deepseek-ai/dsh" }
        }
    }
    if (-not $DshHome) {
        $DshHome = Join-Path $root '.codex_tmp\dsh-home'
    }
    New-Item -ItemType Directory -Force -Path $DshHome | Out-Null
    $settingsTarget = Join-Path $DshHome 'settings.yaml'
    if (-not (Test-Path -LiteralPath $settingsTarget)) {
        $template = Join-Path $PSScriptRoot 'dsh-sub-settings.yaml'
        Copy-Item -LiteralPath $template -Destination $settingsTarget
        Write-Output "[Dispatch] initialized dsh settings from template"
    }
    $env:DSH_HOME = $DshHome
    if (-not $env:OPENCODE_GO_API_KEY) {
        $envFile = Join-Path $env:APPDATA 'reasonix\.env'
        if (Test-Path -LiteralPath $envFile) {
            $kv = Get-Content -LiteralPath $envFile | Select-String -Pattern '^OPENCODE_GO_API_KEY=(.+)$' | Select-Object -First 1
            if ($kv) { $env:OPENCODE_GO_API_KEY = $kv.Matches[0].Groups[1].Value }
        }
    }
    Write-Output "[Dispatch] engine=dsh home=$DshHome"
    if ($Worktree) {
        Push-Location $Worktree
        Write-Output "[Dispatch] worktree=$Worktree"
    }
    try {
        & node $DshCli --profile headless $prompt *> (Join-Path $logDir "$TaskName.out.log")
        $code = $LASTEXITCODE
    }
    finally {
        if ($Worktree) { Pop-Location }
    }
    Write-Output "[Dispatch] EXIT=$code"
    exit $code
}

# Legacy reasonix engine.
$env:REASONIX_METRICS_PATH = Join-Path $logDir "$TaskName.metrics.json"
$dirArg = @()
if ($Worktree) {
    $dirArg = @('--dir', $Worktree)
    Write-Output "[Dispatch] worktree=$Worktree"
}

& $Cli run --permission-mode auto --model $Model --max-steps $MaxSteps --output-format text -p @dirArg $prompt *> (Join-Path $logDir "$TaskName.out.log")
$code = $LASTEXITCODE
Write-Output "[Dispatch] EXIT=$code"
exit $code
