package com.artillexstudios.axminions.api.integrations.types

import com.artillexstudios.axminions.api.integrations.Integration
import org.bukkit.OfflinePlayer
import org.bukkit.inventory.ItemStack

interface PricesIntegration : Integration {

    fun getPrice(itemStack: ItemStack): Double

    /**
     * Get the sell price of an item for a specific player. Integrations that support per-player
     * pricing (dynamic pricing, multipliers, discounts) should override this method.
     */
    fun getPrice(itemStack: ItemStack, player: OfflinePlayer?): Double {
        return getPrice(itemStack)
    }
}
