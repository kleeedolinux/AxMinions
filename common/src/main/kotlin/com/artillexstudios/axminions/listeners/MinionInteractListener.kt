package com.artillexstudios.axminions.listeners

import com.artillexstudios.axminions.AxMinionsPlugin
import com.artillexstudios.axminions.api.config.Config
import com.artillexstudios.axminions.minions.Minions
import org.bukkit.GameMode
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.entity.Interaction
import org.bukkit.entity.Player

class MinionInteractListener : Listener {

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (event.action != Action.RIGHT_CLICK_BLOCK && event.action != Action.RIGHT_CLICK_AIR && event.action != Action.LEFT_CLICK_BLOCK && event.action != Action.LEFT_CLICK_AIR) return
        
        val player = event.player
        val chunk = player.location.chunk
        val nearbyMinions = mutableListOf<com.artillexstudios.axminions.api.minions.Minion>()

        for (x in -1..1) {
            for (z in -1..1) {
                val c = player.world.getChunkAt(chunk.x + x, chunk.z + z)
                nearbyMinions.addAll(Minions.getMinionsInChunk(c))
            }
        }

        if (nearbyMinions.isEmpty()) return

        val eye = player.eyeLocation
        val direction = eye.direction
        val maxDist = 5.0
        
        var closestMinion: com.artillexstudios.axminions.minions.Minion? = null
        var closestDist = Double.MAX_VALUE

        for (minion in nearbyMinions) {
            if (minion !is com.artillexstudios.axminions.minions.Minion) continue
            val minionLoc = minion.getLocation().clone().add(0.0, 0.5, 0.0) // Center of small armor stand
            
            // Simple distance check first
            if (minionLoc.distanceSquared(eye) > maxDist * maxDist) continue

            // Raytrace-ish check
            val toMinion = minionLoc.toVector().subtract(eye.toVector())
            val dist = toMinion.length()
            
            val dot = toMinion.dot(direction)
            if (dot < 0) continue
            
            val distFromLine = toMinion.getCrossProduct(direction).length()
            
            if (distFromLine < 0.4) {
                if (dist < closestDist) {
                    closestDist = dist
                    closestMinion = minion
                }
            }
        }

        if (closestMinion != null) {
            event.isCancelled = true
            
            val canBuildAt = AxMinionsPlugin.integrations.getProtectionIntegration().canBuildAt(player, closestMinion.getLocation())

            if (event.action == Action.LEFT_CLICK_BLOCK || event.action == Action.LEFT_CLICK_AIR) {
                if (closestMinion.broken.get()) return
                
                if (closestMinion.getOwnerUUID() == player.uniqueId) {
                    closestMinion.broken.set(true)
                    closestMinion.breakMinion(player)
                } else if ((canBuildAt && !Config.ONLY_OWNER_BREAK()) || player.hasPermission("axminions.*")) {
                    closestMinion.broken.set(true)
                    closestMinion.breakMinion(player)
                }
            } else {
                if (closestMinion.getOwnerUUID() == player.uniqueId) {
                    closestMinion.openInventory(player)
                } else if ((canBuildAt && !Config.ONLY_OWNER_GUI()) || player.hasPermission("axminions.*")) {
                    closestMinion.openInventory(player)
                }
            }
        }
    }

    @EventHandler
    fun onEntityInteract(event: PlayerInteractEntityEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        // Check if class exists to avoid errors on old versions
        try {
            if (event.rightClicked !is Interaction) return
        } catch (e: NoClassDefFoundError) {
            return
        }
        
        val player = event.player
        val chunk = event.rightClicked.location.chunk
        val nearbyMinions = mutableListOf<com.artillexstudios.axminions.api.minions.Minion>()

        for (x in -1..1) {
            for (z in -1..1) {
                val c = player.world.getChunkAt(chunk.x + x, chunk.z + z)
                nearbyMinions.addAll(Minions.getMinionsInChunk(c))
            }
        }
        
        for (minion in nearbyMinions) {
            if (minion !is com.artillexstudios.axminions.minions.Minion) continue
            if (minion.isInteractionEntity(event.rightClicked)) {
                event.isCancelled = true
                val canBuildAt = AxMinionsPlugin.integrations.getProtectionIntegration().canBuildAt(player, minion.getLocation())
                
                if (minion.getOwnerUUID() == player.uniqueId) {
                    minion.openInventory(player)
                } else if ((canBuildAt && !Config.ONLY_OWNER_GUI()) || player.hasPermission("axminions.*")) {
                    minion.openInventory(player)
                }
                return
            }
        }
    }

    @EventHandler
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        try {
            if (event.entity !is Interaction) return
        } catch (e: NoClassDefFoundError) {
            return
        }
        
        if (event.damager !is Player) return
        
        val player = event.damager as Player
        val chunk = event.entity.location.chunk
        val nearbyMinions = mutableListOf<com.artillexstudios.axminions.api.minions.Minion>()

        for (x in -1..1) {
            for (z in -1..1) {
                val c = player.world.getChunkAt(chunk.x + x, chunk.z + z)
                nearbyMinions.addAll(Minions.getMinionsInChunk(c))
            }
        }
        
        for (minion in nearbyMinions) {
            if (minion !is com.artillexstudios.axminions.minions.Minion) continue
            if (minion.isInteractionEntity(event.entity)) {
                event.isCancelled = true
                val canBuildAt = AxMinionsPlugin.integrations.getProtectionIntegration().canBuildAt(player, minion.getLocation())
                
                if (minion.broken.get()) return
                
                if (minion.getOwnerUUID() == player.uniqueId) {
                    minion.broken.set(true)
                    minion.breakMinion(player)
                } else if ((canBuildAt && !Config.ONLY_OWNER_BREAK()) || player.hasPermission("axminions.*")) {
                    minion.broken.set(true)
                    minion.breakMinion(player)
                }
                return
            }
        }
    }
}
