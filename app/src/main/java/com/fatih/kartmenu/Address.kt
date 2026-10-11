package com.fatih.kartmenu

/**
 * Kayıtlı bir teslimat adresi: ad soyad, telefon(lar), adres satırı 1/2/3 ve
 * Şehir/İlçe/Mahalle. Şehir/İlçe/Mahalle yüzen menüde mavi kutu (chip) olarak durur:
 * kullanıcı Amazon'daki kutuya dokunur, ilgili chip'e basar, değer gelir, açılan
 * listeden kendisi seçer (otomatik seçme denenmez). Bir adrese 5'e kadar telefon
 * eklenebilir; doldururken rastgele biri seçilir.
 */
data class Address(
    val id: String,
    val fullName: String,        // Ad soyad (opsiyonel)
    val phones: List<String>,    // 1-5 telefon (opsiyonel); doldururken rastgele biri seçilir
    val line1: String,           // Adres satırı 1 (cadde/sokak)
    val line2: String,           // Adres satırı 2 (bina/no)
    val line3: String,           // Adres satırı 3 (daire/dükkan, opsiyonel)
    val city: String = "",       // Şehir (menüde mavi kutu; kullanıcı listeden seçer)
    val district: String = "",   // İlçe (menüde mavi kutu)
    val mahalle: String = "",    // Mahalle / Köy (menüde mavi kutu)
) {
    val title: String
        get() = listOf(fullName, line1).firstOrNull { it.isNotBlank() } ?: "Adres"

    /** Menüde/listede tek satır özet. */
    val summary: String
        get() = listOf(line1, line2, line3).filter { it.isNotBlank() }
            .joinToString(" · ")
            .ifBlank { "Adres" }

    /** Doldurma için kayıtlı telefonlardan rastgele biri (hiç yoksa boş). */
    fun randomPhone(): String = phones.filter { it.isNotBlank() }.randomOrNull() ?: ""
}
