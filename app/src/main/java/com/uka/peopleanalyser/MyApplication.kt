package com.uka.peopleanalyser

import android.app.Application
import android.content.Context
import android.util.Log

class MyApplication : Application() {

    private val TAG = "MyApplication"

    companion object {
        // Flag to indicate we successfully set or initialized USBMonitor instance
        @Volatile
        var usbMonitorReady: Boolean = false
            private set

        fun isUsbMonitorReady(): Boolean = usbMonitorReady
    }

    override fun onCreate() {
        super.onCreate()
        initUsbMonitor(this)
    }

    private fun initUsbMonitor(context: Context) {
        try {
            val usbClass = Class.forName("com.serenegiant.usb.USBMonitor")
            var instance: Any? = null

            // 1) 優先嘗試靜態 getInstance(Context)
            try {
                val m = usbClass.getMethod("getInstance", Context::class.java)
                instance = m.invoke(null, context)
                Log.i(TAG, "USBMonitor.getInstance(Context) invoked")
            } catch (e: NoSuchMethodException) {
                Log.i(TAG, "getInstance(Context) not found on USBMonitor")
            } catch (e: Exception) {
                Log.w(TAG, "getInstance(Context) invocation failed: ${e.message}")
            }

            // 2) 若無，嘗試用建構子建立（Context 或 無參）
            if (instance == null) {
                try {
                    val ctor = usbClass.getConstructor(Context::class.java)
                    instance = ctor.newInstance(context)
                    Log.i(TAG, "USBMonitor(Context) constructor used")
                } catch (e: NoSuchMethodException) {
                    try {
                        val ctor0 = usbClass.getConstructor()
                        instance = ctor0.newInstance()
                        Log.i(TAG, "USBMonitor() constructor used")
                    } catch (e2: Exception) {
                        Log.i(TAG, "No suitable constructor for USBMonitor: ${e2.message}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "constructor instantiation failed: ${e.message}")
                }
            }

            // 3) 嘗試設置常見靜態欄位名稱
            val candidates = arrayOf("instance", "sInstance", "INSTANCE", "mInstance")
            var setOk = false
            for (name in candidates) {
                try {
                    val f = usbClass.getDeclaredField(name)
                    f.isAccessible = true
                    f.set(null, instance)
                    Log.i(TAG, "Set static field '$name' on USBMonitor")
                    setOk = true
                    break
                } catch (e: NoSuchFieldException) {
                    // 繼續下一個候選
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to set static field '$name': ${e.message}")
                }
            }

            // 4) Kotlin Companion 或 object 的 instance
            if (!setOk) {
                try {
                    val compField = usbClass.getDeclaredField("Companion")
                    compField.isAccessible = true
                    val companion = compField.get(null)
                    val instNames = arrayOf("instance", "INSTANCE", "sInstance", "mInstance")
                    for (n in instNames) {
                        try {
                            val instField = companion.javaClass.getDeclaredField(n)
                            instField.isAccessible = true
                            instField.set(companion, instance)
                            Log.i(TAG, "Set Companion.$n on USBMonitor")
                            setOk = true
                            break
                        } catch (e: NoSuchFieldException) {
                            // 繼續
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to set Companion.$n: ${e.message}")
                        }
                    }
                } catch (e: NoSuchFieldException) {
                    Log.i(TAG, "Companion field not present on USBMonitor")
                } catch (e: Exception) {
                    Log.w(TAG, "Companion handling failed: ${e.message}")
                }
            }

            if (!setOk) {
                Log.w(TAG, "Could not set USBMonitor instance; lateinit error may still occur")
                usbMonitorReady = false
            } else {
                usbMonitorReady = true
            }
        } catch (e: ClassNotFoundException) {
            Log.i(TAG, "USBMonitor class not found; USB features disabled")
            usbMonitorReady = false
        } catch (ue: kotlin.UninitializedPropertyAccessException) {
            // If the library itself throws because its lateinit property isn't set, catch it here
            Log.w(TAG, "USBMonitor UninitializedPropertyAccessException: ${ue.message}")
            usbMonitorReady = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize USBMonitor", e)
            usbMonitorReady = false
        }
    }

}
