package roro.stellar.manager.carrier

data class CountryPreset(val code: String, val name: String)

data class CarrierPreset(val countryCode: String, val name: String)

object CarrierPresets {
    val countries = listOf(
        CountryPreset("JP", "日本"),
        CountryPreset("US", "美国"),
        CountryPreset("SG", "新加坡"),
        CountryPreset("GB", "英国"),
        CountryPreset("KR", "韩国"),
        CountryPreset("HK", "中国香港"),
        CountryPreset("TW", "中国台湾"),
        CountryPreset("DE", "德国"),
        CountryPreset("AU", "澳大利亚"),
        CountryPreset("CA", "加拿大")
    )

    val carriers = listOf(
        CarrierPreset("JP", "NTT docomo"),
        CarrierPreset("JP", "au"),
        CarrierPreset("JP", "SoftBank"),
        CarrierPreset("US", "T-Mobile"),
        CarrierPreset("US", "Verizon"),
        CarrierPreset("US", "AT&T"),
        CarrierPreset("SG", "Singtel"),
        CarrierPreset("SG", "StarHub"),
        CarrierPreset("GB", "EE"),
        CarrierPreset("KR", "SK Telecom")
    )

    fun carriersFor(countryCode: String) = carriers.filter { it.countryCode.equals(countryCode, true) }
}
