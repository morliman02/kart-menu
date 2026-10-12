package com.fatih.kartmenu

import android.content.Context
import android.util.Log
import org.json.JSONArray

/**
 * ASIN listesi (Amazon ürün kodları, ör. "B0GNN6JYSC"). En fazla 5 tane tutulur.
 * Menüde amz1, amz2, … amz5 mavi kutuları olarak görünür; kullanıcı istediği
 * kutuya basınca o ASIN, o an seçili alana (genelde Amazon arama kutusu) yazılır.
 * Kart/adres gibi, [Crypto] (AES-256-GCM) ile şifreli saklanır; internete gönderilmez.
 */
class AsinStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Kayıtlı ASIN'ler (en fazla 5), sırasıyla. */
    fun getAll(): List<String> = synchronized(LOCK) {
        val blob = prefs.getString(KEY_DATA, null) ?: return emptyList()
        return try {
            val array = JSONArray(Crypto.decrypt(blob))
            (0 until array.length()).map { array.getString(it).trim() }
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "ASIN'ler çözülemedi", e)
            emptyList()
        }
    }

    /** Listeyi değiştirir (ilk 5'i alır, boşları atar). */
    fun save(asins: List<String>) = synchronized(LOCK) {
        val clean = asins.map { it.trim() }.filter { it.isNotBlank() }.take(MAX)
        prefs.edit().putString(KEY_DATA, Crypto.encrypt(JSONArray(clean).toString())).apply()
    }

    companion object {
        const val MAX = 5
        private const val TAG = "AsinStore"
        private const val PREFS_NAME = "asin_kasasi"
        private const val KEY_DATA = "asinler_sifreli"
        private val LOCK = Any()
    }
}
