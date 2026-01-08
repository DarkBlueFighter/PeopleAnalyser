package com.uka.peopleanalyser

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray

class FaceApiClient(private val context: Context) {

    private val client = OkHttpClient()

    /**
     * 傳入 JPEG bytes（application/octet-stream）呼叫 Azure Face API，回傳 FaceData 列表
     */
    fun detectFaces(jpegBytes: ByteArray): List<FaceData> {
        val prefs = context.getSharedPreferences("PeopleAnalyserPrefs", Context.MODE_PRIVATE)
        val azureKey = prefs.getString("azure_key", "") ?: ""
        val azureEndpoint = prefs.getString("azure_endpoint", "") ?: ""

        if (azureKey.isNullOrEmpty() || azureEndpoint.isNullOrEmpty()) return emptyList()

        val url = "${azureEndpoint}face/v1.0/detect?returnFaceAttributes=age,gender,headPose,smile"
        val body = jpegBytes.toRequestBody("application/octet-stream".toMediaType())

        val request = Request.Builder()
            .url(url)
            .addHeader("Ocp-Apim-Subscription-Key", azureKey)
            .addHeader("Content-Type", "application/octet-stream")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val respBody = response.body?.string() ?: return emptyList()
            val arr = JSONArray(respBody)
            val result = mutableListOf<FaceData>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val faceId = obj.optString("faceId", "")
                val rect = obj.optJSONObject("faceRectangle")
                val attrs = obj.optJSONObject("faceAttributes")
                val gender = attrs?.optString("gender", "unknown") ?: "unknown"
                val age = attrs?.optDouble("age", 0.0)?.toInt() ?: 0
                val smile = attrs?.optDouble("smile", 0.0) ?: 0.0
                val headPose = attrs?.optJSONObject("headPose")?.optDouble("yaw", 0.0) ?: 0.0

                val left = rect?.optInt("left", 0) ?: 0
                val top = rect?.optInt("top", 0) ?: 0
                val width = rect?.optInt("width", 0) ?: 0
                val height = rect?.optInt("height", 0) ?: 0

                val face = FaceData(
                    faceId = faceId,
                    gender = gender,
                    age = age,
                    smile = smile,
                    headPose = headPose,
                    rectangleLeft = left,
                    rectangleTop = top,
                    rectangleWidth = width,
                    rectangleHeight = height
                )
                result.add(face)
            }
            return result
        }
    }
}

