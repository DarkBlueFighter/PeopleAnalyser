# BlueStacks 連線診斷腳本
# 用於診斷 Android 模擬器與本機 Mock Server 的連線問題

param(
    [int]$Port = 8000,
    [string]$AdbPath = "adb"
)

Write-Host "================================" -ForegroundColor Cyan
Write-Host "BlueStacks 連線診斷工具" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# 1. 檢查 Mock Server 是否在執行
Write-Host "[1/6] 檢查本機 Mock Server (Port $Port)..." -ForegroundColor Yellow
$serverRunning = $false
try {
    $netstat = netstat -ano | Select-String ":$Port"
    if ($netstat) {
        Write-Host "  ✓ Port $Port 正在監聽" -ForegroundColor Green
        Write-Host "    $netstat" -ForegroundColor Gray
        $serverRunning = $true
    } else {
        Write-Host "  ✗ Port $Port 未在監聽" -ForegroundColor Red
        Write-Host "    請先啟動 Mock Server: python scripts\mock_server.py" -ForegroundColor Yellow
    }
} catch {
    Write-Host "  ✗ 無法檢查 port 狀態: $_" -ForegroundColor Red
}
Write-Host ""

# 2. 測試本機連線
Write-Host "[2/6] 測試本機連線..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/" -TimeoutSec 3 -UseBasicParsing
    if ($response.StatusCode -eq 200) {
        Write-Host "  ✓ 本機連線成功 (HTTP 200)" -ForegroundColor Green
    } else {
        Write-Host "  ! 本機連線回應異常 (HTTP $($response.StatusCode))" -ForegroundColor Yellow
    }
} catch {
    Write-Host "  ✗ 本機連線失敗: $($_.Exception.Message)" -ForegroundColor Red
    if (-not $serverRunning) {
        Write-Host "    請先啟動 Mock Server" -ForegroundColor Yellow
    }
}
Write-Host ""

# 3. 檢查 ADB 是否可用
Write-Host "[3/6] 檢查 ADB..." -ForegroundColor Yellow
$adbAvailable = $false
try {
    $adbVersion = & $AdbPath version 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  ✓ ADB 可用" -ForegroundColor Green
        Write-Host "    版本: $($adbVersion | Select-Object -First 1)" -ForegroundColor Gray
        $adbAvailable = $true
    } else {
        Write-Host "  ✗ ADB 執行失敗" -ForegroundColor Red
    }
} catch {
    Write-Host "  ✗ 找不到 ADB: $_" -ForegroundColor Red
    Write-Host "    請確認已安裝 Android SDK Platform Tools" -ForegroundColor Yellow
    Write-Host "    或指定 ADB 路徑: -AdbPath '.\platform-tools\adb.exe'" -ForegroundColor Yellow
}
Write-Host ""

# 4. 檢查已連接的裝置
Write-Host "[4/6] 檢查已連接的裝置..." -ForegroundColor Yellow
$deviceConnected = $false
if ($adbAvailable) {
    try {
        $devices = & $AdbPath devices 2>&1 | Select-String -Pattern "^\w+\s+device$"
        if ($devices) {
            Write-Host "  ✓ 已連接裝置:" -ForegroundColor Green
            $devices | ForEach-Object {
                Write-Host "    - $_" -ForegroundColor Gray
            }
            $deviceConnected = $true
        } else {
            Write-Host "  ✗ 未偵測到已連接的裝置" -ForegroundColor Red
            Write-Host "    BlueStacks 連接步驟:" -ForegroundColor Yellow
            Write-Host "      1. 開啟 BlueStacks" -ForegroundColor Gray
            Write-Host "      2. 設定 → 進階 → 啟用 Android Debug Bridge (ADB)" -ForegroundColor Gray
            Write-Host "      3. 記下 ADB 連接位址 (通常是 127.0.0.1:5555)" -ForegroundColor Gray
            Write-Host "      4. 執行: adb connect 127.0.0.1:5555" -ForegroundColor Gray
        }
    } catch {
        Write-Host "  ✗ 無法列出裝置: $_" -ForegroundColor Red
    }
}
Write-Host ""

