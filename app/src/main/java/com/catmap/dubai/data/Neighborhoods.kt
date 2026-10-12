package com.catmap.dubai.data

import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

data class Neighborhood(val key: String, val nameEn: String, val nameAr: String, val lat: Double, val lng: Double) {
    fun displayName(locale: Locale = Locale.getDefault()) = if (locale.language == "ar") nameAr else nameEn
}

object Dubai {
    const val CENTER_LAT = 25.2048
    const val CENTER_LNG = 55.2708
    const val NORTH = 25.40
    const val SOUTH = 24.75
    const val EAST = 55.65
    const val WEST = 54.90

    fun contains(lat: Double, lng: Double) = lat in SOUTH..NORTH && lng in WEST..EAST

    val neighborhoods = listOf(
        Neighborhood("deira", "Deira", "ديرة", 25.2711, 55.3075),
        Neighborhood("al_ras", "Al Ras", "الراس", 25.2697, 55.2953),
        Neighborhood("naif", "Naif", "نايف", 25.2722, 55.3125),
        Neighborhood("al_rigga", "Al Rigga", "الرقة", 25.2650, 55.3220),
        Neighborhood("al_muraqqabat", "Al Muraqqabat", "المرقبات", 25.2620, 55.3290),
        Neighborhood("hor_al_anz", "Hor Al Anz", "هور العنز", 25.2770, 55.3420),
        Neighborhood("al_mamzar", "Al Mamzar", "الممزر", 25.2950, 55.3450),
        Neighborhood("al_qusais", "Al Qusais", "القصيص", 25.2830, 55.3870),
        Neighborhood("al_nahda", "Al Nahda", "النهدة", 25.2900, 55.3700),
        Neighborhood("al_twar", "Al Twar", "الطوار", 25.2600, 55.3800),
        Neighborhood("muhaisnah", "Muhaisnah", "محيصنة", 25.2620, 55.4200),
        Neighborhood("mirdif", "Mirdif", "مردف", 25.2180, 55.4210),
        Neighborhood("al_mizhar", "Al Mizhar", "المزهر", 25.2400, 55.4500),
        Neighborhood("al_khawaneej", "Al Khawaneej", "الخوانيج", 25.2350, 55.4800),
        Neighborhood("al_rashidiya", "Al Rashidiya", "الراشدية", 25.2280, 55.3900),
        Neighborhood("al_garhoud", "Al Garhoud", "القرهود", 25.2400, 55.3500),
        Neighborhood("festival_city", "Dubai Festival City", "دبي فستيفال سيتي", 25.2220, 55.3530),
        Neighborhood("al_jaddaf", "Al Jaddaf", "الجداف", 25.2200, 55.3330),
        Neighborhood("oud_metha", "Oud Metha", "عود ميثاء", 25.2360, 55.3150),
        Neighborhood("al_karama", "Al Karama", "الكرامة", 25.2420, 55.3040),
        Neighborhood("bur_dubai", "Bur Dubai", "بر دبي", 25.2580, 55.2970),
        Neighborhood("al_fahidi", "Al Fahidi", "الفهيدي", 25.2635, 55.2990),
        Neighborhood("al_mankhool", "Al Mankhool", "المنخول", 25.2500, 55.2930),
        Neighborhood("al_satwa", "Al Satwa", "السطوة", 25.2260, 55.2770),
        Neighborhood("jumeirah_1", "Jumeirah 1", "جميرا 1", 25.2200, 55.2580),
        Neighborhood("jumeirah_2", "Jumeirah 2", "جميرا 2", 25.2050, 55.2420),
        Neighborhood("jumeirah_3", "Jumeirah 3", "جميرا 3", 25.1900, 55.2300),
        Neighborhood("al_wasl", "Al Wasl", "الوصل", 25.1950, 55.2550),
        Neighborhood("al_safa", "Al Safa", "الصفا", 25.1800, 55.2350),
        Neighborhood("umm_suqeim", "Umm Suqeim", "أم سقيم", 25.1550, 55.2100),
        Neighborhood("difc", "DIFC", "مركز دبي المالي العالمي", 25.2120, 55.2800),
        Neighborhood("downtown", "Downtown Dubai", "وسط مدينة دبي", 25.1972, 55.2744),
        Neighborhood("business_bay", "Business Bay", "الخليج التجاري", 25.1860, 55.2650),
        Neighborhood("ras_al_khor", "Ras Al Khor", "رأس الخور", 25.1900, 55.3500),
        Neighborhood("nad_al_sheba", "Nad Al Sheba", "ند الشبا", 25.1700, 55.3200),
        Neighborhood("al_quoz", "Al Quoz", "القوز", 25.1500, 55.2400),
        Neighborhood("dubai_hills", "Dubai Hills Estate", "دبي هيلز استيت", 25.1100, 55.2500),
        Neighborhood("al_barsha", "Al Barsha", "البرشاء", 25.1100, 55.2000),
        Neighborhood("barsha_heights", "Barsha Heights", "مرتفعات البرشاء", 25.0960, 55.1750),
        Neighborhood("al_sufouh", "Al Sufouh", "الصفوح", 25.1100, 55.1650),
        Neighborhood("palm_jumeirah", "Palm Jumeirah", "نخلة جميرا", 25.1124, 55.1390),
        Neighborhood("dubai_marina", "Dubai Marina", "دبي مارينا", 25.0805, 55.1403),
        Neighborhood("jbr", "JBR", "جميرا بيتش ريزيدنس", 25.0780, 55.1330),
        Neighborhood("jlt", "Jumeirah Lakes Towers", "أبراج بحيرات جميرا", 25.0690, 55.1430),
        Neighborhood("discovery_gardens", "Discovery Gardens", "ديسكفري جاردنز", 25.0400, 55.1370),
        Neighborhood("al_furjan", "Al Furjan", "الفرجان", 25.0270, 55.1460),
        Neighborhood("jebel_ali", "Jebel Ali", "جبل علي", 25.0050, 55.0950),
        Neighborhood("jvc", "Jumeirah Village Circle", "قرية جميرا الدائرية", 25.0590, 55.2090),
        Neighborhood("sports_city", "Dubai Sports City", "مدينة دبي الرياضية", 25.0400, 55.2200),
        Neighborhood("motor_city", "Motor City", "موتور سيتي", 25.0480, 55.2400),
        Neighborhood("arabian_ranches", "Arabian Ranches", "المرابع العربية", 25.0550, 55.2700),
        Neighborhood("silicon_oasis", "Dubai Silicon Oasis", "واحة دبي للسيليكون", 25.1200, 55.3800),
        Neighborhood("international_city", "International City", "المدينة العالمية", 25.1650, 55.4100),
        Neighborhood("al_warqa", "Al Warqa", "الورقاء", 25.1950, 55.4300),
        Neighborhood("dip", "Dubai Investments Park", "مجمع دبي للاستثمار", 24.9900, 55.1700),
        Neighborhood("dubai_south", "Dubai South", "دبي الجنوب", 24.8900, 55.1600),
    )

