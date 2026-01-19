# BlueStacks 模擬器連接本機 Mock Server 指南

## 問題說明

當您在 BlueStacks 模擬器中測試 Android App 時，App 可能無法連接到本機的 Mock Server（例如 `http://127.0.0.1:8000/`）。這是因為模擬器運行在獨立的網路環境中，需要特殊設定才能存取主機（Host）的服務。

### 為什麼會被擋？

1. **網路隔離**：BlueStacks 模擬器有自己的虛擬網路介面，`127.0.0.1` 或 `localhost` 在模擬器內指向模擬器自己，而非主機。
2. **防火牆限制**：Windows 防火牆可能阻擋外部連線到 Python Mock Server。
3. **ADB 未連接**：如果沒有正確連接 ADB，就無法使用 `adb reverse` 來橋接網路。

## 解決方案

### 方案 A：使用 10.0.2.2（推薦，最簡單）

Android 模擬器（包括 BlueStacks）提供了特殊的 IP 位址 `10.0.2.2`，指向主機的 `127.0.0.1`。

#### 步驟：

1. **啟動 Mock Server**（在主機上）：
   ```powershell
   cd C:\Users\<你的使用者名稱>\path\to\PeopleAnalyser
   python scripts\mock_server.py
   ```
   
   應該會看到類似的輸出：
   ```
   Mock Face API Server 已啟動
   監聽位址: 0.0.0.0:8000
   ```

2. **確認 Mock Server 正常運行**（在主機上）：
   開啟瀏覽器，前往 `http://127.0.0.1:8000/`，應該會看到 JSON 回應：
   ```json
   {
     "status": "ok",
     "message": "Mock Face API Server is running",
     ...
   }
   ```

3. **在 BlueStacks 內測試連線**：
   - 在 BlueStacks 中開啟內建的瀏覽器（Chrome）
   - 前往 `http://10.0.2.2:8000/`
   - 如果看到與步驟 2 相同的 JSON 回應，表示連線成功！

4. **修改 App 設定**：
   在您的 Android App 中，將 Mock Server 的網址設定為：
   ```
   http://10.0.2.2:8000/
   ```

#### 注意事項：

- `10.0.2.2` 只在 Android 模擬器中有效，實機無法使用此 IP
- Mock Server 必須監聽 `0.0.0.0`（所有介面），而非只有 `127.0.0.1`

---

### 方案 B：使用 ADB Reverse（進階，需要 ADB 連接）

使用 `adb reverse` 可以將模擬器的 port 轉發到主機的 port，讓 App 可以使用 `http://localhost:8000/` 存取。

#### 步驟：

1. **確認 BlueStacks 已啟用 ADB**：
   - 開啟 BlueStacks
   - 右上角選單 → **設定** (Settings)
   - 進入 **進階** (Advanced) 頁面
   - 勾選 **啟用 Android Debug Bridge (ADB)**
   - 記下 ADB 連接位址（通常是 `127.0.0.1:5555`）

2. **安裝 Android SDK Platform Tools**（如果尚未安裝）：
   - 下載：https://developer.android.com/tools/releases/platform-tools
   - 解壓縮到任意位置（例如 `C:\platform-tools\`）

3. **連接 ADB 到 BlueStacks**（在主機上執行）：
   ```powershell
   # 如果 adb 在 PATH 中
   adb connect 127.0.0.1:5555
   
   # 或使用完整路徑
   C:\platform-tools\adb.exe connect 127.0.0.1:5555
   ```
   
   應該會看到：
   ```
   connected to 127.0.0.1:5555
   ```

4. **確認裝置已連接**：
   ```powershell
   adb devices
   ```
   
   應該會列出 BlueStacks 裝置：
   ```
   List of devices attached
   127.0.0.1:5555  device
   ```

5. **啟動 Mock Server**（如果尚未啟動）：
   ```powershell
   python scripts\mock_server.py
   ```

6. **設定 adb reverse**：
   ```powershell
   adb reverse tcp:8000 tcp:8000
   ```
   
   此指令會將模擬器的 port 8000 轉發到主機的 port 8000。

7. **驗證設定**：
   ```powershell
   adb reverse --list
   ```
   
   應該會看到：
   ```
   127.0.0.1:5555 tcp:8000 tcp:8000
   ```

8. **在 App 中使用**：
   現在 App 可以使用 `http://localhost:8000/` 或 `http://127.0.0.1:8000/` 連接到主機的 Mock Server。

#### 注意事項：

- `adb reverse` 的設定在 BlueStacks 重啟後會消失，需要重新執行
- 如果切換到其他模擬器實例，需要重新連接 ADB 並設定 reverse

---

## 診斷工具

我們提供了自動診斷腳本來幫您排查問題：

```powershell
# 在專案根目錄執行
.\scripts\diagnose_connection.ps1

# 如果 adb 不在 PATH 中，可以指定路徑
.\scripts\diagnose_connection.ps1 -AdbPath "C:\platform-tools\adb.exe"

# 如果 Mock Server 使用不同 port
.\scripts\diagnose_connection.ps1 -Port 9000
```

此腳本會檢查：
1. Mock Server 是否正在執行
2. 本機連線是否正常
3. ADB 是否可用
4. 裝置是否已連接
5. adb reverse 是否已設定
6. Windows 防火牆設定

---

## 常見問題排除

### 1. 本機瀏覽器可以連，但 BlueStacks 連不上

**可能原因**：Windows 防火牆阻擋

**解決方式**：
- 當啟動 Mock Server 時，Windows 可能會彈出防火牆警告
- 選擇「允許存取」，並勾選「私人網路」和「公用網路」
- 如果已經錯過提示，可以手動新增防火牆規則：
  1. 開啟 Windows Defender 防火牆
  2. 進階設定 → 輸入規則 → 新增規則
  3. 選擇「程式」→ 瀏覽到 `python.exe`
  4. 允許連線 → 套用到所有設定檔

