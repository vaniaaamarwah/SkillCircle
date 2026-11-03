# Compile and run ALL automated tests. Run from inside the SkillCircle folder:
#     powershell -ExecutionPolicy Bypass -File .\run-tests.ps1
# The tests use TEMPORARY databases, so your real data in data\ is never touched.
# Expected result:  DatabaseSmokeTest 35 passed, 0 failed
#                   AuthenticationTest 146 passed, 0 failed
#                   DatabaseFlowTest 100 passed, 0 failed

# 0. Are we in the right folder?
if (-not (Test-Path "src") -or -not (Test-Path "test") -or -not (Test-Path "lib")) {
    Write-Host "ERROR: run this from inside the SkillCircle folder (the one that contains src, test and lib)." -ForegroundColor Red
    Write-Host "Current folder: $((Get-Location).Path)"
    exit 1
}

# 1. Refuse to build if any source or test file is EMPTY (0 bytes) or only blanks
$empty = @(Get-ChildItem -Path src, test -Recurse -Filter *.java | Where-Object {
    $_.Length -eq 0 -or [string]::IsNullOrWhiteSpace((Get-Content -LiteralPath $_.FullName -Raw))
})
if ($empty.Count -gt 0) {
    Write-Host "ERROR: these files are EMPTY:" -ForegroundColor Red
    $empty | ForEach-Object { Write-Host $_.FullName }
    Write-Host "Copy the complete files from the ZIP again, then run this script again."
    exit 1
}

if (-not (Test-Path "lib\sqlite-jdbc-3.53.4.0.jar")) {
    Write-Host "ERROR: lib\sqlite-jdbc-3.53.4.0.jar is missing. See lib\README.txt." -ForegroundColor Red
    exit 1
}

# 2. Fresh build folders
if (Test-Path out) { Remove-Item -Recurse -Force out }
if (Test-Path out-test) { Remove-Item -Recurse -Force out-test }
New-Item -ItemType Directory -Path out | Out-Null
New-Item -ItemType Directory -Path out-test | Out-Null

# 3. Compile the program, then the tests
javac -cp "lib\*" -d out (Get-ChildItem -Path src -Recurse -Filter *.java).FullName
if ($LASTEXITCODE -ne 0) { Write-Host "Compile failed." -ForegroundColor Red; exit 1 }

javac -cp "out;lib\*" -d out-test (Get-ChildItem -Path test -Recurse -Filter *.java).FullName
if ($LASTEXITCODE -ne 0) { Write-Host "Test compile failed." -ForegroundColor Red; exit 1 }

# 4. Run both test programs (the second one also starts small Java programs of its own
#    to prove that data survives a real restart; this takes a few seconds)
Write-Host ""
Write-Host "===== DatabaseSmokeTest =====" -ForegroundColor Cyan
java -cp "out;out-test;lib\*" DatabaseSmokeTest
$smokeResult = $LASTEXITCODE

Write-Host ""
Write-Host "===== AuthenticationTest =====" -ForegroundColor Cyan
java -cp "out;out-test;lib\*" AuthenticationTest
$authResult = $LASTEXITCODE

Write-Host ""
Write-Host "===== DatabaseFlowTest =====" -ForegroundColor Cyan
java -cp "out;out-test;lib\*" DatabaseFlowTest
$flowResult = $LASTEXITCODE

Write-Host ""
if ($smokeResult -eq 0 -and $authResult -eq 0 -and $flowResult -eq 0) {
    Write-Host "ALL TESTS PASSED" -ForegroundColor Green
    exit 0
} else {
    Write-Host "SOME TESTS FAILED: scroll up and look for lines that start with FAIL." -ForegroundColor Red
    exit 1
}
