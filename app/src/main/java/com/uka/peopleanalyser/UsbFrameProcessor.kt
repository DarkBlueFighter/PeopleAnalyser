package com.uka.peopleanalyser

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * USB摄像头帧处理助手
 * 用于处理从USB摄像头获取的图像帧数据
 */
class UsbFrameProcessor {

    companion object {
        private const val TAG = "UsbFrameProcessor"
    }

    /**
     * 将YUV420SP格式的数据转换为Bitmap
     *
     * @param data YUV数据
     * @param width 图像宽度
     * @param height 图像高度
     * @return Bitmap对象
     */
    fun yuv420spToBitmap(data: ByteArray, width: Int, height: Int): Bitmap? {
        return try {
            val yuvImage = YuvImage(data, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 80, out)
            val imageBytes = out.toByteArray()
            android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "转换Bitmap失败", e)
            null
        }
    }

    /**
     * 保存帧数据为JPEG文件
     *
     * @param data YUV数据
     * @param width 图像宽度
     * @param height 图像高度
     * @param outputFile 输出文件
     * @return 是否保存成功
     */
    fun saveFrameAsJpeg(data: ByteArray, width: Int, height: Int, outputFile: File): Boolean {
        return try {
            val yuvImage = YuvImage(data, ImageFormat.NV21, width, height, null)
            FileOutputStream(outputFile).use { fos ->
                yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, fos)
            }
            Log.d(TAG, "帧已保存到: ${outputFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "保存帧失败", e)
            false
        }
    }

    /**
     * 分析帧数据（示例：计算平均亮度）
     *
     * @param data YUV数据
     * @param width 图像宽度
     * @param height 图像高度
     * @return 平均亮度值 (0-255)
     */
    fun calculateBrightness(data: ByteArray, width: Int, height: Int): Int {
        if (data.isEmpty()) return 0

        // YUV420SP格式中，前width*height个字节是Y分量（亮度）
        val ySize = width * height
        var sum = 0L

        for (i in 0 until ySize.coerceAtMost(data.size)) {
            sum += (data[i].toInt() and 0xFF)
        }

        return (sum / ySize).toInt()
    }

    /**
     * 检测图像变化（简单的移动检测）
     *
     * @param currentFrame 当前帧
     * @param previousFrame 上一帧
     * @param width 图像宽度
     * @param height 图像高度
     * @param threshold 变化阈值
     * @return 变化百分比 (0-100)
     */
    fun detectMotion(
        currentFrame: ByteArray,
        previousFrame: ByteArray,
        width: Int,
        height: Int,
        threshold: Int = 30
    ): Float {
        if (currentFrame.size != previousFrame.size) return 0f

        val ySize = width * height
        var changedPixels = 0

        for (i in 0 until ySize.coerceAtMost(currentFrame.size)) {
            val diff = Math.abs((currentFrame[i].toInt() and 0xFF) - (previousFrame[i].toInt() and 0xFF))
            if (diff > threshold) {
                changedPixels++
            }
        }

        return (changedPixels.toFloat() / ySize) * 100
    }

    /**
     * 下采样图像数据（减少数据量以提高处理速度）
     *
     * @param data 原始YUV数据
     * @param width 原始宽度
     * @param height 原始高度
     * @param scale 缩放比例（2表示宽高各缩小一半）
     * @return 下采样后的数据
     */
    fun downsampleFrame(data: ByteArray, width: Int, height: Int, scale: Int): ByteArray {
        val newWidth = width / scale
        val newHeight = height / scale
        val result = ByteArray(newWidth * newHeight)

        for (y in 0 until newHeight) {
            for (x in 0 until newWidth) {
                val srcY = y * scale
                val srcX = x * scale
                result[y * newWidth + x] = data[srcY * width + srcX]
            }
        }

        return result
    }

    /**
     * 帧处理回调接口
     */
    interface FrameCallback {
        fun onFrameProcessed(bitmap: Bitmap?, brightness: Int, motionLevel: Float)
    }
}
