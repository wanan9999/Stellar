package roro.stellar.manager.carrier

data class CountryPreset(val code: String, val name: String)

data class CarrierPreset(val countryCode: String, val name: String)

object CarrierPresets {
    val countries = listOf(
        CountryPreset("CN", "中国大陆"),
        CountryPreset("HK", "中国香港"),
        CountryPreset("MO", "中国澳门"),
        CountryPreset("TW", "中国台湾"),
        CountryPreset("JP", "日本"),
        CountryPreset("US", "美国"),
        CountryPreset("SG", "新加坡"),
        CountryPreset("GB", "英国"),
        CountryPreset("KR", "韩国"),
        CountryPreset("DE", "德国"),
        CountryPreset("AU", "澳大利亚"),
        CountryPreset("CA", "加拿大")
    )

    val carriers = listOf(
        CarrierPreset("CN", "中国移动"),
        CarrierPreset("CN", "中国联通"),
        CarrierPreset("CN", "中国电信"),
        CarrierPreset("CN", "中国广电"),
        CarrierPreset("HK", "中国移动香港"),
        CarrierPreset("HK", "CSL"),
        CarrierPreset("HK", "3"),
        CarrierPreset("HK", "SmarTone"),
        CarrierPreset("MO", "CTM"),
        CarrierPreset("MO", "中国电信澳门"),
        CarrierPreset("TW", "中华电信"),
        CarrierPreset("TW", "台湾大哥大"),
        CarrierPreset("TW", "远传电信"),
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
