package com.uka.peopleanalyser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class FaceAnalysisService : Service() {

    companion object {
        private const val TAG = "FaceAnalysisService"
        private const val NOTIFICATION_CHANNEL_ID = "face_detection_channel"
        private const val NOTIFICATION_ID = 1

        const val ACTION_UPLOAD_FACES = "com.uka.peopleanalyser.action.UPLOAD_FACES"
        const val EXTRA_FACES_JSON = "extra_faces_json"

        fun startUpload(context: Context, facesJson: String) {
            val intent = Intent(context, FaceAnalysisService::class.java).apply {
                action = ACTION_UPLOAD_FACES
                putExtra(EXTRA_FACES_JSON, facesJson)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private lateinit var okHttpClient: OkHttpClient
    private var isRunning = false
    private var analysisThread: Thread? = null

    // 人臉追蹤資料
    private val faceTrackingMap = mutableMapOf<String, FaceTrackingData>()

    data class FaceTrackingData(
        val faceId: String,
        var firstSeen: Long,
        var lastSeen: Long,
        var gender: String? = null,
        var age: Int? = null,
        var dwellTime: Long = 0,
        var gazeStartTime: Long? = null,
        var totalGazeTime: Long = 0
    )

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        // 初始化HTTP客戶端
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        // 建立通知頻道
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")

        // 顯示前台服務通知
        startForeground(NOTIFICATION_ID, createNotification())

        // 如果是上傳人臉資料的 action，則處理
        if (intent?.action == ACTION_UPLOAD_FACES) {
            val facesJson = intent.getStringExtra(EXTRA_FACES_JSON) ?: ""
            if (facesJson.isNotEmpty()) {
                Thread {
                    processAndUploadFaces(facesJson)
                }.start()
            }
        } else {
            // 開始人臉分析（原本邏輯）
            startFaceAnalysis()
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "人流分析服務",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "正在進行人流分析"

            val notificationManager = getSystemService(
                NotificationManager::class.java
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("人流分析系統")
            .setContentText("正在分析人流數據...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startFaceAnalysis() {
        if (isRunning) return

        isRunning = true

        analysisThread = Thread {
            while (isRunning) {
                try {
                    // 這裡應該從RTSP串流擷取影像
                    // 由於實際擷取影像需要複雜的RTSP客戶端，
                    // 這裡先實現人臉分析邏輯框架

                    // 模擬分析循環
                    Thread.sleep(2000)

                    // 清理過期的追蹤資料（超過10秒未出現）
                    cleanupOldTracks()

                } catch (e: InterruptedException) {
                    Log.e(TAG, "Analysis thread interrupted", e)
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Face analysis error", e)
                }
            }
        }

        analysisThread?.start()
    }

    private fun processAndUploadFaces(facesJson: String) {
        try {
            val arr = JSONArray(facesJson)
            val faceList = mutableListOf<FaceData>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val face = FaceData(
                    faceId = obj.optString("faceId", ""),
                    gender = obj.optString("gender", "unknown"),
                    age = obj.optInt("age", 0),
                    smile = obj.optDouble("smile", 0.0),
                    headPose = obj.optDouble("headPose", 0.0),
                    rectangleLeft = obj.optJSONObject("faceRectangle")?.optInt("left", 0) ?: 0,
                    rectangleTop = obj.optJSONObject("faceRectangle")?.optInt("top", 0) ?: 0,
                    rectangleWidth = obj.optJSONObject("faceRectangle")?.optInt("width", 0) ?: 0,
                    rectangleHeight = obj.optJSONObject("faceRectangle")?.optInt("height", 0) ?: 0
                )
                faceList.add(face)
                // 更新追蹤資料
                updateFaceTracking(face)
            }

            // 使用既有上傳邏輯上傳 faceList
            uploadAnalysisResults(faceList)

        } catch (e: Exception) {
            Log.e(TAG, "processAndUploadFaces failed", e)
        }
    }

    private fun detectFacesFromImage(imageBytes: ByteArray): List<FaceData> {
        val faceList = mutableListOf<FaceData>()

        try {
            // 從SharedPreferences獲取Azure設定
            val prefs = getSharedPreferences("PeopleAnalyserPrefs", Context.MODE_PRIVATE)
            val azureKey = prefs.getString("azure_key", "")
            val azureEndpoint = prefs.getString("azure_endpoint", "")

            if (azureKey.isNullOrEmpty() || azureEndpoint.isNullOrEmpty()) {
                Log.e(TAG, "Azure API設定不完整")
                return faceList
            }

            // 呼叫Azure Face API
            val requestBody = imageBytes.toRequestBody(
                "application/octet-stream".toMediaType()
            )

            val request = Request.Builder()
                .url("${azureEndpoint}face/v1.0/detect?returnFaceAttributes=age,gender,headPose,smile")
                .addHeader("Ocp-Apim-Subscription-Key", azureKey)
                .addHeader("Content-Type", "application/octet-stream")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (!responseBody.isNullOrEmpty()) {
                    val jsonArray = JSONArray(responseBody)

                    for (i in 0 until jsonArray.length()) {
                        val faceObject = jsonArray.getJSONObject(i)
                        val faceId = faceObject.getString("faceId")
                        val rectangle = faceObject.getJSONObject("faceRectangle")
                        val attributes = faceObject.getJSONObject("faceAttributes")

                        val faceData = FaceData(
                            faceId = faceId,
                            gender = attributes.getString("gender"),
                            age = attributes.getDouble("age").toInt(),
                            smile = attributes.getDouble("smile"),
                            headPose = attributes.getJSONObject("headPose").getDouble("yaw"),
                            rectangleLeft = rectangle.getInt("left"),
                            rectangleTop = rectangle.getInt("top"),
                            rectangleWidth = rectangle.getInt("width"),
                            rectangleHeight = rectangle.getInt("height")
                        )

                        faceList.add(faceData)

                        // 更新追蹤資料
                        updateFaceTracking(faceData)
                    }

                    // 上傳資料
                    uploadAnalysisResults(faceList)
                }
            } else {
                Log.e(TAG, "Azure API呼叫失敗: ${response.code}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Face detection failed", e)
        }

        return faceList
    }

    private fun updateFaceTracking(faceData: FaceData) {
        val currentTime = System.currentTimeMillis()

        if (faceTrackingMap.containsKey(faceData.faceId)) {
            // 更新現有追蹤
            val trackingData = faceTrackingMap[faceData.faceId]!!
            trackingData.lastSeen = currentTime
            trackingData.dwellTime = currentTime - trackingData.firstSeen

            // 更新性別和年齡（如果之前沒有）
            if (trackingData.gender == null) trackingData.gender = faceData.gender
            if (trackingData.age == null) trackingData.age = faceData.age

            // 計算注視時間（如果頭部角度在一定範圍內）
            val headYaw = faceData.headPose
            if (headYaw in -15.0..15.0) { // 假設正視攝影機
                if (trackingData.gazeStartTime == null) {
                    trackingData.gazeStartTime = currentTime
                } else {
                    trackingData.totalGazeTime = currentTime - trackingData.gazeStartTime!!
                }
            } else {
                trackingData.gazeStartTime = null
            }

        } else {
            // 新增追蹤
            val trackingData = FaceTrackingData(
                faceId = faceData.faceId,
                firstSeen = currentTime,
                lastSeen = currentTime,
                gender = faceData.gender,
                age = faceData.age
            )
            faceTrackingMap[faceData.faceId] = trackingData
        }
    }

    private fun cleanupOldTracks() {
        val currentTime = System.currentTimeMillis()
        val timeout = 10000L // 10秒超時

        val expiredFaces = faceTrackingMap.filter {
            currentTime - it.value.lastSeen > timeout
        }.keys

        // 上傳過期的人臉資料
        expiredFaces.forEach { faceId ->
            faceTrackingMap[faceId]?.let { trackingData ->
                uploadFaceDataToServer(trackingData)
            }
        }

        // 移除過期的追蹤
        expiredFaces.forEach { faceTrackingMap.remove(it) }
    }

    private fun uploadAnalysisResults(faceList: List<FaceData>) {
        if (faceList.isEmpty()) return

        Thread {
            try {
                // 從SharedPreferences獲取設定
                val prefs = getSharedPreferences("PeopleAnalyserPrefs", Context.MODE_PRIVATE)
                val serverUrl = prefs.getString("server_url", "") ?: ""
                val storeId = prefs.getString("store_id", "store_001") ?: "store_001"

                if (serverUrl.isEmpty()) {
                    Log.w(TAG, "伺服器URL未設定")
                    return@Thread
                }

                val jsonArray = JSONArray()

                faceList.forEach { faceData ->
                    val jsonObject = JSONObject().apply {
                        put("store_id", storeId)
                        put("device_id", Build.MODEL)
                        put("timestamp", System.currentTimeMillis())
                        put("face_id", faceData.faceId)
                        put("gender", faceData.gender)
                        put("age", faceData.age)
                        put("smile_score", faceData.smile)
                        put("head_yaw", faceData.headPose)
                        put("dwell_time", faceTrackingMap[faceData.faceId]?.dwellTime ?: 0)
                        put("gaze_time", faceTrackingMap[faceData.faceId]?.totalGazeTime ?: 0)
                    }
                    jsonArray.put(jsonObject)
                }

                val requestBody = jsonArray.toString().toRequestBody(
                    "application/json".toMediaType()
                )

                val request = Request.Builder()
                    .url(serverUrl)
                    .post(requestBody)
                    .build()

                val response = okHttpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    Log.d(TAG, "資料上傳成功: ${faceList.size} 筆")
                } else {
                    Log.w(TAG, "資料上傳失敗: ${response.code}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "上傳失敗", e)
            }
        }.start()
    }

    private fun uploadFaceDataToServer(trackingData: FaceTrackingData) {
        Thread {
            try {
                val prefs = getSharedPreferences("PeopleAnalyserPrefs", Context.MODE_PRIVATE)
                val serverUrl = prefs.getString("server_url", "") ?: ""
                val storeId = prefs.getString("store_id", "store_001") ?: "store_001"

                if (serverUrl.isEmpty()) return@Thread

                val jsonData = JSONObject().apply {
                    put("store_id", storeId)
                    put("device_id", Build.MODEL)
                    put("timestamp", System.currentTimeMillis())
                    put("face_id", trackingData.faceId)
                    put("gender", trackingData.gender ?: "unknown")
                    put("age", trackingData.age ?: 0)
                    put("dwell_time_seconds", trackingData.dwellTime / 1000)
                    put("gaze_time_seconds", trackingData.totalGazeTime / 1000)
                    put("first_seen", trackingData.firstSeen)
                    put("last_seen", trackingData.lastSeen)
                }

                val requestBody = jsonData.toString().toRequestBody(
                    "application/json".toMediaType()
                )

                val request = Request.Builder()
                    .url(serverUrl)
                    .post(requestBody)
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d(TAG, "人臉資料上傳成功: ${trackingData.faceId}")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "人臉資料上傳失敗", e)
            }
        }.start()
    }


    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")

        isRunning = false
        analysisThread?.interrupt()
        analysisThread = null

        stopForeground(true)
    }
}