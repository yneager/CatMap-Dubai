# Starts the CatMap AI server on this PC (127.0.0.1:8765 only) and connects a USB-attached phone to it.
# The first run creates a virtual environment and downloads the model (about 350 MB).
#   .\tools\ai-server\start.ps1            real project (phone with USB debugging on, plugged in)
#   .\tools\ai-server\start.ps1 -Emulator  local Firebase emulators (testing)
param([switch]$Emulator, [switch]$Cpu)
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$python = Join-Path $here ".venv\Scripts\python.exe"
if (-not (Test-Path $python)) {
    python -m venv (Join-Path $here ".venv")
    & $python -m pip install --upgrade pip
    & $python -m pip install -r (Join-Path $here "requirements.txt")
}
# The phone reaches the PC through the USB cable: its 127.0.0.1:8765 is forwarded to this PC's 127.0.0.1:8765.
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }
& $adb reverse tcp:8765 tcp:8765
if ($LASTEXITCODE -ne 0) { Write-Host "No phone connected yet. Plug it in and run: adb reverse tcp:8765 tcp:8765" }
$a = @()
if ($Emulator) { $a += "--emulator" }
if ($Cpu) { $a += "--cpu" }
& $python (Join-Path $here "server.py") @a
