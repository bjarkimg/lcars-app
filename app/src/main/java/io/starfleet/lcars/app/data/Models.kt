package io.starfleet.lcars.app.data

data class Bay(
    val id: String,
    val label: String,
)

val BAYS = listOf(
    Bay("bur", "Búr"),
    Bay("frystir-inni", "Frystir inni"),
    Bay("frystir-uti", "Frystir úti"),
    Bay("eldhus", "Eldhús"),
    Bay("annad", "Annað"),
)

data class PantryItem(
    val id: String,
    val barcode: String?,
    val name: String,
    val brand: String?,
    val qty: Double,
    val unit: String,
    val location: String,
    val locationLabel: String?,
    val source: String?,
    val needsName: Boolean,
    val lastScanAt: String?,
)

data class ScanResult(
    val success: Boolean,
    val created: Boolean,
    val item: PantryItem?,
    val error: String?,
    val source: String?,
)

fun catalogLabel(source: String?): String = when (source) {
    "kronan" -> "KRÓNAN"
    "off" -> "OPEN FOOD FACTS"
    "manual" -> "MANUAL"
    "unknown" -> "NO CATALOG"
    else -> ""
}

fun bayLabel(id: String?): String =
    BAYS.firstOrNull { it.id == id }?.label ?: id.orEmpty()
