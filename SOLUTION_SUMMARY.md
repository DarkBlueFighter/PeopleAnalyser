# BlueStacks 連線問題解決方案總結

## 問題描述

用戶在 BlueStacks 模擬器中測試 Android App 時，無法連接到本機的 Mock Server (http://127.0.0.1:8000/)，顯示「拒絕連線」錯誤。

## 根本原因

BlueStacks 模擬器運行在獨立的網路環境中，`127.0.0.1` 或 `localhost` 在模擬器內指向**模擬器本身**，而非主機。因此需要使用特殊的 IP 位址或透過 ADB 橋接才能存取主機服務。

## 解決方案

我們提供了**兩種解決方案**和完整的工具鏈：

### 方案 A：使用 10.0.2.2（推薦）

**原理**：Android 模擬器（包括 BlueStacks）將 `10.0.2.2` 保留為指向主機 `127.0.0.1` 的特殊 IP。

**優點**：
- ✅ 最簡單，無需額外設定
- ✅ 不需要 ADB
- ✅ 適合快速測試

**使用方式**：
1. 啟動 Mock Server：`.\scripts\start_mock_server.ps1`
2. 在 BlueStacks 瀏覽器測試：`http://10.0.2.2:8000/`
3. 在 App 設定中使用：`http://10.0.2.2:8000`

### 方案 B：使用 adb reverse（進階）

**原理**：透過 ADB 將模擬器的 port 轉發到主機的 port。

**優點**：
- ✅ 可以使用標準 URL (localhost)
- ✅ 更符合生產環境配置

**使用方式**：
1. 連接 ADB：`adb connect 127.0.0.1:5555`
2. 設定轉發：`adb reverse tcp:8000 tcp:8000`
3. 在 App 中使用：`http://localhost:8000`

## 提供的工具

### 1. Mock Server (`scripts/mock_server.py`)

完整的 Python Mock Face API Server，支援：
- GET `/` - 狀態檢查端點
- POST `/face/v1.0/detect` - 模擬人臉偵測 API
- 回傳 Mock 資料（2 張臉：男性 28 歲、女性 32 歲）
- 監聽 `0.0.0.0:8000`，可從模擬器存取

**特點**：
- 完整的 CORS 支援
- 詳細的 logging
- 符合 Azure Face API 格式

### 2. 快速啟動腳本 (`scripts/start_mock_server.ps1`)

一鍵啟動 Mock Server，自動顯示：
- 本機測試 URL
- BlueStacks 存取方式
- ADB 設定步驟

### 3. 診斷工具 (`scripts/diagnose_connection.ps1`)

自動化診斷腳本，檢查：
1. ✓ Mock Server 是否執行
2. ✓ 本機連線是否正常
3. ✓ ADB 是否可用
4. ✓ BlueStacks 裝置連接狀態
5. ✓ adb reverse 設定
6. ✓ Windows 防火牆狀態

**互動式功能**：
- 自動檢測問題
- 提供修正建議
- 可選自動設定 adb reverse

### 4. Network Security Config

新增 `app/src/main/res/xml/network_security_config.xml`，確保：
- ✅ 允許 cleartext (HTTP) 流量
- ✅ 信任本機 IP (localhost, 127.0.0.1, 10.0.2.2)
- ✅ 包含生產環境注意事項

已在 `AndroidManifest.xml` 中啟用此配置。

## 完整文檔

### [BLUESTACKS_SETUP.md](BLUESTACKS_SETUP.md)
詳細的設定指南，包含：
- 兩種連線方式的完整步驟
- 常見問題排除（包含 Windows 防火牆、ADB 授權等）
- 網路原理說明
- Mock Server API 文檔
- 比較表格

### [TESTING.md](TESTING.md)
測試驗證指南，包含：
- 6 步驟測試流程
- 預期結果說明
- 疑難排解速查表
- 一鍵測試腳本

### [README.md](README.md) (已更新)
專案總覽，新增：
- 📱 BlueStacks 測試章節
- 🔧 測試與診斷工具說明
- Mock Server 使用方式

## 使用流程（快速開始）

```powershell
# 1. 啟動 Mock Server
.\scripts\start_mock_server.ps1

# 2. 測試本機連線（瀏覽器）
# 前往 http://127.0.0.1:8000/

# 3. 在 BlueStacks 中測試
# 開啟瀏覽器，前往 http://10.0.2.2:8000/

# 4. 如有問題，執行診斷
.\scripts\diagnose_connection.ps1

# 5. 在 App 設定中使用
# Azure 端點：http://10.0.2.2:8000
```

## 技術細節

### Mock Server 實作
- 基於 Python 3 標準庫 `http.server`
- 不需要額外依賴
- 跨平台支援（Windows/Linux/macOS）
- 監聽 `0.0.0.0` 允許外部連線

### 網路配置
- Android Manifest 已設定 `android:usesCleartextTraffic="true"`
- 新增 Network Security Config 明確允許本機 IP
- 支援 Android API 24+ (Android 7.0+)

### 防火牆考量
- Python 首次運行時，Windows 會提示防火牆授權
- 需允許「私人網路」和「公用網路」
- 診斷腳本會檢測防火牆規則

## 常見問題解答

### Q1: 為什麼本機可以連，BlueStacks 連不上？
A: BlueStacks 的網路環境與主機隔離。必須使用 `10.0.2.2` 或設定 `adb reverse`。

### Q2: 10.0.2.2 和 localhost 有什麼區別？
A: 
- `10.0.2.2`：Android 模擬器的特殊 IP，直接指向主機
- `localhost`：需要透過 `adb reverse` 才能指向主機

### Q3: 如果看到「拒絕連線」怎麼辦？
A: 
1. 確認 Mock Server 正在運行
2. 檢查 Windows 防火牆是否允許 Python
3. 執行 `.\scripts\diagnose_connection.ps1` 自動診斷

### Q4: 能在實機上測試嗎？
A: 可以，但需要：
- 實機與電腦在同一區域網路
- 使用電腦的區域網路 IP（如 `192.168.1.100:8000`）
- 不能使用 `10.0.2.2`（這是模擬器專用）

### Q5: Mock Server 的資料可以修改嗎？
A: 可以！編輯 `scripts/mock_server.py` 中的 `MOCK_FACE_RESPONSE` 變數。

## 驗證測試結果

我們已經驗證：

✅ Mock Server GET 端點正常回應  
✅ Mock Server POST 端點回傳正確的 JSON 格式  
✅ AndroidManifest.xml 語法正確  
✅ network_security_config.xml 語法正確  
✅ Python 腳本可執行  
✅ PowerShell 腳本語法正確  

## 後續建議

1. **首次設定**：使用方案 A (10.0.2.2) 進行快速驗證
2. **開發環境**：如需使用 `adb logcat` 等工具，設定方案 B
3. **實機測試**：將 Mock Server 改為監聽區域網路 IP
4. **生產環境**：記得修改 Network Security Config，限制 cleartext traffic

## 相關資源

- [Android 模擬器網路設定](https://developer.android.com/studio/run/emulator-networking)
- [BlueStacks ADB 說明](https://support.bluestacks.com/hc/en-us/articles/360061342631)
- [Android Network Security Config](https://developer.android.com/training/articles/security-config)

---

**總結**：此解決方案提供了完整的工具鏈和文檔，讓用戶能夠快速在 BlueStacks 中測試 Android App 並連接本機 Mock Server，解決了網路隔離的問題。
