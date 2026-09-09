package com.skysoft.data

import com.mojang.authlib.GameProfile
import com.skysoft.SkysoftMod
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.boundedAccessOrderMap
import com.skysoft.utils.net.KeyedAsyncRequestSlots
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.PlayerSkin

object MinecraftProfileLookup {
    private val lock = Any()
    private val profilesByName = boundedAccessOrderMap<String, CachedProfile>(MAXIMUM_PROFILE_COUNT)
    private val profilesById = boundedAccessOrderMap<UUID, CachedProfile>(MAXIMUM_PROFILE_COUNT)
    private val nameRequests = KeyedAsyncRequestSlots<String, GameProfile?>()
    private val idRequests = KeyedAsyncRequestSlots<UUID, GameProfile?>()
    private var generation = 0L
    private val skins = boundedAccessOrderMap<UUID, Supplier<PlayerSkin>>(MAXIMUM_SKIN_COUNT)

    fun register() {
        SkysoftClientEvents.onDisconnect("Minecraft profile lookup reset", ::clear)
        SkysoftClientEvents.onClientStopping("Minecraft profile lookup shutdown") { clear() }
    }

    fun byName(name: String): CompletableFuture<GameProfile?> =
        lookup(name.lowercase(Locale.ROOT), profilesByName, nameRequests, "name $name") {
            Minecraft.getInstance().services().profileResolver().fetchByName(name).orElse(null)
        }

    fun byId(uuid: UUID): CompletableFuture<GameProfile?> =
        lookup(uuid, profilesById, idRequests, "UUID $uuid") {
            Minecraft.getInstance().services().profileResolver().fetchById(uuid).orElse(null)
        }

    private fun <K> lookup(
        key: K,
        profiles: MutableMap<K, CachedProfile>,
        requests: KeyedAsyncRequestSlots<K, GameProfile?>,
        description: String,
        fetch: () -> GameProfile?,
    ): CompletableFuture<GameProfile?> = synchronized(lock) {
        profiles[key]?.let { cached ->
            if (cached.expiresAtMillis >= System.currentTimeMillis()) {
                return CompletableFuture.completedFuture(cached.profile)
            }
            profiles.remove(key)
        }
        val requestGeneration = generation
        requests.getOrStart(
            key,
            requestFactory = {
                CompletableFuture.supplyAsync(fetch).handle { profile, failure ->
                    if (failure != null) {
                        SkysoftMod.LOGGER.warn("Failed to resolve Minecraft profile by $description", failure)
                        null
                    } else {
                        profile
                    }
                }
            },
            completion = { profile, _ ->
                synchronized(lock) {
                    if (generation == requestGeneration) cache(profile, profiles, key)
                }
            },
        )
    }

    fun skin(profile: GameProfile): PlayerSkin = synchronized(lock) {
        skins.getOrPut(profile.id) {
            Minecraft.getInstance().skinManager.createLookup(profile, false)
        }
    }.get()

    private fun <K> cache(profile: GameProfile?, profiles: MutableMap<K, CachedProfile>, key: K) {
        val expiresAtMillis = if (profile == null) {
            System.currentTimeMillis() + NEGATIVE_CACHE_MILLIS
        } else {
            Long.MAX_VALUE
        }
        val cached = CachedProfile(profile, expiresAtMillis)
        profiles[key] = cached
        profile?.let {
            profilesByName[it.name.lowercase(Locale.ROOT)] = cached
            profilesById[it.id] = cached
        }
    }

    private fun clear() = synchronized(lock) {
        generation++
        nameRequests.cancelAll()
        idRequests.cancelAll()
        profilesByName.clear()
        profilesById.clear()
        skins.clear()
    }

    private data class CachedProfile(
        val profile: GameProfile?,
        val expiresAtMillis: Long,
    )

    private const val MAXIMUM_PROFILE_COUNT = 256
    private const val MAXIMUM_SKIN_COUNT = 128
    private const val NEGATIVE_CACHE_MILLIS = 60_000L
}
