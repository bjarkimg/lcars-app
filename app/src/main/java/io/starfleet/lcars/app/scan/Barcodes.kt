package io.starfleet.lcars.app.scan

/** Retail barcodes only: EAN-8, UPC-A (12), EAN-13. Rejects ML Kit near-misses. */
fun normalizeRetailBarcode(raw: String?): String? {
    val digits = raw?.filter { it.isDigit() }.orEmpty()
    return when (digits.length) {
        8 -> digits.takeIf { validChecksum(digits) }
        12 -> digits.takeIf { validChecksum("0$digits") }
        13 -> digits.takeIf { validChecksum(digits) }
        else -> null
    }
}

internal fun validChecksum(digits: String): Boolean {
    if (digits.length != 8 && digits.length != 13) return false
    val nums = digits.map { it - '0' }
    val body = nums.dropLast(1)
    // EAN-13: left digit weight 1. EAN-8: left digit weight 3.
    val leftWeight3 = digits.length == 8
    val sum = body.mapIndexed { i, d ->
        val weight3 = if (leftWeight3) i % 2 == 0 else i % 2 == 1
        if (weight3) d * 3 else d
    }.sum()
    val check = (10 - (sum % 10)) % 10
    return check == nums.last()
}
