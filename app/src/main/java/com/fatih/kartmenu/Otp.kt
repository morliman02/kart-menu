package com.fatih.kartmenu

import java.util.Locale

/**
 * OTP (tek kullanımlık doğrulama kodu) bulma yardımcıları. Hem ekran okuyucu
 * (KartAccessibilityService) hem mail bildirimi dinleyici (OtpNotificationListener)
 * aynı mantığı kullanır. İnternet kullanılmaz; sadece verilen metin taranır.
 */
object OtpUtil {

    // 6 haneli kodu bu kelimelerin geçtiği metinlerde öncelikle arar (yanlış sayı kapmasın).
    private val CONTEXT_KEYS = arrayOf(
        "otp", "kod", "code", "dogrula", "verif", "tek kullanimlik", "one-time", "one time",
        "sifre", "parola", "password", "pin", "amazon"
    )
    private val SIX = Regex("(?<!\\d)\\d{6}(?!\\d)")
    private val SIX_SPLIT = Regex("(?<!\\d)(\\d{3})[ \\-](\\d{3})(?!\\d)")

    /** Türkçe'ye uyumlu küçük harfe indirip aksanları sadeleştirir. */
    fun normalizeTr(s: String): String {
        val lower = s.lowercase(Locale("tr", "TR"))
        val sb = StringBuilder(lower.length)
        for (c in lower) sb.append(
            when (c) {
                'ı', 'i', 'İ' -> 'i'
                'ş' -> 's'; 'ğ' -> 'g'; 'ü' -> 'u'; 'ö' -> 'o'; 'ç' -> 'c'
                else -> c
            }
        )
        return sb.toString()
    }

    fun hasContext(text: String): Boolean {
        val n = normalizeTr(text)
        return CONTEXT_KEYS.any { n.contains(it) }
    }

    /** Metindeki ilk 6 haneli kodu (düz veya "798 562"/"798-562" ayraçlı) döndürür. */
    fun codeIn(text: String): String? {
        SIX.find(text)?.let { return it.value }
        SIX_SPLIT.find(text)?.let { return it.groupValues[1] + it.groupValues[2] }
        return null
    }

    /**
     * Birden çok metin parçasında kodu arar: önce bağlam kelimesi geçen parçada, sonra
     * tek başına 6 haneli duran parçada, en son herhangi bir 6 haneli.
     */
    fun findInTexts(texts: List<String>): String? {
        for (t in texts) if (hasContext(t)) codeIn(t)?.let { return it }
        for (t in texts) {
            val tr = t.trim()
            if (tr.length == 6 && tr.all { it.isDigit() }) return tr
        }
        for (t in texts) codeIn(t)?.let { return it }
        return null
    }

    /** Tek bir metinde (ör. bildirim başlık+içeriği): bağlam varsa kodu döndürür. */
    fun findInNotification(text: String): String? {
        if (!hasContext(text)) return null
        return codeIn(text)
    }
}

/**
 * Mail bildiriminden yakalanan son OTP kodunu (ve ne zaman geldiğini) telefonda saklar.
 * DİSKE yazılır (şifreli, [Crypto]) çünkü bildirimi yakalayan servis ile menüyü gösteren
 * servis ayrı anlarda/süreçlerde çalışabilir; bellekte tutmak süreç yeniden başlayınca
 * kayboluyordu. Yüzen menüdeki "Son OTP" satırı buradan okur; süresi geçince boş döner.
 */
class OtpStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun set(code: String) {
        prefs.edit()
            .putString(KEY_CODE, Crypto.encrypt(code))
            .putLong(KEY_AT, System.currentTimeMillis())
            .apply()
    }

    /** Son kodu döndürür (varsayılan 5 dakikadan eskiyse boş). */
    fun get(maxAgeMs: Long = 5 * 60_000L): String? {
        val enc = prefs.getString(KEY_CODE, null) ?: return null
        val at = prefs.getLong(KEY_AT, 0L)
        if (System.currentTimeMillis() - at > maxAgeMs) return null
        return runCatching { Crypto.decrypt(enc) }.getOrNull()
    }

    companion object {
        private const val PREFS_NAME = "otp_kasasi"
        private const val KEY_CODE = "otp_sifreli"
        private const val KEY_AT = "otp_zaman"
    }
}
