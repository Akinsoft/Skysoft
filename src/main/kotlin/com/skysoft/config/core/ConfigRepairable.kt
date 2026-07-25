package com.skysoft.config.core

interface ConfigRepairable {
    fun repairLoadedValues()
}

fun repairLoadedConfigs(vararg configs: ConfigRepairable) {
    configs.forEach(ConfigRepairable::repairLoadedValues)
}