# 5. 檢查 adb reverse 設定
Write-Host "[5/6] 檢查 adb reverse 設定..." -ForegroundColor Yellow
if ($adbAvailable -and $deviceConnected) {
    try {
        $reverseList = & $AdbPath reverse --list 2>&1
        $reverseExists = $reverseList | Select-String "tcp:$Port"
        
        if ($reverseExists) {
            Write-Host "  ✓ adb reverse tcp:$Port 已設定" -ForegroundColor Green
            Write-Host "    $reverseExists" -ForegroundColor Gray
        } else {
            Write-Host "  ! adb reverse tcp:$Port 未設定" -ForegroundColor Yellow
            Write-Host "    設定方式:" -ForegroundColor Yellow
            Write-Host "      adb reverse tcp:$Port tcp:$Port" -ForegroundColor Gray
            Write-Host ""
            
            # 詢問是否要自動設定
            $answer = Read-Host "    是否要現在設定? (y/N)"
            if ($answer -eq 'y' -or $answer -eq 'Y') {
                Write-Host "  正在設定 adb reverse..." -ForegroundColor Yellow
                $reverseResult = & $AdbPath reverse tcp:$Port tcp:$Port 2>&1
                if ($LASTEXITCODE -eq 0) {
                    Write-Host "  ✓ adb reverse 設定成功" -ForegroundColor Green
                } else {
                    Write-Host "  ✗ adb reverse 設定失敗: $reverseResult" -ForegroundColor Red
                }
            }
        }
    } catch {
        Write-Host "  ✗ 無法檢查 adb reverse: $_" -ForegroundColor Red
    }
} else {
    Write-Host "  - 跳過 (ADB 或裝置未就緒)" -ForegroundColor Gray
}
Write-Host ""

# 6. 檢查 Windows 防火牆
Write-Host "[6/6] 檢查 Windows 防火牆..." -ForegroundColor Yellow
try {
    # 檢查 Python 是否被防火牆阻擋
    $pythonRules = Get-NetFirewallApplicationFilter | Where-Object {
        $_.Program -like "*python*"
    } | Get-NetFirewallRule | Where-Object {
        $_.Enabled -eq $true -and $_.Direction -eq "Inbound"
    }
    
    if ($pythonRules) {
        Write-Host "  ✓ 找到 Python 的防火牆規則" -ForegroundColor Green
        $pythonRules | Select-Object -First 3 | ForEach-Object {
            Write-Host "    - $($_.DisplayName)" -ForegroundColor Gray
        }
    } else {
        Write-Host "  ! 未找到 Python 的防火牆規則" -ForegroundColor Yellow
        Write-Host "    可能需要手動新增防火牆規則允許 Python 接受連線" -ForegroundColor Yellow
        Write-Host "    或在防火牆提示時選擇「允許存取」" -ForegroundColor Gray
    }
} catch {
    Write-Host "  ! 無法檢查防火牆規則 (需要管理員權限)" -ForegroundColor Yellow
}
Write-Host ""

# 總結與建議
Write-Host "================================" -ForegroundColor Cyan
Write-Host "診斷完成 - 建議步驟" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

if (-not $serverRunning) {
    Write-Host "1. 啟動 Mock Server:" -ForegroundColor Yellow
    Write-Host "   python scripts\mock_server.py $Port" -ForegroundColor White
    Write-Host ""
}

if (-not $deviceConnected) {
    Write-Host "2. 連接 BlueStacks 到 ADB:" -ForegroundColor Yellow
    Write-Host "   adb connect 127.0.0.1:5555" -ForegroundColor White
    Write-Host ""
}

Write-Host "3. 設定 adb reverse (讓模擬器連到本機 port):" -ForegroundColor Yellow
Write-Host "   adb reverse tcp:$Port tcp:$Port" -ForegroundColor White
Write-Host ""

Write-Host "4. 在 App 中使用以下任一網址:" -ForegroundColor Yellow
Write-Host "   - http://localhost:$Port/       (需 adb reverse)" -ForegroundColor White
Write-Host "   - http://10.0.2.2:$Port/        (Android 模擬器預設主機 IP)" -ForegroundColor White
Write-Host ""

Write-Host "5. 在 BlueStacks 內建瀏覽器測試:" -ForegroundColor Yellow
Write-Host "   開啟瀏覽器並前往 http://10.0.2.2:$Port/" -ForegroundColor White
Write-Host "   應該會看到 Mock Server 的狀態頁面" -ForegroundColor White
Write-Host ""

Write-Host "================================" -ForegroundColor Cyan
Write-Host ""
