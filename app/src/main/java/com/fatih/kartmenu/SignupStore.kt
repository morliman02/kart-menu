package com.fatih.kartmenu

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Üyelik (hesap oluşturma) bilgileri: e-posta listesi, ad soyad listesi ve sabit parola.
 * Amazon gibi sitelerde hesap açarken kullanılır:
 *  - E-posta: her kullanımda SIRAYLA bir sonraki (sona gelince başa döner).
 *  - Ad soyad: her kullanımda RASTGELE biri.
 *  - Parola: her zaman aynısı.
 * Kart/adres gibi, bu bilgiler de [Crypto] (AES-256-GCM) ile şifreli saklanır; internete gönderilmez.
 */
data class SignupData(
    val emails: List<String>,
    val names: List<String>,
    val password: String,
) {
    fun isEmpty(): Boolean = emails.isEmpty() && names.isEmpty() && password.isBlank()

    /** Doldurma için rastgele bir ad soyad (yoksa boş). */
    fun randomName(): String = names.filter { it.isNotBlank() }.randomOrNull() ?: ""
}

class SignupStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getData(): SignupData = synchronized(LOCK) {
        val blob = prefs.getString(KEY_DATA, null) ?: return SignupData(emptyList(), emptyList(), "")
        return try {
            val o = JSONObject(Crypto.decrypt(blob))
            SignupData(
                emails = o.optJSONArray("emails").toList(),
                names = o.optJSONArray("names").toList(),
                password = o.optString("password"),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Üyelik bilgileri çözülemedi", e)
            SignupData(emptyList(), emptyList(), "")
        }
    }

    fun save(data: SignupData) = synchronized(LOCK) {
        val o = JSONObject()
            .put("emails", JSONArray(data.emails))
            .put("names", JSONArray(data.names))
            .put("password", data.password)
        prefs.edit()
            .putString(KEY_DATA, Crypto.encrypt(o.toString()))
            .putInt(KEY_EMAIL_INDEX, 0) // liste değişince sıra baştan başlasın
            .apply()
    }

    /**
     * ŞU ANKİ e-postayı döndürür ama sırayı İLERLETMEZ. Menüde "aynısını yapıştır"
     * bunu kullanır: yapıştırma başarısız olup tekrar denense bile mail kaçmaz.
     * E-posta yoksa boş döner.
     */
    fun currentEmail(): String = synchronized(LOCK) {
        val emails = getData().emails.filter { it.isNotBlank() }
        if (emails.isEmpty()) return ""
        return emails[normIndex(emails.size)]
    }

    /**
     * Sırayı bir sonrakine ilerletir (sona gelince başa döner) ve YENİ şu anki e-postayı
     * döndürür. Menüde "sonraki e-postaya geç" bunu kullanır. E-posta yoksa boş döner.
     */
    fun advanceEmail(): String = synchronized(LOCK) {
        val emails = getData().emails.filter { it.isNotBlank() }
        if (emails.isEmpty()) return ""
        val next = (normIndex(emails.size) + 1) % emails.size
        prefs.edit().putInt(KEY_EMAIL_INDEX, next).apply()
        return emails[next]
    }

    /** Bir SONRAKİ e-postayı İLERLETMEDEN döndürür (menüde "sonraki" satırında göstermek için). */
    fun peekNextEmail(): String = synchronized(LOCK) {
        val emails = getData().emails.filter { it.isNotBlank() }
        if (emails.isEmpty()) return ""
        return emails[(normIndex(emails.size) + 1) % emails.size]
    }

    /** Şu anki e-postanın 1 tabanlı sırası ve toplam adet (ör. 3/8). Boşsa 0/0. */
    fun emailPosition(): Pair<Int, Int> = synchronized(LOCK) {
        val size = getData().emails.filter { it.isNotBlank() }.size
        if (size == 0) 0 to 0 else (normIndex(size) + 1) to size
    }

    /** Kayıtlı sıra numarasını liste boyutuna göre güvenli aralığa indirger. */
    private fun normIndex(size: Int): Int =
        ((prefs.getInt(KEY_EMAIL_INDEX, 0) % size) + size) % size

    private fun JSONArray?.toList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).map { getString(it).trim() }.filter { it.isNotBlank() }
    }

    companion object {
        private const val TAG = "SignupStore"
        private const val PREFS_NAME = "uyelik_kasasi"
        private const val KEY_DATA = "uyelik_sifreli"
        private const val KEY_EMAIL_INDEX = "email_sira"
        private val LOCK = Any()
    }
}
