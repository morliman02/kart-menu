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
     * Sıradaki e-postayı döndürür ve sırayı bir ilerletir (sona gelince başa döner).
     * E-posta yoksa boş döner.
     */
    fun nextEmail(): String = synchronized(LOCK) {
        val emails = getData().emails.filter { it.isNotBlank() }
        if (emails.isEmpty()) return ""
        val idx = ((prefs.getInt(KEY_EMAIL_INDEX, 0) % emails.size) + emails.size) % emails.size
        prefs.edit().putInt(KEY_EMAIL_INDEX, (idx + 1) % emails.size).apply()
        return emails[idx]
    }

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
