# Сборка/тесты ClockWeather с читаемым логом (cmd-редирект, без обрезки строк).
#   powershell -ExecutionPolicy Bypass -File tools/run.ps1 test
#   powershell -ExecutionPolicy Bypass -File tools/run.ps1 apk
param([string]$task = "test")

$ErrorActionPreference = "Stop"
$proj = "C:\Users\rioor\Documents\DeepSeek Harness\ClockWeather"
$ab   = "C:\Users\rioor\Documents\DeepSeek Harness\.android-build"

$env:JAVA_HOME         = "$ab\downloads\jdk17\jdk-17.0.21+9"
$env:ANDROID_HOME      = "$ab\android-sdk"
$env:ANDROID_USER_HOME = "$ab\android-user-home"
$env:GRADLE_USER_HOME  = "$ab\gradle-home"
$env:TEMP = "$proj\.tmp"; $env:TMP = "$proj\.tmp"
Remove-Item Env:\ANDROID_SDK_HOME -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$proj\.tmp" | Out-Null

$gradle = "$ab\downloads\gradle\gradle-8.13\bin\gradle.bat"
$target = switch ($task) {
    "apk"  { ":app:assembleProd" }
    "both" { ":app:testDebugUnitTest :app:assembleProd" }
    default { ":app:testDebugUnitTest" }
}
$log = "$proj\.tmp\build-$task.log"

& cmd.exe /c "`"$gradle`" -p `"$proj`" $target --console=plain > `"$log`" 2>&1"
$code = $LASTEXITCODE

if (Test-Path $log) {
    $txt = Get-Content $log -Raw
    $errs = [regex]::Matches($txt, "e: [^\r\n]+")
    if ($errs.Count -gt 0) {
        "--- ошибки компиляции ($($errs.Count)) ---"
        $errs | Select-Object -First 25 | ForEach-Object {
            $_.Value -replace ".*clockweather/", "" -replace "^e: ", ""
        }
    }
    Get-Content $log | Select-String -Pattern "BUILD (SUCCESSFUL|FAILED)|FAILED$|tests completed|> Task :app:assemble" |
        Select-Object -Last 6 | ForEach-Object { $_.Line }
}
"EXIT=$code"
exit $code
