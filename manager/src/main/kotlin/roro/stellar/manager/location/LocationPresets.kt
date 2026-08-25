package roro.stellar.manager.location

import roro.stellar.manager.R

data class CityPreset(
    val nameRes: Int,
    val lat: Double,
    val lng: Double,
    val zoom: Double = DEFAULT_ZOOM
)

object LocationPresets {
    val cities = listOf(
        CityPreset(R.string.location_city_beijing, 39.9042, 116.4074),
        CityPreset(R.string.location_city_shanghai, 31.2304, 121.4737),
        CityPreset(R.string.location_city_hongkong, 22.3193, 114.1694),
        CityPreset(R.string.location_city_taipei, 25.0330, 121.5654),
        CityPreset(R.string.location_city_tokyo, 35.6762, 139.6503),
        CityPreset(R.string.location_city_newyork, 40.7128, -74.0060)
    )
}
