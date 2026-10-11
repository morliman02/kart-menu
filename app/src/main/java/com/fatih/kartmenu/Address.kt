package com.fatih.kartmenu

/**
 * Kayıtlı bir teslimat adresi. Alanlar Amazon.com.tr adres formuna göre seçildi.
 */
data class Address(
    val id: String,
    val fullName: String,   // Ad soyad (alıcı)
    val phone: String,      // Telefon numarası
    val line: String,       // Adres (mahalle, cadde/sokak, bina, daire)
    val district: String,   // İlçe
    val city: String,       // Şehir / il
    val postalCode: String, // Posta kodu
) {
    /** Menüde tek satır özet. */
    val summary: String
        get() = buildString {
            append(fullName)
            val place = listOf(district, city).filter { it.isNotBlank() }.joinToString(" / ")
            if (place.isNotEmpty()) append(" · ").append(place)
        }
}