### 2. BlueStacks 瀏覽器顯示「拒絕連線」(ERR_CONNECTION_REFUSED)

**可能原因**：Mock Server 未啟動或監聽錯誤位址

**檢查方式**：
```powershell
# 檢查 port 8000 是否在監聽
netstat -ano | findstr 8000
```

應該會看到類似：
```
TCP    0.0.0.0:8000           0.0.0.0:0              LISTENING       12345
```

如果沒有輸出，表示 Mock Server 未啟動或啟動失敗。

### 3. adb devices 顯示空白或 "unauthorized"

**解決方式**：
- 確認 BlueStacks 的 ADB 設定已啟用
- 在 BlueStacks 中會彈出「允許 USB 偵錯」的提示，選擇「允許」
- 如果沒有彈出提示，嘗試：
  ```powershell
  adb kill-server
  adb start-server
  adb connect 127.0.0.1:5555
  ```

### 4. 使用 10.0.2.2 還是連不上

**可能原因**：Mock Server 只監聽 localhost

**解決方式**：
- 確認 Mock Server 監聽 `0.0.0.0` 而非 `127.0.0.1`
- 我們提供的 `scripts/mock_server.py` 預設已正確設定為 `0.0.0.0`

### 5. BlueStacks 版本問題

某些舊版 BlueStacks 對 ADB 的支援有限，建議：
- 使用 **BlueStacks 5** 或更新版本
- 或考慮改用 **Android Studio 內建模擬器** 或 **Genymotion**

---

## 在 App 中設定 Mock Server 網址

如果您的 App 需要設定 API 端點，根據使用的方案選擇對應的網址：

### 使用方案 A (10.0.2.2)
在 `SettingsActivity` 或相關設定中，將 Azure 端點改為：
```
http://10.0.2.2:8000
```

### 使用方案 B (adb reverse)
在 `SettingsActivity` 或相關設定中，將 Azure 端點改為：
```
http://localhost:8000
```

### 在程式碼中動態判斷

您也可以在 `FaceApiClient.kt` 中加入判斷邏輯：

```kotlin
private fun getApiEndpoint(): String {
    val prefs = context.getSharedPreferences("PeopleAnalyserPrefs", Context.MODE_PRIVATE)
    val azureEndpoint = prefs.getString("azure_endpoint", "") ?: ""
    
    // 如果是測試模式，使用 Mock Server
    val isTestMode = prefs.getBoolean("test_mode", false)
    if (isTestMode) {
        return "http://10.0.2.2:8000"  // 或 "http://localhost:8000" 如果使用 adb reverse
    }
    
    return azureEndpoint
}
```

---

## Mock Server API 端點

我們提供的 Mock Server 支援以下端點：

### GET /
測試用端點，回傳 server 狀態

**回應範例**：
```json
{
  "status": "ok",
  "message": "Mock Face API Server is running",
  "timestamp": "2025-01-12T12:00:00",
  "endpoints": {
    "face_detect": "POST /face/v1.0/detect?returnFaceAttributes=age,gender,headPose,smile"
  }
}
```

### POST /face/v1.0/detect
模擬 Azure Face API 的人臉偵測端點

**請求**：
- Content-Type: `application/octet-stream`
- Body: JPEG 影像資料（bytes）

**回應範例**：
```json
[
  {
    "faceId": "mock-face-001",
    "faceRectangle": {
      "top": 100,
      "left": 120,
      "width": 150,
      "height": 150
    },
    "faceAttributes": {
      "gender": "male",
      "age": 28.0,
      "smile": 0.8,
      "headPose": {
        "yaw": 5.0,
        "pitch": 0.0,
        "roll": 0.0
      }
    }
  },
  {
    "faceId": "mock-face-002",
    "faceRectangle": {
      "top": 120,
      "left": 400,
      "width": 140,
      "height": 140
    },
    "faceAttributes": {
      "gender": "female",
      "age": 32.0,
      "smile": 0.6,
      "headPose": {
        "yaw": -3.0,
        "pitch": 0.0,
        "roll": 0.0
      }
    }
  }
]
```

---

## 總結

| 方案 | 網址 | 優點 | 缺點 | 建議使用時機 |
|------|------|------|------|------------|
| **方案 A: 10.0.2.2** | `http://10.0.2.2:8000/` | 簡單，無需 ADB | 只適用模擬器 | 快速測試、首次設定 |
| **方案 B: adb reverse** | `http://localhost:8000/` | 使用標準網址 | 需要 ADB 連接 | 正式開發、CI/CD |

**推薦流程**：
1. 先使用方案 A (10.0.2.2) 快速驗證連線
2. 如果需要進階功能（如 logcat、安裝 APK），再設定 ADB
3. 實機測試時，將 Mock Server 部署到區域網路中的實際 IP（如 `192.168.1.100:8000`）

---

## 相關資源

- [Android 模擬器網路設定文檔](https://developer.android.com/studio/run/emulator-networking)
- [BlueStacks ADB 設定說明](https://support.bluestacks.com/hc/en-us/articles/360061342631-How-to-connect-your-instance-to-ADB-on-BlueStacks-5)
- [Android Platform Tools 下載](https://developer.android.com/tools/releases/platform-tools)

---

## 需要更多協助？

如果按照本指南操作後仍有問題，請執行診斷腳本並提供輸出：

```powershell
.\scripts\diagnose_connection.ps1 > diagnosis.txt
```

將 `diagnosis.txt` 的內容提供給開發團隊，我們會協助排查問題。
