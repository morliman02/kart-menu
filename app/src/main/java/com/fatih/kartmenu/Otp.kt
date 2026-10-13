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
 * Mail bildiriminden yakalanan son OTP kodunu (ve ne zaman geldiğini) bellekte tutar.
 * Yüzen menüdeki "Son OTP" satırı buradan okur. Diske yazılmaz; süresi geçince boş döner.
 */
object OtpHolder {
    @Volatile private var code: String? = null
    @Volatile private var atMs: Long = 0L

    fun set(c: String) {
        code = c
        atMs = System.currentTimeMillis()
    }

    /** Son kodu döndürür (varsayılan 5 dakikadan eskiyse boş). */
    fun get(maxAgeMs: Long = 5 * 60_000L): String? {
        val c = code ?: return null
        return if (System.currentTimeMillis() - atMs <= maxAgeMs) c else null
    }
}
