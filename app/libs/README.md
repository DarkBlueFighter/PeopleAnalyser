若 JitPack 無法下載 UVCCamera，你可以手動把 AAR 與 native .so 放到本專案：

1) 推薦：在可上網的機器下載 AAR（範例使用 PowerShell）

# PowerShell 範例：下載 UVCCamera AAR（請在可用網路環境執行，然後把檔案放到本機 repo 的 app/libs/）
$dest = "UVCCamera-2.5.6.aar"
$uri = "https://github.com/saki4510t/UVCCamera/releases/download/v2.5.6/UVCCamera-2.5.6.aar"
Invoke-WebRequest -Uri $uri -OutFile $dest

# 如果上面失敗，備用：手動從 GitHub release 或其他來源抓 AAR

2) 把 AAR 放到本專案的 app/libs/ 目錄下（你可以在此 repo 執行）

3) 若 AAR 包含 native libs，並希望手動檢查或修正，請解壓 AAR 並把 jni/* 下的 .so 放到 app/src/main/jniLibs/<abi>/

# 解壓 AAR（Windows PowerShell 範例）
Expand-Archive -LiteralPath "UVCCamera-2.5.6.aar" -DestinationPath "uvc_temp"
# 檢查 uvc_temp/jni 下是否有 armeabi-v7a/arm64-v8a 等資料夾，有的話拷貝到 app/src/main/jniLibs/

4) 建置指令（Windows PowerShell）：
cd <repo root>
.\gradlew.bat clean assembleDebug --console=plain

5) 常見錯誤：
- Could not find com.github.saki4510t:UVCCamera:2.5.6 -> network/JitPack blocked，請改用本地 AAR。
- UnsatisfiedLinkError -> APK 內沒有對應 ABI 的 .so，請檢查裝置 ABI 與 app/src/main/jniLibs/ 是否包含同一 ABI。

如果需要，我可以幫你下載 AAR 並把必要的 .so 放到 repo，但我需要確認你允許我在 repo 裡新增二進位檔案（AAR / .so）。
