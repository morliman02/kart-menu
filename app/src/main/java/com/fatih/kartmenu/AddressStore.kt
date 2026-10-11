package com.fatih.kartmenu

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Adresleri telefonda şifreli saklar ([Crypto], AES-256-GCM, Android Keystore anahtarı).
 * Diskte düz metin adres bilgisi olmaz.
 */
class AddressStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(): List<Address> = synchronized(LOCK) {
        val blob = prefs.getString(KEY_DATA, null) ?: return emptyList()
        return try {
            val array = JSONArray(Crypto.decrypt(blob))
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                // Yeni biçim: "phones" dizisi. Eski biçim: tek "phone" metni.
                val phones = o.optJSONArray("phones")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: listOf(o.optString("phone"))
                Address(
                    id = o.getString("id"),
                    fullName = o.optString("fullName"),
                    phones = phones.map { it.trim() }.filter { it.isNotBlank() },
                    line1 = o.optString("line1"),
                    line2 = o.optString("line2"),
                    line3 = o.optString("line3"),
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Adresler çözülemedi", e)
            emptyList()
        }
    }

    fun add(address: Address) = synchronized(LOCK) { save(getAll() + address) }

    /** Aynı id'li adresi yenisiyle değiştirir (yerinde günceller). */
    fun update(address: Address) = synchronized(LOCK) {
        save(getAll().map { if (it.id == address.id) address else it })
    }

    fun delete(id: String) = synchronized(LOCK) { save(getAll().filterNot { it.id == id }) }

    private fun save(addresses: List<Address>) {
        val array = JSONArray()
        addresses.forEach { a ->
            array.put(
                JSONObject()
                    .put("id", a.id)
                    .put("fullName", a.fullName)
                    .put("phones", JSONArray(a.phones))
                    .put("line1", a.line1)
                    .put("line2", a.line2)
                    .put("line3", a.line3)
            )
        }
        prefs.edit().putString(KEY_DATA, Crypto.encrypt(array.toString())).apply()
    }

    companion object {
        private const val TAG = "AddressStore"
        private const val PREFS_NAME = "adres_kasasi"
        private const val KEY_DATA = "adresler_sifreli"
        private val LOCK = Any()
    }
}
