package com.artillexstudios.axminions.listeners

import com.artillexstudios.axapi.utils.StringUtils
import com.artillexstudios.axminions.AxMinionsPlugin
import com.artillexstudios.axminions.api.config.Config
import com.artillexstudios.axminions.api.config.Messages
import com.artillexstudios.axminions.api.events.MinionChestLinkEvent
import com.artillexstudios.axminions.api.events.PreMinionDamageEntityEvent
import com.artillexstudios.axminions.api.minions.Minion
import org.bukkit.Bukkit
import java.util.WeakHashMap
import org.bukkit.Material
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import kotlin.math.min

class LinkingListener : Listener {
    companion object {
        val linking = WeakHashMap<Player, MutableList<Minion>>()
        private val CONTAINERS = listOf(Material.BARREL, Material.CHEST, Material.TRAPPED_CHEST)
    }

    @EventHandler
    fun onPlayerInteractEvent(event: PlayerInteractEvent) {
        if (event.clickedBlock == null) return
        if (event.player !in linking) return
        if (event.clickedBlock!!.type !in CONTAINERS) return
        if (!AxMinionsPlugin.integrations.getProtectionIntegration().canBuildAt(event.player, event.clickedBlock!!.location)) return

        val minions = linking.remove(event.player) ?: return
        var successCount = 0
        var failCount = 0

        for (minion in minions) {
            val linkEvent = MinionChestLinkEvent(
                minion,
                event.player,
                event.clickedBlock!!
            )
            Bukkit.getPluginManager().callEvent(linkEvent)
            if (linkEvent.isCancelled) {
                failCount++
                continue
            }

            if (minion.getLocation()
                    .distanceSquared(event.clickedBlock!!.location) > Config.MAX_LINKING_DISTANCE() * Config.MAX_LINKING_DISTANCE()
            ) {
                failCount++
                continue
            }

            val min = minion as com.artillexstudios.axminions.minions.Minion
            if (min.broken.get()) {
                continue
            }

            minion.setLinkedChest(event.clickedBlock!!.location)
            successCount++
        }

        event.isCancelled = true
        if (successCount > 0) {
            event.player.sendMessage(StringUtils.formatToString(Messages.PREFIX() + Messages.LINK_SUCCESS() + " ($successCount)"))
        }
        if (failCount > 0) {
            event.player.sendMessage(StringUtils.formatToString(Messages.PREFIX() + Messages.LINK_FAIL() + " ($failCount failed)"))
        }
    }
}