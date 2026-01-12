# BlueStacks 連線測試驗證指南

本文件說明如何驗證 Mock Server 與 BlueStacks 連線設定是否正確。

## 前置檢查清單

在開始測試前，請確認：

- [ ] Python 3.x 已安裝
- [ ] BlueStacks 已安裝並正在運行
- [ ] (選用) Android SDK Platform Tools 已安裝（如需使用 adb reverse）

## 測試步驟

### 步驟 1：啟動 Mock Server

在專案根目錄執行：

```powershell
# 使用快速啟動腳本
.\scripts\start_mock_server.ps1

# 或直接執行 Python 腳本
python scripts\mock_server.py
```

**預期結果**：
- 應該看到以下訊息：
  ```
  ============================================================
  Mock Face API Server 已啟動
  監聽位址: 0.0.0.0:8000
  ============================================================
  ```

### 步驟 2：本機測試

在**主機**上開啟瀏覽器，前往：
```
http://127.0.0.1:8000/
```

**預期結果**：
- 應該看到 JSON 回應：
  ```json
  {
    "status": "ok",
    "message": "Mock Face API Server is running",
    ...
  }
  ```

### 步驟 3：BlueStacks 瀏覽器測試（方式 A - 10.0.2.2）

1. 在 **BlueStacks** 中開啟內建瀏覽器（Chrome）
2. 在網址列輸入：`http://10.0.2.2:8000/`
3. 按 Enter

**預期結果**：
- 應該看到與步驟 2 相同的 JSON 回應
- 如果看到「拒絕連線」，請參考 [BLUESTACKS_SETUP.md](BLUESTACKS_SETUP.md) 的疑難排解章節

### 步驟 4：執行診斷腳本（如有問題時）

如果步驟 3 失敗，在主機上執行診斷腳本：

```powershell
.\scripts\diagnose_connection.ps1
```

**診斷項目**：
1. ✓ Mock Server 是否執行
2. ✓ 本機連線是否正常
3. ✓ ADB 是否可用
4. ✓ BlueStacks 裝置是否已連接
5. ✓ adb reverse 是否已設定
6. ✓ Windows 防火牆狀態

根據診斷結果，按照提示修正問題。

### 步驟 5：BlueStacks ADB 測試（方式 B - 選用）

如果要使用 `adb reverse`：

1. **連接 ADB**：
   ```powershell
   adb connect 127.0.0.1:5555
   ```

2. **確認裝置連接**：
   ```powershell
   adb devices
   ```
   應該顯示：
   ```
   List of devices attached
   127.0.0.1:5555  device
   ```

3. **設定 port 轉發**：
   ```powershell
   adb reverse tcp:8000 tcp:8000
   ```

4. **驗證設定**：
   ```powershell
   adb reverse --list
   ```
   應該顯示：
   ```
   127.0.0.1:5555 tcp:8000 tcp:8000
   ```

5. **在 BlueStacks 瀏覽器測試**：
   前往 `http://localhost:8000/` 或 `http://127.0.0.1:8000/`

**預期結果**：
- 應該看到 Mock Server 的 JSON 回應

### 步驟 6：App 測試

1. **建置並安裝 App**：
   ```powershell
   .\gradlew.bat assembleDebug
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```

2. **開啟 App**，進入設定頁面

3. **設定 Azure 端點**（根據使用的方式）：
   - 方式 A (10.0.2.2)：`http://10.0.2.2:8000`
   - 方式 B (adb reverse)：`http://localhost:8000`

4. **測試人臉偵測功能**：
   - 點擊「模擬影格」按鈕
   - 或連接攝影機並啟動分析

**預期結果**：
- 應該看到人數統計更新（顯示 Mock 回傳的 2 張臉）
- 性別比例：男 50% | 女 50%
- 平均年齡：30

## 驗證完成

如果所有步驟都成功，表示：

✅ Mock Server 正常運作  
✅ BlueStacks 可以連接到主機  
✅ App 可以正確呼叫 Mock API  
✅ 網路設定正確（cleartext traffic 已允許）

## 疑難排解速查表

| 問題 | 可能原因 | 解決方式 |
|------|---------|---------|
| 本機瀏覽器連不上 | Mock Server 未啟動 | 檢查 Python 是否執行中 |
| BlueStacks 顯示拒絕連線 | 防火牆阻擋 | 允許 Python 通過防火牆 |
| adb devices 空白 | BlueStacks ADB 未啟用 | 在 BlueStacks 設定中啟用 ADB |
| adb reverse 失敗 | 裝置未授權 | 在 BlueStacks 中允許 USB 偵錯 |
| App 顯示「未連接」 | 端點設定錯誤 | 確認使用正確的 IP（10.0.2.2 或 localhost） |
| App 無網路權限錯誤 | Manifest 設定問題 | 確認已加入 INTERNET 權限（已包含在本專案） |

## 完整測試腳本（PowerShell）

以下是一鍵測試腳本，可驗證所有步驟：

```powershell
# 測試腳本 - test_connection.ps1
Write-Host "=== BlueStacks 連線完整測試 ===" -ForegroundColor Cyan

# 1. 檢查 Python
Write-Host "`n[1/4] 檢查 Python..." -ForegroundColor Yellow
python --version
if ($LASTEXITCODE -ne 0) { Write-Host "  ✗ Python 未安裝" -ForegroundColor Red; exit 1 }

# 2. 啟動 Mock Server（背景）
Write-Host "`n[2/4] 啟動 Mock Server..." -ForegroundColor Yellow
Start-Process python -ArgumentList "scripts\mock_server.py" -WindowStyle Hidden
Start-Sleep -Seconds 2

# 3. 測試本機連線
Write-Host "`n[3/4] 測試本機連線..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "http://127.0.0.1:8000/" -UseBasicParsing
    if ($response.StatusCode -eq 200) {
        Write-Host "  ✓ 本機連線成功" -ForegroundColor Green
    }
} catch {
    Write-Host "  ✗ 本機連線失敗" -ForegroundColor Red
}

# 4. 執行診斷
Write-Host "`n[4/4] 執行完整診斷..." -ForegroundColor Yellow
.\scripts\diagnose_connection.ps1

Write-Host "`n測試完成！" -ForegroundColor Cyan
```

## 相關文件

- [BLUESTACKS_SETUP.md](BLUESTACKS_SETUP.md) - 詳細設定指南
- [README.md](README.md) - 專案總覽

## 需要協助？

如果測試失敗，請：
1. 執行 `.\scripts\diagnose_connection.ps1 > diagnosis.txt`
2. 將 `diagnosis.txt` 內容提供給開發團隊
3. 附上錯誤訊息的截圖

---

**提示**：首次設定建議使用方式 A (10.0.2.2)，因為最簡單且不需要 ADB。
