package com.uka.peopleanalyser

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.view.SurfaceHolder
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 1001
    }

    // UI元件
    private lateinit var etRTSPUrl: EditText
    private lateinit var btnConnect: Button
    private lateinit var btnStartAnalysis: Button
    private lateinit var btnStopAnalysis: Button
    private lateinit var btnSettings: Button
    private lateinit var tvConnectionStatus: TextView
    private lateinit var tvStreamStatus: TextView
    private lateinit var tvAnalysisStatus: TextView
    private lateinit var tvPeopleCount: TextView
    private lateinit var tvLastUpdate: TextView
    private lateinit var playerView: PlayerView
    private lateinit var tvGenderStats: TextView
    private lateinit var tvAgeStats: TextView
    private lateinit var tvDwellTime: TextView
    private lateinit var btnSimulateFrame: Button

    // Small overlay views
    private lateinit var tvSmallConnection: TextView
    private lateinit var tvSmallUsb: TextView
    private lateinit var tvSmallPeople: TextView

    // USB 相關 UI 元件
    private lateinit var btnConnectUsb: Button
    private lateinit var usbCameraView: android.view.SurfaceView

    // ExoPlayer 播放器
    private var player: ExoPlayer? = null
    private var isAnalysisRunning = false

    // USB 攝影機管理
    private var usbCameraManager: UsbCameraManager? = null
    private var isUsbCameraMode = false

    // 處理器與執行緒
    private val frameProcessor = UsbFrameProcessor()
    private var lastFaceApiCall = 0L
    private val processingExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViews()
        setupClickListeners()
        updateUiState()
        updateSmallOverlay()
        // DO NOT initialize UsbCameraManager here to avoid triggering library late-init at app start.
        // We'll initialize when the user requests a USB connection (better UX and safer for missing native libs).
        // initUsbCamera()
    }

    private fun initViews() {
        etRTSPUrl = findViewById(R.id.etRTSPUrl)
        btnConnect = findViewById(R.id.btnConnect)
        btnStartAnalysis = findViewById(R.id.btnStartAnalysis)
        btnStopAnalysis = findViewById(R.id.btnStopAnalysis)
        btnSettings = findViewById(R.id.btnSettings)
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)
        tvStreamStatus = findViewById(R.id.tvStreamStatus)
        tvAnalysisStatus = findViewById(R.id.tvAnalysisStatus)
        tvPeopleCount = findViewById(R.id.tvPeopleCount)
        tvLastUpdate = findViewById(R.id.tvLastUpdate)
        playerView = findViewById(R.id.openGlView)

        tvGenderStats = findViewById(R.id.tvGenderStats)
        tvAgeStats = findViewById(R.id.tvAgeStats)
        tvDwellTime = findViewById(R.id.tvDwellTime)

        btnConnectUsb = findViewById(R.id.btnConnectUsb)
        usbCameraView = findViewById(R.id.usbCameraView)

        btnSimulateFrame = findViewById(R.id.btnSimulateFrame)

        // overlay
        tvSmallConnection = findViewById(R.id.tvSmallConnection)
        tvSmallUsb = findViewById(R.id.tvSmallUsb)
        tvSmallPeople = findViewById(R.id.tvSmallPeople)
    }

    private fun setupClickListeners() {
        btnConnect.setOnClickListener {
            if (player == null || player?.isPlaying != true) connectToStream() else disconnectFromStream()
        }

        btnStartAnalysis.setOnClickListener { startAnalysis() }
        btnStopAnalysis.setOnClickListener { stopAnalysis() }
        btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        btnConnectUsb.setOnClickListener {
            if (!isUsbCameraMode) connectUsbCamera() else disconnectUsbCamera()
        }

        btnSimulateFrame.setOnClickListener {
            processingExecutor.execute {
                val width = 640
                val height = 480
                val ySize = width * height
                val uvSize = ySize / 2
                val data = ByteArray(ySize + uvSize)
                for (i in 0 until ySize) data[i] = 128.toByte()
                for (i in ySize until data.size) data[i] = 128.toByte()

                runOnUiThread { tvStreamStatus.text = "模擬影格已送出，等待分析..." }
                val bitmap = frameProcessor.yuv420spToBitmap(data, width, height)
                if (bitmap != null) {
                    val baos = java.io.ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, baos)
                    val jpegBytes = baos.toByteArray()
                    try {
                        val client = FaceApiClient(this@MainActivity)
                        val faces = client.detectFaces(jpegBytes)
                        runOnUiThread {
                            tvLastUpdate.text = "最後更新：${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}"
                            if (faces.isNullOrEmpty()) {
                                tvPeopleCount.text = "偵測人數：0"
                                tvGenderStats.text = "性別比例：-"
                                tvAgeStats.text = "平均年齡：-"
                                tvDwellTime.text = "平均駐足：- 秒"
                                updateSmallOverlay()
                            } else {
                                tvPeopleCount.text = "偵測人數：${faces.size}"
                                val male = faces.count { it.gender.equals("male", true) }
                                val female = faces.count { it.gender.equals("female", true) }
                                val total = faces.size
                                val malePct = if (total > 0) (male * 100 / total) else 0
                                val femalePct = if (total > 0) (female * 100 / total) else 0
                                tvGenderStats.text = "性別比例：男 ${malePct}% | 女 ${femalePct}%"
                                val avgAge = if (total > 0) faces.map { it.age }.average().toInt() else 0
                                tvAgeStats.text = "平均年齡：${if (avgAge>0) avgAge.toString() else "-"}"
                                updateSmallOverlay()
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread { Toast.makeText(this@MainActivity, "Face API 呼叫失敗: ${e.message}", Toast.LENGTH_LONG).show() }
                    }
                }
            }
        }
    }

    private fun connectToStream() {
        val rtspUrl = etRTSPUrl.text.toString().trim()
        if (rtspUrl.isEmpty()) { Toast.makeText(this, "请输入RTSP地址", Toast.LENGTH_SHORT).show(); return }
        tvConnectionStatus.text = "连接中..."
        releasePlayer()
        player = ExoPlayer.Builder(this).build().apply {
            playWhenReady = true
            playerView.player = this
            setMediaItem(MediaItem.fromUri(rtspUrl))
            prepare()
        }
        tvConnectionStatus.text = "已连接"
        tvStreamStatus.visibility = android.view.View.GONE
        btnConnect.text = "断开连接"
        btnStartAnalysis.isEnabled = true
        updateSmallOverlay()
    }

    private fun disconnectFromStream() {
        releasePlayer()
        tvConnectionStatus.text = "已断开"
        tvStreamStatus.text = "等待连接..."
        tvStreamStatus.visibility = android.view.View.VISIBLE
        btnConnect.text = "连接"
        btnStartAnalysis.isEnabled = false
        btnStopAnalysis.isEnabled = false
        stopAnalysis()
        updateSmallOverlay()
    }

    private fun startAnalysis() {
        isAnalysisRunning = true
        tvAnalysisStatus.text = "状态：分析中"
        tvAnalysisStatus.setTextColor(getColor(R.color.green))
        btnStartAnalysis.isEnabled = false
        btnStopAnalysis.isEnabled = true
        Thread {
            while (isAnalysisRunning) {
                Thread.sleep(3000)
                runOnUiThread {
                    tvPeopleCount.text = "检测人数：${Random().nextInt(5) + 1}"
                    tvLastUpdate.text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    updateSmallOverlay()
                }
            }
        }.start()
    }

    private fun stopAnalysis() {
        isAnalysisRunning = false
        tvAnalysisStatus.text = "状态：已停止"
        tvAnalysisStatus.setTextColor(getColor(R.color.orange))
        btnStartAnalysis.isEnabled = true
        btnStopAnalysis.isEnabled = false
        updateSmallOverlay()
    }

    private fun releasePlayer() {
        player?.stop(); player?.release(); player = null; playerView.player = null
    }

    private fun updateUiState() {
        btnStartAnalysis.isEnabled = player != null && !isAnalysisRunning
        btnStopAnalysis.isEnabled = isAnalysisRunning
        btnConnect.text = if (player != null && player?.isPlaying == true) "断开连接" else "连接"
    }

    override fun onDestroy() {
        super.onDestroy()
        isAnalysisRunning = false
        releasePlayer()
        usbCameraManager?.release()
        processingExecutor.shutdownNow()
    }

    // Small overlay updater
    private fun updateSmallOverlay() {
        runOnUiThread {
            try {
                val conn = if (player != null && player?.isPlaying == true) "RTSP: ON" else if (isUsbCameraMode) "USB PREVIEW" else "OFF"
                tvSmallConnection.text = "Conn: $conn"
                val usb = if (usbCameraManager != null) {
                    val devices = usbCameraManager?.getAttachedDevices()
                    if (!devices.isNullOrEmpty()) "Attached (${devices.size})" else "未偵測"
                } else {
                    "未偵測"
                }
                tvSmallUsb.text = "USB: $usb"

                val peopleText = tvPeopleCount.text?.toString() ?: "偵測人數：0"
                // extract number
                val num = Regex("\\d+").find(peopleText)?.value ?: "0"
                tvSmallPeople.text = "People: $num"
            } catch (e: Exception) {
                // defensive: don't crash UI when overlay update fails
            }
        }
    }

    // USB camera initialization and callbacks
    private fun createUsbCallback(): UsbCameraManager.UsbCameraCallback {
        return object : UsbCameraManager.UsbCameraCallback {
            override fun onDeviceAttached(device: UsbDevice) {
                runOnUiThread { Toast.makeText(this@MainActivity, "USB攝影機已插入: ${device.deviceName}", Toast.LENGTH_SHORT).show(); updateSmallOverlay() }
            }

            override fun onDeviceDetached(device: UsbDevice) {
                runOnUiThread { Toast.makeText(this@MainActivity, "USB攝影機已移除", Toast.LENGTH_SHORT).show(); disconnectUsbCamera(); updateSmallOverlay() }
            }

            override fun onPermissionGranted(device: UsbDevice) {
                runOnUiThread { Toast.makeText(this@MainActivity, "USB權限已授予", Toast.LENGTH_SHORT).show() }
            }

            override fun onPermissionDenied() {
                runOnUiThread { Toast.makeText(this@MainActivity, "USB權限被拒絕", Toast.LENGTH_SHORT).show() }
            }

            override fun onCameraOpened() {
                runOnUiThread {
                    if (usbCameraView.holder.surface.isValid) {
                        usbCameraManager?.startPreview(usbCameraView.holder.surface)
                    } else {
                        usbCameraView.holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) { usbCameraManager?.startPreview(holder.surface) }
                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                            override fun surfaceDestroyed(holder: SurfaceHolder) {}
                        })
                    }

                    // 註冊 frame callback 以處理原始影格（使用 UsbCameraManager.setFrameCallback）
                    usbCameraManager?.setFrameCallback { data ->
                        val now = System.currentTimeMillis()
                        if (now - lastFaceApiCall < 2000) return@setFrameCallback
                        lastFaceApiCall = now

                        processingExecutor.execute {
                            val bitmap = frameProcessor.yuv420spToBitmap(data, 640, 480)
                            if (bitmap != null) {
                                val baos = java.io.ByteArrayOutputStream()
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, baos)
                                val jpegBytes = baos.toByteArray()
                                val client = FaceApiClient(this@MainActivity)
                                val faces = try { client.detectFaces(jpegBytes) } catch (e: Exception) { null }
                                if (!faces.isNullOrEmpty()) {
                                    runOnUiThread {
                                        tvPeopleCount.text = "偵測人數：${faces.size}"
                                        tvLastUpdate.text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                                        val male = faces.count { it.gender.equals("male", true) }
                                        val female = faces.count { it.gender.equals("female", true) }
                                        val total = faces.size
                                        val malePct = if (total > 0) (male * 100 / total) else 0
                                        val femalePct = if (total > 0) (female * 100 / total) else 0
                                        tvGenderStats.text = "性別比例：男 ${malePct}% | 女 ${femalePct}%"
                                        val avgAge = if (total > 0) faces.map { it.age }.average().toInt() else 0
                                        tvAgeStats.text = "平均年齡：${if (avgAge>0) avgAge.toString() else "-"}"
                                        updateSmallOverlay()
                                    }
                                    // 上傳
                                    val arr = org.json.JSONArray()
                                    faces.forEach { arr.put(it.toJson()) }
                                    FaceAnalysisService.startUpload(this@MainActivity, arr.toString())
                                } else {
                                    runOnUiThread {
                                        tvPeopleCount.text = "偵測人數：0"
                                        tvGenderStats.text = "性別比例：-"
                                        tvAgeStats.text = "平均年齡：-"
                                        updateSmallOverlay()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            override fun onPreviewStarted() {
                runOnUiThread {
                    tvConnectionStatus.text = "USB攝影機預覽中"
                    tvStreamStatus.visibility = android.view.View.GONE
                    btnConnectUsb.text = "斷開USB攝影機"
                    btnStartAnalysis.isEnabled = true
                    isUsbCameraMode = true
                    playerView.visibility = android.view.View.GONE
                    usbCameraView.visibility = android.view.View.VISIBLE
                    updateSmallOverlay()
                }
            }

            override fun onError(message: String) {
                runOnUiThread { Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show(); updateSmallOverlay() }
            }

            // ...existing code...

        }
    }

    private fun initUsbCamera() {
        // Keep a helper to create manager instance but DO NOT call initialize here.
        usbCameraManager = UsbCameraManager(this)
        updateSmallOverlay()
    }

    private fun ensureUsbInitializedAndListDevices(): List<UsbDevice> {
        if (usbCameraManager == null) usbCameraManager = UsbCameraManager(this)
        // initialize only if not initialized; UsbCameraManager will report errors via callback
        // We must pass the callback created above
        try {
            usbCameraManager?.initialize(createUsbCallback())
        } catch (e: Exception) {
            // initialize should already catch internal exceptions, but be defensive
            runOnUiThread { Toast.makeText(this@MainActivity, "初始化 USB 失敗: ${e.message}", Toast.LENGTH_LONG).show() }
        }
        updateSmallOverlay()
        return usbCameraManager?.getAttachedDevices() ?: emptyList()
    }

    private fun connectUsbCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
            return
        }

        // initialize manager now (on demand) and get devices
        val devices = ensureUsbInitializedAndListDevices()

        if (devices.isNullOrEmpty()) {
            Toast.makeText(this, "未偵測到 USB 攝影機", Toast.LENGTH_SHORT).show()
            updateSmallOverlay()
        } else {
            Toast.makeText(this, "請授權 USB 權限（若出現提示）或等待預覽啟動", Toast.LENGTH_SHORT).show()
            updateSmallOverlay()
        }
    }

    private fun disconnectUsbCamera() {
        usbCameraManager?.stopPreview()
        isUsbCameraMode = false
        tvConnectionStatus.text = "已斷開"
        tvStreamStatus.text = "等待連線..."
        tvStreamStatus.visibility = android.view.View.VISIBLE
        btnConnectUsb.text = "連接USB攝影機"
        btnStartAnalysis.isEnabled = false
        btnStopAnalysis.isEnabled = false
        usbCameraView.visibility = android.view.View.GONE
        playerView.visibility = android.view.View.VISIBLE
        stopAnalysis()
        updateSmallOverlay()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) connectUsbCamera() else Toast.makeText(this, "需要相機權限以使用 USB 攝影機", Toast.LENGTH_LONG).show()
        }
    }
}
