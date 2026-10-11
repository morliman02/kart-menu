package com.fatih.kartmenu

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Kartları telefonda şifreli saklar.
 *
 * Kart listesi [Crypto] ile (AES-256-GCM, Android Keystore anahtarı) şifrelenip
 * uygulamaya özel SharedPreferences dosyasına yazılır; diskte düz metin kart bilgisi olmaz.
 */
class CardStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(): List<Card> = synchronized(LOCK) {
        val blob = prefs.getString(KEY_DATA, null) ?: return emptyList()
        return try {
            val array = JSONArray(Crypto.decrypt(blob))
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Card(
                    id = o.getString("id"),
                    number = o.getString("number"),
                    expiry = o.getString("expiry"),
                    holder = o.getString("holder"),
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kartlar çözülemedi", e)
            emptyList()
        }
    }

    fun add(card: Card) = synchronized(LOCK) { save(getAll() + card) }

    /** Aynı id'li kartı yenisiyle değiştirir (yerinde günceller). */
    fun update(card: Card) = synchronized(LOCK) {
        save(getAll().map { if (it.id == card.id) card else it })
    }

    fun delete(id: String) = synchronized(LOCK) { save(getAll().filterNot { it.id == id }) }

    private fun save(cards: List<Card>) {
        val array = JSONArray()
        cards.forEach { c ->
            array.put(
                JSONObject()
                    .put("id", c.id)
                    .put("number", c.number)
                    .put("expiry", c.expiry)
                    .put("holder", c.holder)
            )
        }
        prefs.edit().putString(KEY_DATA, Crypto.encrypt(array.toString())).apply()
    }

    companion object {
        private const val TAG = "CardStore"
        private const val PREFS_NAME = "kart_kasasi"
        private const val KEY_DATA = "kartlar_sifreli"
        private val LOCK = Any()
    }
}
