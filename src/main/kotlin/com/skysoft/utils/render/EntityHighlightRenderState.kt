package com.skysoft.utils.render

interface EntityHighlightRenderState {
    fun skysoftGetEntityFillColor(): Int

    fun skysoftSetEntityFillColor(color: Int)

    fun skysoftHasEquipmentOnlyOutline(): Boolean

    fun skysoftSetEquipmentOnlyOutline(equipmentOnly: Boolean)
}
