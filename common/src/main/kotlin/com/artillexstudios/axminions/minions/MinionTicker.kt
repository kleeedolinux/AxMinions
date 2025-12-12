package com.artillexstudios.axminions.minions

import com.artillexstudios.axapi.scheduler.Scheduler
import com.artillexstudios.axminions.api.config.Config
import com.artillexstudios.axminions.api.minions.Minion
import com.artillexstudios.axminions.api.utils.fastFor
import java.util.concurrent.ConcurrentLinkedQueue

object MinionTicker {
    private var tick = 0L
    private val tickQueue = ConcurrentLinkedQueue<Minion>()
    private var batchSize = 50 // Default batch size, can be configured
    
    init {
        // Try to load batch size from config, fallback to default
        try {
            batchSize = Config.TICKER_BATCH_SIZE()
        } catch (e: Exception) {
            // Config not loaded yet or key doesn't exist, use default
        }
    }

    private fun tickAll() {
        // Refill the queue if it's empty or getting low
        if (tickQueue.isEmpty()) {
            Minions.get { minions ->
                minions.forEach { pos ->
                    if (!pos.ticking) return@forEach
                    pos.minions.fastFor { minion ->
                        tickQueue.offer(minion)
                    }
                }
            }
        }
        
        // Process a batch of minions per tick to prevent lag spikes
        var processed = 0
        while (processed < batchSize && tickQueue.isNotEmpty()) {
            val minion = tickQueue.poll() ?: break
            
            // Check if minion is still ticking (might have been removed)
            if (minion.isTicking()) {
                minion.tick()
            }
            
            processed++
        }

        tick++
    }

    fun startTicking() {
        Scheduler.get().runTimer({ _ ->
            tickAll()
        }, 1, 1)
    }

    fun getTick(): Long {
        return this.tick
    }
    
    /**
     * Updates the batch size for minion ticking
     */
    fun setBatchSize(size: Int) {
        batchSize = size.coerceAtLeast(1) // Ensure at least 1 minion per tick
    }
    
    /**
     * Clears the tick queue (useful for reloads)
     */
    fun clearQueue() {
        tickQueue.clear()
    }
}