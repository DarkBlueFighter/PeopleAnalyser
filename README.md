PeopleAnalyser - USB 攝影機與 RTSP 人流分析

此專案為 Android 應用範例，提供：
- RTSP 串流播放（ExoPlayer）
- USB 攝影機偵測與預覽（UVCCamera 支援）
- 基本影格處理範例（YUV -> Bitmap, 亮度計算, 簡易運動偵測）
- 前台服務範例（FaceAnalysisService）
- **Mock Server 支援**：本機測試時可使用 Mock Face API Server

## 📱 在 BlueStacks 模擬器中測試

如果您要在 BlueStacks 模擬器中測試 App，請參考 **[BlueStacks 設定指南](BLUESTACKS_SETUP.md)**，其中包含：
- 如何啟動 Mock Server
- 如何讓模擬器連接到本機 Mock Server
- 連線問題排查指南
- ADB 設定說明

### 快速開始（BlueStacks 測試）

1. **啟動 Mock Server**：
   ```powershell
   .\scripts\start_mock_server.ps1
   ```

2. **在 BlueStacks 瀏覽器中測試連線**：
   - 開啟 BlueStacks 內建瀏覽器
   - 前往 `http://10.0.2.2:8000/`
   - 應該會看到 Mock Server 的回應

3. **在 App 設定中使用 Mock Server**：
   - Azure 端點設定為：`http://10.0.2.2:8000`

詳細說明請見：[BLUESTACKS_SETUP.md](BLUESTACKS_SETUP.md)

---

## 🛠️ 專案修改摘要

我已在專案中做的修改摘要：
- 修正並清理 `AndroidManifest.xml`，新增 USB `device_filter.xml`（res/xml/device_filter.xml）
- 新增 `network_security_config.xml`：允許 cleartext (HTTP) 連線用於開發測試
- 新增/更新 `UsbCameraManager.kt`：使用 UVCCamera + USBMonitor（若系統/相依正確，會自動監聽 USB attach/detach、處理權限並開啟 camera / startPreview）
- 更新 `MainActivity.kt`：新增 USB preview 的 `SurfaceView`（`R.id.usbCameraView`）、按鈕 `btnConnectUsb` 行為、frame callback 處理（把 ByteBuffer 轉 byte[]，交給 `UsbFrameProcessor` 處理）、相機權限檢查
- 修正 `UsbFrameProcessor.kt` 的語法錯誤（並保留若干轉換/處理輔助方法）

---

## ⚙️ 本地建置需求
- 您的開發環境必須有 JDK 並正確設定 `JAVA_HOME`（Gradle 需要）

在 Windows PowerShell 設置 JAVA_HOME（範例，請依照實際 JDK 路徑修改）：

```powershell
# 例如 JDK 安裝在 C:\Program Files\Java\jdk-17
$env:JAVA_HOME = 'C:\\Program Files\\Java\\jdk-17'
[System.Environment]::SetEnvironmentVariable('JAVA_HOME', $env:JAVA_HOME, 'User')
# 將 java 加入 PATH（臨時於當前 shell）
$env:Path = $env:JAVA_HOME + '\\bin;' + $env:Path
```

編譯與安裝（於專案根目錄）：

```powershell
cd C:\Users\roger\AndroidStudioProjects\PeopleAnalyser
.\gradlew.bat assembleDebug
# 或建置並安裝到已連接的裝置（並啟用 USB 調試）：
.\gradlew.bat installDebug
```

在 Android 裝置上測試 USB 攝影機（步驟）
1. 裝置需支援 USB Host / OTG。
2. 連接 USB 攝影機。系統會廣播 `ACTION_USB_DEVICE_ATTACHED`，應用會偵測到並顯示通知。
3. 在應用中按下「連接USB攝影機」按鈕（第一次會檢查 CAMERA 權限並請求），授權後 USB 管理器會請求對該裝置的使用權（系統提示）。
4. 授權後，若已整合 `UVCCamera` native library，`UsbCameraManager` 會嘗試使用 `USBMonitor` 與 `UVCCamera` 開啟攝影機並把畫面預覽到 `usbCameraView`。若未整合 native libs，會收到錯誤通知（app 目前在沒有 native 時會回報錯誤 message）。
5. 在 logcat 查看日誌（過濾標籤 `UsbCameraManager` 或 `MainActivity`）：

```text
adb logcat -s UsbCameraManager MainActivity
```

已知限制與下一步建議
- 本地 CI/終端機 build 會失敗若未設定 `JAVA_HOME`（我在你的環境中執行 gradle 時就遇到這個問題）。請先在你的電腦上設定 JDK。
- 如果要完整支援 USB 攝影機影格流與較低階控制（曝光、格式轉換），建議完整整合 `UVCCamera` 並確認原生 `.so` 檔可被打包。`app/build.gradle.kts` 已宣告 `com.github.saki4510t:UVCCamera:2.5.6`，但請確認 Gradle 能下載並編譯原生部份。
- 可進一步：把 frame 處理移到 `Executor`/`Coroutine` 以利控制併發與取消，並在 `onPause` / `onResume` 處理 preview 的暫停與恢復。

如果你要我接下來做的事（請選）：
- A：我協助把 frame 處理改為使用 `Executor` 與 Coroutine，並加入取消/生命週期管理。
- B：我協助把 UVCCamera 的完整示例（含 native .so 設定）整合，並示範如何在 `UsbCameraManager` 中抓取 YUV 帧。（此步驟可能需要在有 JDK 與正確 NDK 環境下 build）
- C：我只提供完整的使用說明與 debug 指令，你自行在本機執行 build 與測試。

