package com.fatih.kartmenu

/**
 * Kayıtlı bir teslimat adresi. Yalnızca ad soyad, telefon(lar) ve adres satırı 1/2/3 tutulur.
 * Şehir/İlçe/Mahalle Amazon'da kullanıcı tarafından elle (birbirine bağlı önerilerle) girildiği
 * için saklanmaz. Bir adrese 5'e kadar telefon eklenebilir; doldururken rastgele biri seçilir.
 */
data class Address(
    val id: String,
    val fullName: String,        // Ad soyad (opsiyonel)
    val phones: List<String>,    // 1-5 telefon (opsiyonel); doldururken rastgele biri seçilir
    val line1: String,           // Adres satırı 1 (cadde/sokak)
    val line2: String,           // Adres satırı 2 (bina/no)
    val line3: String,           // Adres satırı 3 (daire/dükkan, opsiyonel)
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
