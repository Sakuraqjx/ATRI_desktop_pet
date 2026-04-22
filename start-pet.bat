@echo off
setlocal

cd /d "%~dp0"

set "MODEL_ID=atri"

:parse_args
if "%~1"=="" goto after_args
if /I "%~1"=="--check" (
    set "CHECK_ONLY=1"
    shift
    goto parse_args
)
if /I "%~1"=="--model" (
    if not "%~2"=="" (
        set "MODEL_ID=%~2"
        shift
    )
    shift
    goto parse_args
)
shift
goto parse_args

:after_args
where mvn >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Maven was not found in PATH.
    echo Please make sure `mvn -version` works in your terminal.
    pause
    exit /b 1
)

where npm >nul 2>nul
if errorlevel 1 (
    echo [ERROR] npm was not found in PATH.
    echo Please make sure `npm -v` works in your terminal.
    pause
    exit /b 1
)

if defined CHECK_ONLY (
    echo [INFO] Startup script check passed for model "%MODEL_ID%".
    exit /b 0
)

if not exist "desktop-shell\node_modules\electron" (
    echo [INFO] Installing Electron shell dependencies...
    pushd desktop-shell
    call npm install
    if errorlevel 1 (
        popd
        echo [ERROR] Failed to install desktop shell dependencies.
        pause
        exit /b 1
    )
    popd
)

echo [INFO] Launching Java desktop pet backend for model "%MODEL_ID%"...
mvn -q -Ddesktop.pet.model=%MODEL_ID% compile exec:java 1> desktop-pet.out.log 2> desktop-pet.err.log

if errorlevel 1 (
    echo.
    echo [ERROR] Launch failed.
    echo [ERROR] Logs: desktop-pet.out.log, desktop-pet.err.log
    pause
    exit /b 1
)

endlocal
