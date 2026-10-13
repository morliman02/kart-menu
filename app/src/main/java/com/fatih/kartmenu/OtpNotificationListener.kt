package com.fatih.kartmenu

import android.app.Notification
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.widget.Toast

/**
 * Mail (veya başka) bildirimlerini dinler ve içinde OTP/doğrulama kodu geçen bir bildirim
 * gelince 6 haneli kodu yakalar. Kodu belleğe ([OtpHolder]) koyar; yüzen menüdeki "Son OTP"
 * satırından tek dokunuşla Amazon'daki kutuya yazılır. Panoya da kopyalamayı dener.
 *
 * İnternet KULLANMAZ; yalnızca telefona düşen bildirimin metnini okur. Kendi bildirimimizi
 * ve kod içermeyen/kod bağlamı olmayan bildirimleri atlar.
 */
class OtpNotificationListener : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Servis (yeniden) bağlandığında, o an bildirim panelinde DURAN bildirimleri de tarar.
     * Önemli: onNotificationPosted sadece YENİ gelen bildirimde çalışır; uygulama güncellenince
     * dinleyici geç bağlanırsa, daha önce gelmiş OTP bildirimini bu sayede yine de yakalarız.
     */
    override fun onListenerConnected() {
        runCatching { activeNotifications }.getOrNull()?.forEach { handle(it) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = handle(sbn)

    private fun handle(sbn: StatusBarNotification?) {
        val sbnPkg = sbn?.packageName
        if (sbnPkg == packageName) return // kendi bildirimimiz
        val n = sbn?.notification ?: return
        val extras = n.extras ?: return

        val sb = StringBuilder()
        fun add(cs: CharSequence?) { if (!cs.isNullOrBlank()) sb.append(' ').append(cs) }
        add(extras.getCharSequence(Notification.EXTRA_TITLE))
        add(extras.getCharSequence(Notification.EXTRA_TEXT))
        add(extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
        add(extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { add(it) }
        val text = sb.toString()
        if (text.isBlank()) return

        val code = OtpUtil.findInNotification(text) ?: return
        OtpStore(this).set(code)
        copyToClipboard(code)
        handler.post {
            Toast.makeText(this, "OTP $code yakalandı — menüden \"Son OTP\" ile yaz", Toast.LENGTH_LONG).show()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = Unit

    private fun copyToClipboard(value: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = ClipData.newPlainText("OTP", value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        runCatching { clipboard.setPrimaryClip(clip) }
    }
}
