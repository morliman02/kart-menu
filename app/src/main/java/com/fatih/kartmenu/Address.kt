package com.fatih.kartmenu

/**
 * Kayıtlı bir teslimat adresi. Alanlar Amazon.com.tr adres formuna göre:
 * Adres satırı 1/2/3, Mahalle/Köy, İlçe, Şehir. Posta kodu Amazon'da otomatik
 * dolduğu için zorunlu değil (girilirse başka formlarda kullanılır).
 * Ad soyad ve telefon, Amazon'un adresten önceki adımında gerekebildiği için tutulur.
 */
data class Address(
    val id: String,
    val fullName: String,   // Ad soyad (opsiyonel)
    val phone: String,      // Telefon (opsiyonel)
    val line1: String,      // Adres satırı 1 (cadde/sokak)
    val line2: String,      // Adres satırı 2 (bina/no)
    val line3: String,      // Adres satırı 3 (daire/dükkan, opsiyonel)
    val mahalle: String,    // Mahalle / Köy
    val district: String,   // İlçe
    val city: String,       // Şehir
    val postalCode: String, // Posta kodu (opsiyonel; Amazon otomatik doldurur)
) {
    val title: String
        get() = listOf(fullName, line1).firstOrNull { it.isNotBlank() } ?: "Adres"

    /** Menüde/listede tek satır özet. */
    val summary: String
        get() {
            val place = listOf(mahalle, district, city).filter { it.isNotBlank() }.joinToString(" / ")
            return listOf(line1, place).filter { it.isNotBlank() }.joinToString(" · ")
                .ifBlank { "Adres" }
        }
}
