package com.skysoft.features.loot

internal interface RareLootContextContributor {
    fun isActive(): Boolean = false

    fun hasLootShareEvidence(now: Long): Boolean = false

    fun recordDrop(
        drop: RareLootChatDrop,
        lootshare: Boolean,
        now: Long,
    ): RareLootDropCount? = null
}

internal object RareLootContextRegistry {
    private val contributors = mutableListOf<RareLootContextContributor>()

    fun register(contributor: RareLootContextContributor) {
        require(contributor !in contributors) { "Rare loot context contributor is already registered." }
        contributors += contributor
    }

    fun hasLootShareEvidence(now: Long): Boolean =
        contributors.any { contributor -> contributor.isActive() && contributor.hasLootShareEvidence(now) }

    fun hasActiveContributors(): Boolean = contributors.any(RareLootContextContributor::isActive)

    fun recordDrop(drop: RareLootChatDrop, lootshare: Boolean, now: Long): RareLootDropCount? {
        val counts = contributors.filter(RareLootContextContributor::isActive)
            .mapNotNull { contributor -> contributor.recordDrop(drop, lootshare, now) }
        require(counts.size <= 1) { "Multiple rare loot context contributors supplied a drop count." }
        return counts.singleOrNull()
    }
}
