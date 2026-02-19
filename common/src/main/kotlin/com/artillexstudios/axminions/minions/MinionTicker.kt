package com.artillexstudios.axminions.minions

import com.artillexstudios.axapi.scheduler.Scheduler
import com.artillexstudios.axminions.api.config.Config
import com.artillexstudios.axminions.api.minions.Minion
import com.artillexstudios.axminions.api.utils.fastFor
import java.util.concurrent.Executors

object MinionTicker {
    private var tick = 0L

    // Use a fixed thread pool sized to available cores, minimum 2
    private val executor =
            Executors.newFixedThreadPool(
                    Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
            )

    /**
     * Tick interval for staggering minion updates. A value of 2 means each minion ticks every 2nd
     * server tick (every 100ms instead of 50ms). This halves the per-tick workload with virtually
     * no gameplay impact.
     */
    private const val TICK_INTERVAL = 2

    private fun tickAll() {
        val batchSize = Config.TICKER_BATCH_SIZE()
        val currentTick = tick

        // Collect only the minions that should tick THIS tick (stagger by hash)
        val tickableMinions = ArrayList<Minion>(256)

        Minions.get { chunkPositions ->
            chunkPositions.forEach { pos ->
                if (!pos.ticking) return@forEach
                pos.minions.fastFor { minion ->
                    if (minion.isTicking() && shouldTickThisCycle(minion, currentTick)) {
                        tickableMinions.add(minion)
                    }
                }
            }
        }

        // Submit work in batches to reduce thread pool scheduling overhead
        // Instead of 1 task per minion, we submit 1 task per batch
        val size = tickableMinions.size
        if (size == 0) {
            tick++
            return
        }

        var i = 0
        while (i < size) {
            val start = i
            val end = (i + batchSize).coerceAtMost(size)
            executor.submit {
                for (j in start until end) {
                    try {
                        tickableMinions[j].tick()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            i = end
        }

        tick++
    }

    /**
     * Determines if a minion should tick on this cycle using hash-based staggering. Distributes
     * minions evenly across tick intervals so not all run simultaneously.
     */
    private fun shouldTickThisCycle(minion: Minion, currentTick: Long): Boolean {
        // Use identity hash to distribute evenly without allocations
        val hash = System.identityHashCode(minion) and 0x7FFFFFFF
        return (currentTick % TICK_INTERVAL).toInt() == (hash % TICK_INTERVAL)
    }

    fun startTicking() {
        Scheduler.get().runTimer({ _ -> tickAll() }, 1, 1)
    }

    fun getTick(): Long {
        return this.tick
    }
}
