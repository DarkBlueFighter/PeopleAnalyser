package com.uka.peopleanalyser

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast

/**
 * LinkUtils: 幫助在聊天室或訊息列表的 TextView 上啟用連結點擊而不搶焦點／造成滾動跳動。
 *
 * 使用：在你的 message view binding 或 adapter 裡呼叫：
 * LinkUtils.makeLinksClickable(yourTextView)
 *
 * 重點行為：
 * - 設定 MovementMethod 讓連結可點擊
 * - 關閉 focusable / focusableInTouchMode，避免系統在點擊時自動 requestFocus 導致滾動
 * - 攔截 touch 事件以避免 parent 自動 scrollToChild（但不吞掉點擊事件）
 * - 提供長按複製到剪貼簿 (可選)
 */
object LinkUtils {
    /**
     * 啟用 TextView 的安全連結處理：連結可點但不會搶焦、也不會造成父容器跳動。
     * - 必須在 TextView 設定好 text（包含 URLSpan / ClickableSpan）之後呼叫。
     */
    @SuppressLint("ClickableViewAccessibility")
    @JvmStatic
    fun makeLinksClickable(tv: TextView, enableLongPressCopy: Boolean = true) {
        // 讓 TextView 支援點選連結
        tv.movementMethod = LinkMovementMethod.getInstance()
        tv.linksClickable = true

        // 關閉焦點，以免點擊時系統嘗試把焦點移到此 view，導致 parent scroll
        tv.isFocusable = false
        tv.isFocusableInTouchMode = false

        // 保留可點擊與長按行為
        tv.isClickable = true
        tv.isLongClickable = enableLongPressCopy

        // 攔截 touch 事件，告訴 parent 不要攔截（避免 RecyclerView/ScrollView 的自動滾動行為）
        // 但我們回傳 false，讓後續的 click / link span 還是會被處理。
        tv.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (v == null || event == null) return false

                val action = event.actionMasked

                // 只有在 DOWN 或 MOVE 時要求 parent 不攔截，避免在不必要的事件中大量呼叫
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    return false
                }

                // 在 ACTION_UP 時，我們先判斷是否點到了 ClickableSpan（link）
                if (action == MotionEvent.ACTION_UP) {
                    val tvView = v as? TextView
                    var touchedLink = false

                    if (tvView != null) {
                        try {
                            val layout = tvView.layout
                            if (layout != null) {
                                // 計算觸控位置相對於文字內容的位置
                                val x = (event.x - tvView.totalPaddingLeft + tvView.scrollX).coerceAtLeast(0f)
                                val y = (event.y - tvView.totalPaddingTop + tvView.scrollY).coerceAtLeast(0f)

                                val line = layout.getLineForVertical(y.toInt())
                                val offset = layout.getOffsetForHorizontal(line, x)

                                val text = tvView.text
                                if (text is Spannable) {
                                    val spans = text.getSpans(offset, offset, ClickableSpan::class.java)
                                    if (spans != null && spans.isNotEmpty()) {
                                        // 點到 link，讓 LinkMovementMethod 處理（不要呼叫 performClick）
                                        touchedLink = true
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // 保守回退：若偵測失敗，允許 performClick 確保 click 不會遺失
                            Log.w("LinkUtils", "hit-test for ClickableSpan failed", e)
                        }
                    }

                    if (!touchedLink) {
                        // 非 link 區域，當作一般點擊處理
                        v.performClick()
                    }

                    // 不攔截事件，讓系統與 LinkMovementMethod 處理後續
                    return false
                }

                return false
            }
        })

        if (enableLongPressCopy) {
            tv.setOnLongClickListener { v ->
                try {
                    val text = (v as? TextView)?.text?.toString() ?: return@setOnLongClickListener false
                    val cm = v.context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = ClipData.newPlainText("text", text)
                    cm?.setPrimaryClip(clip)

                    // 無障礙提示（API 16+），同時保留 Toast 作為視覺反饋
                    val msg = try {
                        v.context.getString(R.string.copied_message)
                    } catch (e: Exception) {
                        "已複製訊息"
                    }

                    try {
                        v.announceForAccessibility(msg)
                    } catch (ignored: Exception) {
                        // 若 announceForAccessibility 在某些環境失敗，僅記錄不阻斷
                        Log.w("LinkUtils", "announceForAccessibility failed", ignored)
                    }

                    Toast.makeText(v.context, msg, Toast.LENGTH_SHORT).show()
                    true
                } catch (e: Exception) {
                    Log.w("LinkUtils", "long press copy failed", e)
                    false
                }
            }
        }
    }
}
