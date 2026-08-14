package com.skysoft.features.combat

import com.skysoft.utils.EntityUtilities.cleanName
import com.skysoft.utils.NumberUtilities.parseCompactNumberOrNull
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.WorldVec
import com.skysoft.utils.toWorldVec
import net.minecraft.network.chat.Component
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import java.util.Optional

internal object DamageSplashText {
    private val damagePattern = Regex("""^[✧✯]?(?<damage>[0-9,.]+[KMBkmb]?)[⚔+✧❤♞☄✷ﬗ✯]*$""")

    fun parseDamage(name: String): Long? =
        damagePattern.matchEntire(name.replace(",", ""))
            ?.groups
            ?.get("damage")
            ?.value
            ?.parseCompactNumberOrNull()
            ?.value
            ?.toLong()

    fun fromEntity(entity: Entity): DamageSplash? {
        val armorStand = entity as? ArmorStand ?: return null
        val cleanName = armorStand.cleanName()
        return fromName(armorStand.id, cleanName, armorStand.position().toWorldVec())
    }

    fun fromMetadata(
        entityId: Int,
        location: WorldVec,
        packedItems: List<SynchedEntityData.DataValue<*>>,
    ): List<DamageSplash> =
        packedItems
            .mapNotNull { value -> value.componentText() }
            .mapNotNull { name -> fromName(entityId, name, location) }

    private fun fromName(entityId: Int, cleanName: String, location: WorldVec): DamageSplash? {
        val damage = parseDamage(cleanName) ?: return null
        return DamageSplash(
            entityId = entityId,
            damage = damage,
            location = location,
            cleanName = cleanName,
        )
    }

    private fun SynchedEntityData.DataValue<*>.componentText(): String? {
        val value = value()
        val component = when (value) {
            is Component -> value
            is Optional<*> -> if (value.isPresent) value.get() as? Component else null
            else -> null
        } ?: return null
        return component.cleanSkyBlockText()
    }
}
