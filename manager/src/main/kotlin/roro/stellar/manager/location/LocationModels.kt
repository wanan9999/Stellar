package roro.stellar.manager.location

data class SavedPlace(
    val name: String,
    val lat: Double,
    val lng: Double
)

data class StoredLocation(
    val lat: Double,
    val lng: Double,
    val label: String,
    val zoom: Double = DEFAULT_ZOOM,
    val running: Boolean = false
)

data class SearchHit(
    val name: String,
    val lat: Double,
    val lng: Double
)

data class LocationSnapshot(
    val active: Boolean = false,
    val lat: Double = DEFAULT_LAT,
    val lng: Double = DEFAULT_LNG,
    val label: String = "",
    val zoom: Double = DEFAULT_ZOOM,
    val mockAppReady: Boolean = false,
    val reduceJump: Boolean = false,
    val error: String = ""
)

const val DEFAULT_LAT = 39.9042
const val DEFAULT_LNG = 116.4074
const val DEFAULT_ZOOM = 12.0
