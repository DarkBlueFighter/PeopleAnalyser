package com.uka.peopleanalyser.examples

/**
 * USB摄像头使用示例
 *
 * 此文件展示如何使用UsbCameraManager和UsbFrameProcessor
 */

/*
// ============================================
// 示例 1: 基本使用 - 显示USB摄像头预览
// ============================================

class YourActivity : AppCompatActivity() {

    private lateinit var usbCameraManager: UsbCameraManager
    private lateinit var surfaceView: SurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化USB摄像头管理器
        usbCameraManager = UsbCameraManager(this)
        usbCameraManager.initialize(object : UsbCameraManager.UsbCameraCallback {

            override fun onDeviceAttached(device: UsbDevice) {
                Log.d(TAG, "USB设备已连接")
            }

            override fun onDeviceDetached(device: UsbDevice) {
                Log.d(TAG, "USB设备已断开")
            }

            override fun onPermissionGranted(device: UsbDevice) {
                Log.d(TAG, "USB权限已授予")
            }

            override fun onPermissionDenied() {
                Log.d(TAG, "USB权限被拒绝")
            }

            override fun onCameraOpened() {
                // 摄像头已打开，开始预览
                if (surfaceView.holder.surface.isValid) {
                    usbCameraManager.startPreview(surfaceView.holder.surface)
                }
            }

            override fun onPreviewStarted() {
                Log.d(TAG, "预览已启动")
            }

            override fun onError(message: String) {
                Log.e(TAG, "错误: $message")
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        usbCameraManager.release()
    }
}

// ============================================
// 示例 2: 获取并处理帧数据
// ============================================

class YourActivityWithFrameProcessing : AppCompatActivity() {

    private lateinit var usbCameraManager: UsbCameraManager
    private val frameProcessor = UsbFrameProcessor()
    private var previousFrame: ByteArray? = null

    private fun setupFrameCallback() {
        usbCameraManager.setFrameCallback(object : IFrameCallback {
            override fun onFrame(frame: ByteBuffer?) {
                frame?.let { buffer ->
                    // 将ByteBuffer转换为ByteArray
                    val data = ByteArray(buffer.remaining())
                    buffer.get(data)

                    // 处理帧数据
                    processFrame(data, 640, 480)
                }
            }
        })
    }

    private fun processFrame(data: ByteArray, width: Int, height: Int) {
        // 1. 转换为Bitmap（如果需要显示或进一步处理）
        val bitmap = frameProcessor.yuv420spToBitmap(data, width, height)

        // 2. 计算亮度
        val brightness = frameProcessor.calculateBrightness(data, width, height)

        // 3. 检测运动
        val motionLevel = previousFrame?.let { prev ->
            frameProcessor.detectMotion(data, prev, width, height)
        } ?: 0f

        // 4. 保存当前帧作为下一帧的参考
        previousFrame = data.copyOf()

        // 5. 在UI线程更新显示
        runOnUiThread {
            updateUI(bitmap, brightness, motionLevel)
        }

        // 6. 如果需要，可以保存特定帧
        if (motionLevel > 20f) { // 检测到显著运动
            val file = File(filesDir, "motion_${System.currentTimeMillis()}.jpg")
            frameProcessor.saveFrameAsJpeg(data, width, height, file)
        }
    }

    private fun updateUI(bitmap: Bitmap?, brightness: Int, motionLevel: Float) {
        // 更新UI显示
        tvBrightness.text = "亮度: $brightness"
        tvMotion.text = "运动: ${String.format("%.2f", motionLevel)}%"
    }
}

// ============================================
// 示例 3: 性能优化 - 下采样处理
// ============================================

class OptimizedFrameProcessing : AppCompatActivity() {

    private val frameProcessor = UsbFrameProcessor()
    private var frameCount = 0

    private fun processFrameOptimized(data: ByteArray, width: Int, height: Int) {
        frameCount++

        // 只处理每第5帧，减少CPU负载
        if (frameCount % 5 != 0) return

        // 下采样到原来的1/4大小
        val downsampledData = frameProcessor.downsampleFrame(data, width, height, 2)
        val newWidth = width / 2
        val newHeight = height / 2

        // 在下采样的数据上进行处理（更快）
        val brightness = frameProcessor.calculateBrightness(downsampledData, newWidth, newHeight)

        // 使用下采样的数据进行AI分析等
        // ...
    }
}

// ============================================
// 示例 4: 与AI模型集成
// ============================================

class AIIntegrationExample : AppCompatActivity() {

    private val frameProcessor = UsbFrameProcessor()
    // private val aiModel = YourAIModel() // 您的AI模型

    private fun analyzeFrameWithAI(data: ByteArray, width: Int, height: Int) {
        // 1. 转换为Bitmap
        val bitmap = frameProcessor.yuv420spToBitmap(data, width, height)

        bitmap?.let {
            // 2. 调整大小以匹配AI模型输入
            val resizedBitmap = Bitmap.createScaledBitmap(it, 224, 224, true)

            // 3. 发送到AI模型进行人脸/人体检测
            // val results = aiModel.detectPeople(resizedBitmap)

            // 4. 处理结果
            // results.forEach { person ->
            //     Log.d(TAG, "检测到人物: ${person.confidence}")
            // }
        }
    }
}

// ============================================
// 示例 5: 多线程处理
// ============================================

class MultiThreadedProcessing : AppCompatActivity() {

    private val frameProcessor = UsbFrameProcessor()
    private val processingExecutor = Executors.newSingleThreadExecutor()

    private fun setupFrameCallback() {
        usbCameraManager.setFrameCallback(object : IFrameCallback {
            override fun onFrame(frame: ByteBuffer?) {
                frame?.let { buffer ->
                    val data = ByteArray(buffer.remaining())
                    buffer.get(data)

                    // 在后台线程处理，避免阻塞
                    processingExecutor.execute {
                        processFrameInBackground(data, 640, 480)
                    }
                }
            }
        })
    }

    private fun processFrameInBackground(data: ByteArray, width: Int, height: Int) {
        // 耗时的处理操作
        val bitmap = frameProcessor.yuv420spToBitmap(data, width, height)
        val brightness = frameProcessor.calculateBrightness(data, width, height)

        // 处理完成后更新UI
        runOnUiThread {
            // 更新UI
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        processingExecutor.shutdown()
    }
}

// ============================================
// 示例 6: 检查USB设备列表
// ============================================

class CheckUsbDevices : AppCompatActivity() {

    private lateinit var usbCameraManager: UsbCameraManager

    private fun checkConnectedDevices() {
        val devices = usbCameraManager.getAttachedDevices()

        if (devices.isEmpty()) {
            Toast.makeText(this, "未检测到USB摄像头", Toast.LENGTH_SHORT).show()
        } else {
            devices.forEach { device ->
                Log.d(TAG, """
                    设备名称: ${device.deviceName}
                    设备ID: ${device.deviceId}
                    供应商ID: ${device.vendorId}
                    产品ID: ${device.productId}
                """.trimIndent())
            }
        }
    }
}
*/

/**
 * 注意事项:
 *
 * 1. 性能优化
 *    - 使用下采样减少数据量
 *    - 只处理关键帧（例如每5帧处理1次）
 *    - 在后台线程处理帧数据
 *
 * 2. 内存管理
 *    - 及时释放Bitmap对象
 *    - 使用对象池重用ByteArray
 *    - 在onDestroy中释放所有资源
 *
 * 3. 错误处理
 *    - 检查空指针
 *    - 处理USB设备意外断开
 *    - 处理权限被拒绝情况
 *
 * 4. 设备兼容性
 *    - 不是所有Android设备都支持USB Host模式
 *    - 某些USB摄像头可能不兼容
 *    - 建议在应用启动时检查设备支持情况
 */

