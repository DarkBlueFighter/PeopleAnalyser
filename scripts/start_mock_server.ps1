# 快速啟動 Mock Server 腳本
# 簡化版，直接啟動 Mock Server 並顯示連線資訊

param(
    [int]$Port = 8000
)

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  PeopleAnalyser Mock Server Launcher" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 檢查 Python 是否可用
Write-Host "檢查 Python..." -ForegroundColor Yellow
try {
    # 優先嘗試 python3，如果不存在則使用 python
    $pythonCmd = if (Get-Command python3 -ErrorAction SilentlyContinue) { "python3" } else { "python" }
    $pythonVersion = & $pythonCmd --version 2>&1
    Write-Host "  ✓ Python 已安裝: $pythonVersion" -ForegroundColor Green
} catch {
    Write-Host "  ✗ 找不到 Python" -ForegroundColor Red
    Write-Host ""
    Write-Host "請先安裝 Python 3:" -ForegroundColor Yellow
    Write-Host "  https://www.python.org/downloads/" -ForegroundColor White
    Write-Host ""
    exit 1
}

# 檢查 mock_server.py 是否存在
$scriptPath = Join-Path $PSScriptRoot "mock_server.py"
if (-not (Test-Path $scriptPath)) {
    Write-Host "  ✗ 找不到 mock_server.py" -ForegroundColor Red
    Write-Host "    預期路徑: $scriptPath" -ForegroundColor Gray
    Write-Host ""
    exit 1
}

Write-Host "  ✓ 找到 mock_server.py" -ForegroundColor Green
Write-Host ""

# 顯示說明
Write-Host "Mock Server 啟動資訊:" -ForegroundColor Cyan
Write-Host "  Port: $Port" -ForegroundColor White
Write-Host ""
Write-Host "測試方式:" -ForegroundColor Cyan
Write-Host "  1. 本機測試:" -ForegroundColor Yellow
Write-Host "     開啟瀏覽器前往 http://127.0.0.1:$Port/" -ForegroundColor White
Write-Host ""
Write-Host "  2. BlueStacks 測試 (方式 A - 推薦):" -ForegroundColor Yellow
Write-Host "     在 BlueStacks 瀏覽器中開啟 http://10.0.2.2:$Port/" -ForegroundColor White
Write-Host ""
Write-Host "  3. BlueStacks 測試 (方式 B - 需 ADB):" -ForegroundColor Yellow
Write-Host "     a. 連接 ADB: adb connect 127.0.0.1:5555" -ForegroundColor White
Write-Host "     b. 設定轉發: adb reverse tcp:$Port tcp:$Port" -ForegroundColor White
Write-Host "     c. App 使用: http://localhost:$Port/" -ForegroundColor White
Write-Host ""
Write-Host "按 Ctrl+C 停止 Mock Server" -ForegroundColor Gray
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 啟動 Mock Server
try {
    & $pythonCmd $scriptPath $Port
} catch {
    Write-Host ""
    Write-Host "Mock Server 啟動失敗: $_" -ForegroundColor Red
    exit 1
}
