package com.fatih.kartmenu

import java.util.Calendar

/** Kayıtlı bir kart. CVV bilerek tutulmaz. */
data class Card(
    val id: String,
    val number: String,
    val expiry: String,
    val holder: String,
) {
    val last4: String get() = number.takeLast(4)
    val masked: String get() = "•••• $last4"
}

object CardValidation {

    fun normalizeNumber(input: String): String = input.filter { it.isDigit() }

    /** "1227", "12/27" veya "12/2027" kabul eder; "12/27" biçiminde döndürür, geçersizse null. */
    fun normalizeExpiry(input: String): String? {
        val digits = input.filter { it.isDigit() }
        val (mm, yy) = when (digits.length) {
            4 -> digits.substring(0, 2) to digits.substring(2, 4)
            6 -> digits.substring(0, 2) to digits.substring(4, 6)
            else -> return null
        }
        val month = mm.toInt()
        if (month !in 1..12) return null
        val now = Calendar.getInstance()
        val year = 2000 + yy.toInt()
        val currentYear = now.get(Calendar.YEAR)
        val currentMonth = now.get(Calendar.MONTH) + 1
        if (year < currentYear || (year == currentYear && month < currentMonth)) return null
        return "$mm/$yy"
    }

    fun isValidNumber(number: String): Boolean = number.length in 12..19 && luhn(number)

    private fun luhn(number: String): Boolean {
        var sum = 0
        var double = false
        for (i in number.indices.reversed()) {
            var d = number[i] - '0'
            if (double) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
            double = !double
        }
        return sum % 10 == 0
    }
}
