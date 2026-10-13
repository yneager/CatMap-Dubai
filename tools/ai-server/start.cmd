@echo off
rem CatMap AI Server: double-click to start. The first run installs Python packages and downloads the model (~350 MB).
rem   start.cmd             the real CatMap project (needs app\google-services.json and worker.local.json)
rem   start.cmd --emulator  the local Firebase emulators (testing)
cd /d "%~dp0"
if not exist ".venv\Scripts\pythonw.exe" (
  echo First run: setting up the CatMap AI Server, this takes a few minutes...
  python -m venv .venv || (echo Python 3 is needed: https://www.python.org/downloads/ & pause & exit /b 1)
  ".venv\Scripts\python.exe" -m pip install --upgrade pip
  ".venv\Scripts\python.exe" -m pip install -r requirements.txt || (pause & exit /b 1)
  echo Downloading the recognition model...
  ".venv\Scripts\python.exe" -c "from huggingface_hub import hf_hub_download; hf_hub_download('open-noodle/pet-recognition-base','recognition/model.onnx',cache_dir='models')"
)
start "" ".venv\Scripts\pythonw.exe" server.py %*
