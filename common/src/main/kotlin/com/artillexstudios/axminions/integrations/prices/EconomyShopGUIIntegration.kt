package com.artillexstudios.axminions.integrations.prices

import com.artillexstudios.axminions.api.integrations.types.PricesIntegration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import me.gypopo.economyshopgui.api.EconomyShopGUIHook
import me.gypopo.economyshopgui.objects.ShopItem
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.inventory.ItemStack

/**
 * EconomyShopGUI integration with bulk dynamic pricing updates.
 *
 * Price lookups happen inline (fast), but stock/dynamic pricing updates are queued and flushed in
 * bulk on a background thread every few seconds. This reduces hundreds of individual API calls into
 * a handful of batched ones.
 */
class EconomyShopGUIIntegration : PricesIntegration {

    /** Pending dynamic pricing updates: ShopItem → total amount sold since last flush. */
    private val pendingDynamicUpdates = ConcurrentHashMap<ShopItem, Int>()

    /** Pending stock refill updates: ShopItem → queue of (playerUUID, amount) pairs. */
    private data class StockUpdate(val playerUUID: UUID, val amount: Int)
    private val pendingStockRefills =
            ConcurrentHashMap<ShopItem, ConcurrentLinkedQueue<StockUpdate>>()
    private val pendingSellLimits =
            ConcurrentHashMap<ShopItem, ConcurrentLinkedQueue<StockUpdate>>()

    /** Single-thread executor for background flush operations. */
    private val flushExecutor =
            Executors.newSingleThreadExecutor { r ->
                Thread(r, "AxMinions-EShopGUI-Flush").apply { isDaemon = true }
            }

    /** Flush interval in server ticks (40 ticks = 2 seconds). */
    private companion object {
        const val FLUSH_INTERVAL = 40L
    }

    override fun getPrice(itemStack: ItemStack): Double {
        return getPrice(itemStack, null)
    }

    override fun getPrice(itemStack: ItemStack, player: OfflinePlayer?): Double {
        val shopItem = EconomyShopGUIHook.getShopItem(itemStack) ?: return 0.0

        // Check if item is sellable
        if (!EconomyShopGUIHook.isSellAble(shopItem)) return 0.0

        // Check max sell transaction limit
        try {
            if (shopItem.isMaxSell(itemStack.amount)) return 0.0
        } catch (_: Exception) {}

        // Check sell stock limits (Premium feature)
        try {
            if (shopItem.limitedSellMode != 0 && player != null) {
                val stock = EconomyShopGUIHook.getSellLimit(shopItem, player.uniqueId)
                if (stock <= 0) return 0.0
            }
        } catch (_: Exception) {}

        // Get sell price — use online player for multipliers/discounts if available
        val onlinePlayer = player?.player
        val price =
                if (onlinePlayer != null) {
                    EconomyShopGUIHook.getItemSellPrice(
                            shopItem,
                            itemStack,
                            onlinePlayer,
                            itemStack.amount,
                            0
                    )
                } else {
                    EconomyShopGUIHook.getItemSellPrice(shopItem, itemStack)
                }

        if (price <= 0) return 0.0

        // Queue updates for bulk flush instead of updating immediately
        queueUpdates(shopItem, itemStack.amount, player)

        return price
    }

    /** Queue dynamic pricing and stock limit updates for later bulk processing. */
    private fun queueUpdates(shopItem: ShopItem, amount: Int, player: OfflinePlayer?) {
        // Queue dynamic pricing update
        try {
            if (shopItem.isDynamicPricing) {
                pendingDynamicUpdates.merge(shopItem, amount) { old, new -> old + new }
            }
        } catch (_: Exception) {}

        // Queue stock refill update
        if (player != null) {
            try {
                if (shopItem.isRefillStock) {
                    pendingStockRefills
                            .computeIfAbsent(shopItem) { ConcurrentLinkedQueue() }
                            .add(StockUpdate(player.uniqueId, amount))
                }
            } catch (_: Exception) {}

            try {
                if (shopItem.limitedSellMode != 0) {
                    pendingSellLimits
                            .computeIfAbsent(shopItem) { ConcurrentLinkedQueue() }
                            .add(StockUpdate(player.uniqueId, amount))
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Flush all pending updates in bulk on the background thread. Called periodically by the
     * scheduler.
     */
    private fun flushUpdates() {
        // Snapshot and clear dynamic pricing updates
        val dynamicSnapshot = HashMap(pendingDynamicUpdates)
        pendingDynamicUpdates.clear()

        // Snapshot and clear stock updates
        val stockRefillSnapshot = HashMap(pendingStockRefills)
        pendingStockRefills.clear()

        val sellLimitSnapshot = HashMap(pendingSellLimits)
        pendingSellLimits.clear()

        if (dynamicSnapshot.isEmpty() &&
                        stockRefillSnapshot.isEmpty() &&
                        sellLimitSnapshot.isEmpty()
        )
                return

        // Run all API updates on background thread
        flushExecutor.submit {
            // Bulk dynamic pricing: one call per ShopItem with total amount
            for ((shopItem, totalAmount) in dynamicSnapshot) {
                try {
                    EconomyShopGUIHook.sellItem(shopItem, totalAmount)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Stock refills: aggregate per (ShopItem, playerUUID)
            for ((shopItem, queue) in stockRefillSnapshot) {
                val perPlayer = HashMap<UUID, Int>()
                while (true) {
                    val update = queue.poll() ?: break
                    perPlayer.merge(update.playerUUID, update.amount) { old, new -> old + new }
                }
                for ((uuid, amount) in perPlayer) {
                    try {
                        EconomyShopGUIHook.sellItemStock(shopItem, uuid, amount)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // Sell limits: aggregate per (ShopItem, playerUUID)
            for ((shopItem, queue) in sellLimitSnapshot) {
                val perPlayer = HashMap<UUID, Int>()
                while (true) {
                    val update = queue.poll() ?: break
                    perPlayer.merge(update.playerUUID, update.amount) { old, new -> old + new }
                }
                for ((uuid, amount) in perPlayer) {
                    try {
                        EconomyShopGUIHook.sellItemLimit(shopItem, uuid, amount)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    override fun register() {
        // Start the periodic bulk flush task
        Bukkit.getScheduler()
                .runTaskTimer(
                        Bukkit.getPluginManager().getPlugin("AxMinions")!!,
                        Runnable { flushUpdates() },
                        FLUSH_INTERVAL,
                        FLUSH_INTERVAL
                )
    }
}
