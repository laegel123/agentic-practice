<#
  measure.ps1 - compute static health metrics of the legacy codebase.
  Usage:  pwsh ./measure.ps1            (default root: ../../legacy-ecommerce)
          pwsh ./measure.ps1 -Root <path>
  Use the output to fill the baseline/after columns of static-scorecard.md.
  (Labels are intentionally ASCII to avoid console code-page mojibake.)
#>
param([string]$Root)

if (-not $Root) { $Root = Join-Path $PSScriptRoot "..\..\legacy-ecommerce" }
$Root = (Resolve-Path $Root).Path

function LineCount($path) { (Get-Content -LiteralPath $path | Measure-Object -Line).Lines }
function Has($rel) { Test-Path (Join-Path $Root $rel) }
function CountMatch($pattern, $scope) {
  $files = Get-ChildItem -Path $Root -Recurse -Filter *.java -ErrorAction SilentlyContinue | Where-Object { $_.FullName -match $scope }
  ($files | Select-String -Pattern $pattern -ErrorAction SilentlyContinue | Measure-Object).Count
}

$allJava  = Get-ChildItem -Path $Root -Recurse -Filter *.java -ErrorAction SilentlyContinue
$mainJava = $allJava | Where-Object { $_.FullName -match '\\src\\main\\' }
$testJava = $allJava | Where-Object { $_.FullName -match '\\src\\test\\' }

$withLoc  = $mainJava | ForEach-Object { [pscustomobject]@{ File = $_.FullName.Substring($Root.Length + 1); Loc = (LineCount $_.FullName) } }
$totalLoc = [int]((($withLoc | Measure-Object -Property Loc -Sum).Sum))
$big      = @($withLoc | Where-Object { $_.Loc -gt 300 })
$avgLoc   = if ($mainJava.Count) { [math]::Round($totalLoc / $mainJava.Count, 1) } else { 0 }
$modules  = (Get-ChildItem $Root -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'build.gradle') }).Count

Write-Host "================ static metrics (baseline) ================"
Write-Host ("root                 : {0}" -f $Root)
Write-Host ("modules              : {0}" -f $modules)
Write-Host ("main .java files     : {0}" -f $mainJava.Count)
Write-Host ("main total LOC       : {0}" -f $totalLoc)
Write-Host ("avg LOC/file         : {0}" -f $avgLoc)
Write-Host ("files > 300 LOC      : {0}" -f $big.Count)
Write-Host ("test .java files     : {0}   <- tests present?" -f $testJava.Count)
Write-Host ""
Write-Host "largest files (top 8):"
$withLoc | Sort-Object Loc -Descending | Select-Object -First 8 | ForEach-Object { "  {0,5}  {1}" -f $_.Loc, $_.File }

Write-Host ""
Write-Host "----- smell approximations (grep) -----"
Write-Host ("System.out.println       : {0}" -f (CountMatch 'System\.out\.println' '\\src\\main\\'))
Write-Host ("'double' declarations    : {0}" -f (CountMatch '\bdouble\b' '\\src\\main\\'))
Write-Host ("@SuppressWarnings        : {0}" -f (CountMatch '@SuppressWarnings' '\\src\\main\\'))
Write-Host ("catch blocks             : {0}" -f (CountMatch 'catch\s*\(' '\\src\\main\\'))

Write-Host ""
Write-Host "----- harness / agentic affordances present? -----"
$spotlessHit = (Get-ChildItem $Root -Recurse -Filter *.gradle -ErrorAction SilentlyContinue |
  Select-String -Pattern 'spotless|checkstyle|spotbugs|pmd|jacoco' -ErrorAction SilentlyContinue | Measure-Object).Count -gt 0
$checks = @(
  @{ n = 'CLAUDE.md';                  p = (Has 'CLAUDE.md') },
  @{ n = '.claude/settings.json';      p = (Has '.claude\settings.json') },
  @{ n = '.github/workflows (CI)';     p = (Has '.github\workflows') },
  @{ n = 'gradle/libs.versions.toml';  p = (Has 'gradle\libs.versions.toml') },
  @{ n = 'static-analysis/format cfg'; p = $spotlessHit },
  @{ n = 'docs/ folder';               p = (Has 'docs') },
  @{ n = 'tests present';              p = ($testJava.Count -gt 0) }
)
foreach ($c in $checks) { "  [{0}] {1}" -f $(if ($c.p) { 'O' } else { 'X' }), $c.n }
Write-Host "=========================================================="
