package com.uka.peopleanalyser

import org.json.JSONObject

data class FaceData(
    val faceId: String,
    val gender: String,
    val age: Int,
    val smile: Double,
    val headPose: Double,
    val rectangleLeft: Int,
    val rectangleTop: Int,
    val rectangleWidth: Int,
    val rectangleHeight: Int
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("faceId", faceId)
        obj.put("gender", gender)
        obj.put("age", age)
        obj.put("smile", smile)
        obj.put("headPose", headPose)
        val rect = JSONObject()
        rect.put("left", rectangleLeft)
        rect.put("top", rectangleTop)
        rect.put("width", rectangleWidth)
        rect.put("height", rectangleHeight)
        obj.put("faceRectangle", rect)
        return obj
    }
}