回報與日誌
- 若在實機測試時看到錯誤或例外，請複製 adb logcat 的錯誤段落並回報給我，我會協助分析並修正。

---
感謝你，我已把需要的檔案修改並提交到 workspace；請告訴我要繼續做哪一項（A / B / C），或把你想要我直接執行的下一步告訴我。

## 一鍵建立 GitHub repository 與 CI（選擇性、請在準備好後執行）

專案中包含兩個可選的自動化輔助檔案：

- `scripts/create_and_push_repo.ps1`：一個 PowerShell 腳本，使用 GitHub CLI (`gh`) 幫你建立遠端 repository、初始化或使用現有的 git、commit 當前變更，並將專案推上 GitHub（同時會把我新增的 CI workflow 一併上傳）。
- `.github/workflows/android-build.yml`：GitHub Actions workflow，會在 push 或 PR 時於 runner 上執行 `./gradlew assembleDebug` 來建置 debug APK，並上傳 artifact（debug-apk）。

重要提醒：
- 我已在 workspace 中新增上述檔案，但**不會**自動執行或推送任何東西到遠端。請在你確認無誤且準備好對外發布時，再手動執行下面步驟或腳本。
- 若你還沒準備好或不熟悉 Git/GitHub，請先不要執行腳本。我會等你授權再幫你觸發推送或後續動作。

如何使用（當你準備好時）

1) 使用 `gh`（GitHub CLI）自動建立並推送（推薦、一次完成）:

```powershell
# 在專案根目錄執行（會互動式要求 gh 驗證，如果尚未登入）
.\scripts\create_and_push_repo.ps1
```

2) 或手動建立遠端 repository 並推送（如果你偏好 Web UI）:

- 在 GitHub 上建立一個新的 repository（不要勾選 "Initialize with README"）。
- 然後在專案根目錄執行：

```powershell
# 若尚未 git 初始化
if (-not (git rev-parse --is-inside-work-tree 2>$null)) { git init }
git add -A
git commit -m "CI: add Android build workflow; UI: centralize status overlay colors and night values" 2>$null || Write-Output "No new changes to commit"
# 替換為你自己的遠端 URL
$REMOTE_URL = "https://github.com/<YOUR_USER>/<YOUR_REPO>.git"
git remote remove origin 2>$null; git remote add origin $REMOTE_URL
git branch -M main
git push -u origin main
```

3) 在你 push 後：
- 前往 GitHub -> Actions 檢視 workflow run 狀態。成功時可從 Artifacts 下載 `debug-apk`。
- 若發生錯誤，請把失敗步驟的日誌貼給我，我會協助修正。

我目前的狀態（摘要）

- 我已在 workspace 內新增腳本與 workflow 檔，但**沒有**對任何遠端執行 push，也不會在未經你同意下觸發 CI。你可在任何時候告訴我「現在推送」，我就會引導或協助你完成。

---

# 已完成的變更摘要（快速回顧）

- 把 `status_overlay_bg` 的顏色抽成 `@color`，並加入 `status_overlay_border`，同時新增夜間覆寫（`values-night/colors.xml`）。
- 調整 `status_overlay_bg.xml`：使用 color 資源、加入細邊框、微調 corner radius 與 padding。
- 新增 `.github/workflows/android-build.yml`（CI），以及 `scripts/create_and_push_repo.ps1`（一鍵上傳腳本）與 `.gitignore`。

---

感謝你，當你準備好要我執行 push 與觸發 CI 時，直接說一聲「現在推送」或執行上方腳本中的命令，我會立即跟進並處理 CI 的後續結果。

## 本機一鍵建置並收集 APK

如果你想在本機快速建置並收集 debug APK，我已新增一個簡單的 PowerShell 腳本：

- `scripts/build_and_collect.ps1`：在本機執行會使用本專案的 `gradlew.bat assembleDebug` 建置，並把生成的 APK 複製到專案下的 `artifacts/` 目錄，方便檢視與安裝。

使用方式（在專案根目錄於 PowerShell 執行）：

```powershell
# 於專案根目錄執行
.\scripts\build_and_collect.ps1
```

注意：此腳本需要在有 Java/JDK 並可使用 `java -version` 的環境下執行；腳本會檢查 `gradlew.bat` 是否存在。

---

## 🔧 測試與診斷工具

### Mock Server 相關工具

- **啟動 Mock Server**：`.\scripts\start_mock_server.ps1`
  - 快速啟動本機測試用的 Mock Face API Server
  - 預設監聽 port 8000，可從 BlueStacks 透過 `10.0.2.2:8000` 存取

- **連線診斷工具**：`.\scripts\diagnose_connection.ps1`
  - 自動診斷 BlueStacks 與本機 Mock Server 的連線問題
  - 檢查項目包括：
    - Mock Server 是否執行
    - 本機連線測試
    - ADB 連接狀態
    - adb reverse 設定
    - Windows 防火牆狀態
  - 使用方式：
    ```powershell
    .\scripts\diagnose_connection.ps1
    # 或指定 ADB 路徑
    .\scripts\diagnose_connection.ps1 -AdbPath "C:\platform-tools\adb.exe"
    ```

### Mock Server API 端點

- `GET /` - 測試用端點，回傳 server 狀態
- `POST /face/v1.0/detect` - 模擬 Azure Face API，回傳 Mock 人臉資料

詳細 API 文檔請見：[BLUESTACKS_SETUP.md](BLUESTACKS_SETUP.md)

---
