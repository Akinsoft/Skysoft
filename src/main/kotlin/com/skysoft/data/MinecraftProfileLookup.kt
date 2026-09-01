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

    fun byName(name: String): CompletableFuture<GameProfile?> {
        val key = name.lowercase(Locale.ROOT)
        synchronized(lock) {
            profilesByName[key]?.let { cached ->
                if (cached.expiresAtMillis >= System.currentTimeMillis()) {
                    return CompletableFuture.completedFuture(cached.profile)
                }
                profilesByName.remove(key)
            }
            val requestGeneration = generation
            return nameRequests.getOrStart(
                key,
                requestFactory = {
                    CompletableFuture.supplyAsync {
                        Minecraft.getInstance().services().profileResolver().fetchByName(name).orElse(null)
                    }.handle { profile, failure ->
                        if (failure != null) {
                            SkysoftMod.LOGGER.warn("Failed to resolve Minecraft profile by name $name", failure)
                            null
                        } else {
                            profile
                        }
                    }
                },
                completion = { profile, _ ->
                    synchronized(lock) {
                        if (generation == requestGeneration) cache(profile, nameKey = key)
                    }
                },
            )
        }
    }

    fun byId(uuid: UUID): CompletableFuture<GameProfile?> {
        synchronized(lock) {
            profilesById[uuid]?.let { cached ->
                if (cached.expiresAtMillis >= System.currentTimeMillis()) {
                    return CompletableFuture.completedFuture(cached.profile)
                }
                profilesById.remove(uuid)
            }
            val requestGeneration = generation
            return idRequests.getOrStart(
                uuid,
                requestFactory = {
                    CompletableFuture.supplyAsync {
                        Minecraft.getInstance().services().profileResolver().fetchById(uuid).orElse(null)
                    }.handle { profile, failure ->
                        if (failure != null) {
                            SkysoftMod.LOGGER.warn("Failed to resolve Minecraft profile by UUID $uuid", failure)
                            null
                        } else {
                            profile
                        }
                    }
                },
                completion = { profile, _ ->
                    synchronized(lock) {
                        if (generation == requestGeneration) cache(profile, uuid = uuid)
                    }
                },
            )
        }
    }

    fun skin(profile: GameProfile): PlayerSkin = synchronized(lock) {
        skins.getOrPut(profile.id) {
            Minecraft.getInstance().skinManager.createLookup(profile, false)
        }
    }.get()

    private fun cache(profile: GameProfile?, nameKey: String? = null, uuid: UUID? = null) {
        val expiresAtMillis = if (profile == null) {
            System.currentTimeMillis() + NEGATIVE_CACHE_MILLIS
        } else {
            Long.MAX_VALUE
        }
        val cached = CachedProfile(profile, expiresAtMillis)
        nameKey?.let { profilesByName[it] = cached }
        uuid?.let { profilesById[it] = cached }
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
