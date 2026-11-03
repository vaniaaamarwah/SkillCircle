# Build and run SkillCircle. Run this from inside the SkillCircle folder
# (the folder that contains src, lib and run.ps1; check with:  dir):
#     powershell -ExecutionPolicy Bypass -File .\run.ps1
#
# Options:
#   -NoDemo   do not add the 5 demo students (they are only ever added once anyway)
#   -DbPath   keep the database somewhere else, for example outside OneDrive:
#     powershell -ExecutionPolicy Bypass -File .\run.ps1 -DbPath C:\SkillCircleData\skillcircle.db
param(
    [string]$DbPath = "",
    [switch]$NoDemo
)

# 0. Are we in the right folder?
if (-not (Test-Path "src") -or -not (Test-Path "lib")) {
    Write-Host "ERROR: run this from inside the SkillCircle folder (the one that contains src and lib)." -ForegroundColor Red
    Write-Host "Current folder: $((Get-Location).Path)"
    Write-Host "Type 'dir' to look around, then 'cd' into the right folder."
    exit 1
}

# 1. Refuse to build if any source file is EMPTY (0 bytes) or contains only blanks
$empty = @(Get-ChildItem -Path src -Recurse -Filter *.java | Where-Object {
    $_.Length -eq 0 -or [string]::IsNullOrWhiteSpace((Get-Content -LiteralPath $_.FullName -Raw))
})
if ($empty.Count -gt 0) {
    Write-Host "ERROR: these source files are EMPTY:" -ForegroundColor Red
    $empty | ForEach-Object { Write-Host $_.FullName }
    Write-Host "Copy the complete files from the ZIP again, then run this script again."
    exit 1
}

# 2. Make sure the important files exist (catches a half-copied project)
$required = @(
    "src\Main.java",
    "src\dao\StudentDAO.java", "src\dao\AppMetaDAO.java", "src\dao\DashboardDAO.java",
    "src\database\DatabaseConnection.java", "src\database\DatabaseInitializer.java",
    "src\service\AuthenticationService.java", "src\service\PasswordHasher.java",
    "src\service\DemoDataSeeder.java", "src\ui\LoginPage.java", "src\ui\MainFrame.java"
)
$missing = @($required | Where-Object { -not (Test-Path $_) })
if ($missing.Count -gt 0) {
    Write-Host "ERROR: these files are missing:" -ForegroundColor Red
    $missing | ForEach-Object { Write-Host $_ }
    exit 1
}

# 3. The SQLite driver must be present
if (-not (Test-Path "lib\sqlite-jdbc-3.53.4.0.jar")) {
    Write-Host "ERROR: lib\sqlite-jdbc-3.53.4.0.jar is missing. See lib\README.txt." -ForegroundColor Red
    exit 1
}

# 4. Java must be installed
if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
    Write-Host "ERROR: javac was not found. Install a JDK (version 17 or newer) and reopen PowerShell." -ForegroundColor Red
    exit 1
}

# A friendly warning: SQLite databases inside OneDrive can get locked while OneDrive syncs
if ($DbPath -eq "" -and (Get-Location).Path -like "*OneDrive*") {
    Write-Host "WARNING: this project is inside OneDrive. If you ever see 'database is locked'," -ForegroundColor Yellow
    Write-Host "         move the project to C:\Projects\SkillCircle or use -DbPath (see top of this file)." -ForegroundColor Yellow
}

# 5. Fresh build folder
if (Test-Path out) { Remove-Item -Recurse -Force out }
New-Item -ItemType Directory -Path out | Out-Null

# 6. Compile every .java file under src
javac -cp "lib\*" -d out (Get-ChildItem -Path src -Recurse -Filter *.java).FullName
if ($LASTEXITCODE -ne 0) {
    Write-Host "Compile failed. Fix the errors shown above." -ForegroundColor Red
    exit 1
}

# 7. Run (out = our classes, lib\* = the SQLite driver)
Write-Host "Compiled OK. Starting SkillCircle..." -ForegroundColor Green
$javaArgs = @()
if ($DbPath -ne "") { $javaArgs += "-Dskillcircle.db=$DbPath" }
$javaArgs += @("-cp", "out;lib\*", "Main")
if (-not $NoDemo) { $javaArgs += "--seed-demo" }    # adds the 5 demo students, only once
& java @javaArgs
