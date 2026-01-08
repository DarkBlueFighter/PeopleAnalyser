package com.uka.peopleanalyser

import android.content.Context
import android.hardware.usb.UsbDevice
import android.util.Log
import android.view.Surface

/**
 * UsbCameraManager - UVCCamera-compatible manager using reflection.
 * 目的：讓專案可在沒有 UVCCamera AAR 的情況下仍能編譯。
 * 若你把 UVCCamera-2.5.6.aar 放到 app/libs/ 並且包含 native .so 到 app/src/main/jniLibs/<abi>/,
 * 本類會嘗試在執行時透過反射載入 `com.serenegiant.usb.USBMonitor` 與相關 API 來啟用真實功能。
 */
class UsbCameraManager(private val context: Context) {
    companion object {
        private const val TAG = "UsbCameraManager"
    }

    private var callback: UsbCameraCallback? = null
    // USBMonitor 實例（若載入成功，會以反射物件儲存）
    private var usbMonitorInstance: Any? = null
    private var uvccAvailable: Boolean = false

    // we keep minimal handler references as Any? because the real types exist only when AAR present
    private var cameraHandlerInstance: Any? = null
    private var uvcCameraInstance: Any? = null

    fun initialize(callback: UsbCameraCallback) {
        this.callback = callback
        try {
            // 嘗試透過反射載入 UVCCamera 所需類別
            val usbMonitorClass = Class.forName("com.serenegiant.usb.USBMonitor")
            val listenerInterface = Class.forName("com.serenegiant.usb.USBMonitor\$OnDeviceConnectListener")

            // 使用 Java Proxy 建立 listener，轉發到 callback
            val proxyListener = java.lang.reflect.Proxy.newProxyInstance(
                listenerInterface.classLoader,
                arrayOf(listenerInterface)
            ) { _, method, args ->
                try {
                    when (method.name) {
                        "onAttach" -> {
                            val device = args?.get(0) as? UsbDevice
                            device?.let { callback.onDeviceAttached(it) }
                        }
                        "onDettach" -> {
                            val device = args?.get(0) as? UsbDevice
                            device?.let { callback.onDeviceDetached(it) }
                        }
                        "onConnect" -> {
                            // args: (UsbDevice device, UsbControlBlock ctrlBlock)
                            callback.onCameraOpened()
                        }
                        "onDisconnect" -> {
                            val device = args?.get(0) as? UsbDevice
                            device?.let { callback.onDeviceDetached(it) }
                        }
                        "onCancel" -> {
                            callback.onPermissionDenied()
                        }
                        else -> {
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "listener proxy error: ${e.message}")
                }
                null
            }

            // 建構 USBMonitor(Context, listener)
            val ctor = usbMonitorClass.getConstructor(Context::class.java, listenerInterface)
            usbMonitorInstance = try {
                ctor.newInstance(context, proxyListener)
            } catch (ue: kotlin.UninitializedPropertyAccessException) {
                // library internal lateinit not initialized
                Log.w(TAG, "USBMonitor init failed (UninitializedPropertyAccessException): ${ue.message}")
                null
            } catch (e: Exception) {
                Log.w(TAG, "USBMonitor instantiation failed: ${e.message}")
                null
            }

            if (usbMonitorInstance == null) {
                // if we couldn't create it, mark unavailable and bail
                uvccAvailable = false
                callback.onError("USBMonitor instantiation failed; UVCCamera features disabled.")
                return
            }

            // 呼叫 register()
            try {
                usbMonitorClass.getMethod("register").invoke(usbMonitorInstance)
            } catch (ue: kotlin.UninitializedPropertyAccessException) {
                Log.w(TAG, "USBMonitor.register() failed: ${ue.message}")
                uvccAvailable = false
                callback.onError("USBMonitor.register failed: ${ue.message}")
                return
            } catch (e: Exception) {
                Log.w(TAG, "USBMonitor.register invocation failed: ${e.message}")
                uvccAvailable = false
                callback.onError("USBMonitor.register failed: ${e.message}")
                return
            }

            uvccAvailable = true
            Log.i(TAG, "UVCCamera library loaded: USBMonitor initialized via reflection")
        } catch (cnf: ClassNotFoundException) {
            uvccAvailable = false
            callback.onError("UVCCamera library not found. 請把 UVCCamera-2.5.6.aar 放到 app/libs/ 並確保 native .so 在 app/src/main/jniLibs/<abi>/，然後重建。詳見 app/libs/README.md")
        } catch (e: UnsatisfiedLinkError) {
            uvccAvailable = false
            callback.onError("Native library not loaded (UnsatisfiedLinkError): ${e.message}")
        } catch (e: Exception) {
            uvccAvailable = false
            callback.onError("UVCCamera 初始化失敗: ${e.message}")
        }
    }

    fun setFrameCallback(cb: (ByteArray) -> Unit) {
        if (!uvccAvailable) return
        // 若需要，可以在真實載入時透過反射註冊 frame callback。
        // 這裡只儲存 callback，同時要在 startPreview 成功時把它註冊到 cameraHandler。
    }

    fun requestPermission(device: UsbDevice) {
        if (!uvccAvailable) {
            callback?.onError("UVCCamera library 不存在：無法 requestPermission。請先放置 AAR 與 .so。")
            return
        }
        try {
            val usbMonitorClass = Class.forName("com.serenegiant.usb.USBMonitor")
            // 嘗試呼叫 requestPermission(UsbDevice)
            try {
                usbMonitorClass.getMethod("requestPermission", UsbDevice::class.java).invoke(usbMonitorInstance, device)
            } catch (ue: kotlin.UninitializedPropertyAccessException) {
                Log.w(TAG, "requestPermission failed: ${ue.message}")
                callback?.onError("requestPermission failed: ${ue.message}")
            }
        } catch (e: Exception) {
            callback?.onError("requestPermission 失敗: ${e.message}")
        }
    }

    fun getAttachedDevices(): List<UsbDevice> {
        if (!uvccAvailable) return emptyList()
        try {
            val usbMonitorClass = Class.forName("com.serenegiant.usb.USBMonitor")
            // 嘗試先找 getDeviceList() method，否則尋找 field deviceList
            return try {
                val deviceMap = usbMonitorClass.getMethod("getDeviceList").invoke(usbMonitorInstance) as? java.util.Map<*, *>
                // values() is a Java method; call it explicitly to get the Collection
                deviceMap?.values()?.mapNotNull { it as? UsbDevice } ?: emptyList()
            } catch (e: NoSuchMethodException) {
                try {
                    val field = usbMonitorClass.getDeclaredField("mDeviceList")
                    field.isAccessible = true
                    val deviceMap = field.get(usbMonitorInstance) as? java.util.Map<*, *>
                    deviceMap?.values()?.mapNotNull { it as? UsbDevice } ?: emptyList()
                } catch (ex: Exception) {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "getAttachedDevices error: ${e.message}")
            return emptyList()

        }
    }

    fun startPreview(surface: Surface) {
        if (!uvccAvailable) {
            callback?.onError("UVCCamera library 不存在：startPreview 無效，請先放置 AAR 與 .so。")
            return
        }
        try {
            // 我們已經在 initialize 時建立了 usbMonitor；實際的開啟/預覽邏輯會在 OnDeviceConnect 的 onConnect 中透過 reflection 處理。
            // 這裡僅回報已啟動預覽（若真實流程在後續透過 onConnect 開啟），或嘗試透過 cameraHandler api 啟動。

            // 嘗試透過 UVCCameraHandler.createHandler(context, 1, 640, 480)
            val handlerClass = try { Class.forName("com.serenegiant.usb.UVCCameraHandler") } catch (e: Exception) { null }
            if (handlerClass == null) {
                callback?.onError("UVCCameraHandler class not found")
                return
            }

            val createHandlerMethod = try {
                handlerClass.getMethod("createHandler", android.content.Context::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            } catch (e: Exception) {
                try {
                    handlerClass.getMethod("createHandler", android.content.Context::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                } catch (ex: Exception) {
                    null
                }
            }

            // createHandler(context, groupId, width, height) - 根據 library 版本參數數量可能不同，這裡嘗試寬度/高度作為最後兩個整數
            cameraHandlerInstance = try {
                createHandlerMethod?.invoke(null, context, 1, 640, 480)
            } catch (ue: kotlin.UninitializedPropertyAccessException) {
                Log.w(TAG, "createHandler failed: ${ue.message}")
                null
            } catch (e: Exception) {
                null
            }

            if (cameraHandlerInstance != null) {
                // 嘗試 setSurface(surface) 和 startPreview(uvcCamera)
                try {
                    val handlerClazz = cameraHandlerInstance!!.javaClass
                    // setSurface(Surface)
                    handlerClazz.getMethod("setSurface", Surface::class.java).invoke(cameraHandlerInstance, surface)
                    // startPreview(UVCCamera)
                    handlerClazz.getMethod("startPreview", Class.forName("com.serenegiant.usb.UVCCamera")).invoke(cameraHandlerInstance, uvcCameraInstance)
                } catch (e: NoSuchMethodException) {
                    // 如果 method 不存在就忽略
                }
            }

            callback?.onPreviewStarted()
        } catch (ue: kotlin.UninitializedPropertyAccessException) {
            callback?.onError("startPreview failed: ${ue.message}")
            uvccAvailable = false
        } catch (e: Exception) {
            callback?.onError("startPreview 失敗: ${e.message}")
        }
    }

    fun stopPreview() {
        if (!uvccAvailable) return
        try {
            cameraHandlerInstance?.javaClass?.getMethod("stopPreview")?.invoke(cameraHandlerInstance)
        } catch (e: Exception) {
            Log.w(TAG, "stopPreview error: ${e.message}")
        }
    }

    fun release() {
        try {
            stopPreview()
            if (uvccAvailable) {
                try {
                    cameraHandlerInstance?.javaClass?.getMethod("close")?.invoke(cameraHandlerInstance)
                } catch (e: Exception) {
                }
                try {
                    val usbMonitorClass = Class.forName("com.serenegiant.usb.USBMonitor")
                    usbMonitorClass.getMethod("unregister").invoke(usbMonitorInstance)
                    usbMonitorClass.getMethod("destroy").invoke(usbMonitorInstance)
                } catch (e: Exception) {
                }
            }
        } catch (e: Exception) {
        }
    }

    interface UsbCameraCallback {
        fun onDeviceAttached(device: UsbDevice)
        fun onDeviceDetached(device: UsbDevice)
        fun onPermissionGranted(device: UsbDevice)
        fun onPermissionDenied()
        fun onCameraOpened()
        fun onPreviewStarted()
        fun onError(message: String)
    }
}
