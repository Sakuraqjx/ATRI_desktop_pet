$CheckOnly = $false
$ModelId = "atri"

for ($i = 0; $i -lt $args.Count; $i++) {
    switch ($args[$i]) {
        "--check" {
            $CheckOnly = $true
        }
        "--model" {
            if ($i + 1 -lt $args.Count) {
                $ModelId = $args[$i + 1]
                $i += 1
            }
        }
    }
}

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
    Write-Host "[INFO] Startup script check passed for model '$ModelId'." -ForegroundColor Green
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

Write-Host "[INFO] Launching Java desktop pet backend for model '$ModelId'..." -ForegroundColor Cyan
mvn -q "-Ddesktop.pet.model=$ModelId" compile exec:java 1> desktop-pet.out.log 2> desktop-pet.err.log
