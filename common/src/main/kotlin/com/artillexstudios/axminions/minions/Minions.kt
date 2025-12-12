package com.artillexstudios.axminions.minions

import com.artillexstudios.axminions.api.minions.Minion
import com.artillexstudios.axminions.api.minions.utils.ChunkPos
import com.artillexstudios.axapi.scheduler.Scheduler
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import org.bukkit.Bukkit
import org.bukkit.Chunk
import java.util.UUID

object Minions {
    internal val lock = ReentrantReadWriteLock()
    // Use ConcurrentHashMap for O(1) lookups instead of O(n) ArrayList iteration
    private val minionsMap = ConcurrentHashMap<String, ChunkPos>()
    
    /**
     * Creates a unique key for chunk position
     */
    private fun getChunkKey(worldUUID: UUID, chunkX: Int, chunkZ: Int): String {
        return "$worldUUID:$chunkX:$chunkZ"
    }
    
    private fun getChunkKey(chunk: Chunk): String {
        return getChunkKey(chunk.world.uid, chunk.x, chunk.z)
    }

    fun startTicking(chunk: Chunk) {
        val key = getChunkKey(chunk)
        val chunkPos = minionsMap[key]
        
        if (chunkPos != null) {
            if (!Bukkit.isPrimaryThread()) {
                Scheduler.get().run { _ ->
                    chunkPos.setTicking(true)
                }
            } else {
                chunkPos.setTicking(true)
            }
        }
    }

    fun isTicking(chunk: Chunk): Boolean {
        val key = getChunkKey(chunk)
        return minionsMap[key]?.ticking ?: false
    }

    fun stopTicking(chunk: Chunk) {
        val key = getChunkKey(chunk)
        val chunkPos = minionsMap[key]
        
        if (chunkPos != null) {
            if (!Bukkit.isPrimaryThread()) {
                Scheduler.get().run { _ ->
                    chunkPos.setTicking(false)
                }
            } else {
                chunkPos.setTicking(false)
            }
        }
    }

    fun load(minion: Minion) {
        val chunkX = minion.getLocation().blockX shr 4
        val chunkZ = minion.getLocation().blockZ shr 4
        val world = minion.getLocation().world ?: return
        val key = getChunkKey(world.uid, chunkX, chunkZ)

        if (!Bukkit.isPrimaryThread()) {
            Scheduler.get().run { _ ->
                val pos = minionsMap.computeIfAbsent(key) {
                    ChunkPos(world, chunkX, chunkZ, false)
                }
                pos.addMinion(minion)
            }
        } else {
            val pos = minionsMap.computeIfAbsent(key) {
                ChunkPos(world, chunkX, chunkZ, false)
            }
            pos.addMinion(minion)
        }
    }

    fun remove(minion: Minion) {
        val chunkX = minion.getLocation().blockX shr 4
        val chunkZ = minion.getLocation().blockZ shr 4
        val world = minion.getLocation().world ?: return
        val key = getChunkKey(world.uid, chunkX, chunkZ)

        if (!Bukkit.isPrimaryThread()) {
            Scheduler.get().run { _ ->
                val chunkPos = minionsMap[key]
                if (chunkPos != null && chunkPos.removeMinion(minion)) {
                    minionsMap.remove(key)
                }
            }
        } else {
            val chunkPos = minionsMap[key]
            if (chunkPos != null && chunkPos.removeMinion(minion)) {
                minionsMap.remove(key)
            }
        }
    }

    fun getMinions(): List<Minion> {
        val list = mutableListOf<Minion>()
        lock.read {
            minionsMap.values.forEach {
                list.addAll(it.minions)
            }
            return Collections.unmodifiableList(list)
        }
    }

    internal inline fun get(minions: (Collection<ChunkPos>) -> Unit) {
        lock.read {
            minions(minionsMap.values)
        }
    }
    
    /**
     * Get minion count for monitoring/debugging
     */
    fun getMinionCount(): Int {
        return minionsMap.values.sumOf { it.minions.size }
    }
    
    /**
     * Get chunk count for monitoring/debugging
     */
    fun getChunkCount(): Int {
        return minionsMap.size
    }
}