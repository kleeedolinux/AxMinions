package com.artillexstudios.axminions.minions

import com.artillexstudios.axapi.scheduler.Scheduler
import com.artillexstudios.axminions.AxMinionsPlugin
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import org.bukkit.Location
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

/**
 * Batched item transfer system for minions.
 *
 * Instead of each minion calling `inventory.addItem()` individually (which causes many small
 * inventory operations per tick), items are queued and flushed in bulk.
 *
 * Flow:
 * 1. Minion produces items → calls `enqueue(inventory, items, dropLocation)`
 * 2. Items accumulate in a per-inventory buffer
 * 3. Every FLUSH_INTERVAL ticks, all buffers are flushed in one batch per inventory
 * 4. Overflow items (chest full) are dropped at the minion's location
 *
 * This reduces hundreds of individual `addItem` calls into a handful of bulk operations.
 */
object ItemBatchQueue {

    /**
     * How often to flush queued items (in server ticks). 4 ticks = 200ms, a good balance between
     * responsiveness and batching efficiency.
     */
    private const val FLUSH_INTERVAL = 4L

    /** Data class holding a queued item transfer with its drop location for overflow. */
    private data class QueuedTransfer(val items: List<ItemStack>, val dropLocation: Location)

    /**
     * Per-inventory queue of pending transfers. Key: the target Inventory (chest/barrel) Value:
     * thread-safe queue of transfers waiting to be flushed
     */
    private val pendingTransfers =
            ConcurrentHashMap<Inventory, ConcurrentLinkedQueue<QueuedTransfer>>()

    /**
     * Enqueue items for batch transfer to an inventory. If the inventory is null, items are dropped
     * immediately.
     *
     * @param inventory Target inventory (linked chest), or null to drop
     * @param items Items to transfer
     * @param dropLocation Where to drop overflow items
     */
    fun enqueue(inventory: Inventory?, items: List<ItemStack>, dropLocation: Location) {
        if (items.isEmpty()) return

        if (inventory == null) {
            // No linked chest — drop everything immediately
            dropAll(items, dropLocation)
            return
        }

        val queue = pendingTransfers.computeIfAbsent(inventory) { ConcurrentLinkedQueue() }
        queue.add(QueuedTransfer(items, dropLocation))
    }

    /** Enqueue a single item for batch transfer. */
    fun enqueue(inventory: Inventory?, item: ItemStack, dropLocation: Location) {
        enqueue(inventory, listOf(item), dropLocation)
    }

    /** Start the periodic flush task. Call once during plugin enable. */
    fun startFlushing() {
        Scheduler.get().runTimer({ _ -> flushAll() }, FLUSH_INTERVAL, FLUSH_INTERVAL)
    }

    /**
     * Flush all pending transfers for all inventories. Runs on the main thread via the scheduler.
     */
    private fun flushAll() {
        if (pendingTransfers.isEmpty()) return

        // Snapshot and clear to minimize lock contention
        val snapshot = HashMap<Inventory, ConcurrentLinkedQueue<QueuedTransfer>>(pendingTransfers)
        pendingTransfers.clear()

        for ((inventory, queue) in snapshot) {
            if (queue.isEmpty()) continue
            flushInventory(inventory, queue)
        }
    }

    /**
     * Flush all queued transfers for a single inventory. Merges items first to minimize addItem
     * calls, then transfers in bulk.
     */
    private fun flushInventory(inventory: Inventory, queue: ConcurrentLinkedQueue<QueuedTransfer>) {
        // Collect all items and their associated drop locations
        val allItems = ArrayList<ItemStack>(64)
        var dropLocation: Location? = null

        while (true) {
            val transfer = queue.poll() ?: break
            allItems.addAll(transfer.items)
            dropLocation = transfer.dropLocation // Use the last known drop location
        }

        if (allItems.isEmpty() || dropLocation == null) return

        // Merge identical items to reduce the number of addItem calls
        val merged = mergeItems(allItems)

        // Bulk transfer to inventory
        val remaining = inventory.addItem(*merged.toTypedArray())

        // Drop any overflow
        if (remaining.isNotEmpty()) {
            val finalDropLocation = dropLocation
            remaining.values.forEach { overflow ->
                AxMinionsPlugin.integrations
                        .getStackerIntegration()
                        .dropItemAt(overflow, overflow.amount, finalDropLocation)
            }
        }
    }

    /**
     * Merge a list of ItemStacks, combining stacks of identical items. This turns e.g. 50
     * individual wheat items into a few max-stacked items, reducing the number of addItem
     * operations needed.
     */
    private fun mergeItems(items: List<ItemStack>): List<ItemStack> {
        if (items.size <= 1) return items

        val merged = ArrayList<ItemStack>(items.size / 2)

        for (item in items) {
            if (item.type.isAir) continue

            var found = false
            for (existing in merged) {
                if (existing.isSimilar(item)) {
                    val space = existing.maxStackSize - existing.amount
                    if (space > 0) {
                        val toAdd = item.amount.coerceAtMost(space)
                        existing.amount += toAdd
                        if (toAdd < item.amount) {
                            // Leftover that didn't fit in this stack
                            val leftover = item.clone()
                            leftover.amount = item.amount - toAdd
                            merged.add(leftover)
                        }
                        found = true
                        break
                    }
                }
            }

            if (!found) {
                merged.add(item.clone())
            }
        }

        return merged
    }

    /** Drop all items at a location (no chest available). */
    private fun dropAll(items: List<ItemStack>, location: Location) {
        val stacker = AxMinionsPlugin.integrations.getStackerIntegration()
        for (item in items) {
            if (!item.type.isAir) {
                stacker.dropItemAt(item, item.amount, location)
            }
        }
    }

    /** Force flush all pending transfers. Call during plugin disable. */
    fun shutdown() {
        flushAll()
    }
}
