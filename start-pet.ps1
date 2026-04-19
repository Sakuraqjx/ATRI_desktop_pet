$CheckOnly = $args -contains "--check"

$ErrorActionPreference = "Stop"
Set-Location -LiteralPath $PSScriptRoot

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Host "[ERROR] Maven was not found in PATH." -ForegroundColor Red
    Write-Host "Please make sure 'mvn -version' works in your terminal."
    exit 1
}

if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
    Write-Host "[ERROR] npm was not found in PATH." -ForegroundColor Red
    Write-Host "Please make sure 'npm -v' works in your terminal."
    exit 1
}

if ($CheckOnly) {
    Write-Host "[INFO] Startup script check passed." -ForegroundColor Green
    exit 0
}

if (-not (Test-Path "desktop-shell\node_modules\electron")) {
    Write-Host "[INFO] Installing Electron shell dependencies..." -ForegroundColor Cyan
    Push-Location desktop-shell
    try {
        npm install
    }
    finally {
        Pop-Location
    }
}

Write-Host "[INFO] Launching Java desktop pet backend..." -ForegroundColor Cyan
mvn -q compile exec:java 1> desktop-pet.out.log 2> desktop-pet.err.log
