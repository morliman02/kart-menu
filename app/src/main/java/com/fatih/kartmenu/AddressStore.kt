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
                Address(
                    id = o.getString("id"),
                    fullName = o.optString("fullName"),
                    phone = o.optString("phone"),
                    line = o.optString("line"),
                    district = o.optString("district"),
                    city = o.optString("city"),
                    postalCode = o.optString("postalCode"),
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Adresler çözülemedi", e)
            emptyList()
        }
    }

    fun add(address: Address) = synchronized(LOCK) { save(getAll() + address) }

    fun delete(id: String) = synchronized(LOCK) { save(getAll().filterNot { it.id == id }) }

    private fun save(addresses: List<Address>) {
        val array = JSONArray()
        addresses.forEach { a ->
            array.put(
                JSONObject()
                    .put("id", a.id)
                    .put("fullName", a.fullName)
                    .put("phone", a.phone)
                    .put("line", a.line)
                    .put("district", a.district)
                    .put("city", a.city)
                    .put("postalCode", a.postalCode)
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