    private val other = Neighborhood("other", "Elsewhere in Dubai", "مناطق أخرى في دبي", CENTER_LAT, CENTER_LNG)

    /** Old trading districts around the Creek, for the "Souq Resident" badge. */
    val souqAreas = setOf("deira", "al_ras", "naif", "bur_dubai", "al_fahidi")
    val marinaAreas = setOf("dubai_marina", "jbr", "jlt", "palm_jumeirah")

    fun byKey(key: String?): Neighborhood = neighborhoods.firstOrNull { it.key == key } ?: other

    fun nameOf(key: String?): String = if (key.isNullOrBlank()) "" else byKey(key).displayName()

    fun nearest(lat: Double, lng: Double): Neighborhood {
        val best = neighborhoods.minBy { distanceKm(lat, lng, it.lat, it.lng) }
        return if (distanceKm(lat, lng, best.lat, best.lng) <= 6.0) best else other
    }

    fun search(query: String): List<Neighborhood> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return neighborhoods
        return neighborhoods.filter { it.nameEn.lowercase().contains(q) || it.nameAr.contains(q) || it.key.contains(q) }
    }
}

fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return 2 * r * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

/**
 * Turns an exact position into the approximate one that is stored publicly. The point is snapped
 * to a grid cell and then nudged by an offset derived from [seed], so repeated sightings of the
 * same place don't line up into a revealing pattern and the exact spot can't be recovered.
 */
object LocationPrivacy {
    const val STANDARD = "standard"
    const val HIGH = "high"

    fun cellSize(level: String) = if (level == HIGH) 0.0045 else 0.002

    fun fuzz(lat: Double, lng: Double, level: String, seed: String): Pair<Double, Double> {
        val cell = cellSize(level)
        val baseLat = floor(lat / cell) * cell + cell / 2
        val baseLng = floor(lng / cell) * cell + cell / 2
        val h = seed.hashCode()
        val jitterLat = ((h and 0xFF) / 255.0 - 0.5) * cell * 0.6
        val jitterLng = (((h shr 8) and 0xFF) / 255.0 - 0.5) * cell * 0.6
        return round5(baseLat + jitterLat) to round5(baseLng + jitterLng)
    }

    /** Rescue reports keep a coarse (~10 m) position so volunteers can still find the cat. */
    fun approximate(value: Double) = round(value * 10_000) / 10_000

    private fun round5(v: Double) = round(v * 100_000) / 100_000

    fun approxRadiusMeters(level: String) = (cellSize(level) * 111_000 * 0.8).let { abs(it).toInt() }
}
