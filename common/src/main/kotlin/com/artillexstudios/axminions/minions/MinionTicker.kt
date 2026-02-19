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

    private fun tickAll() {
        val batchSize = Config.TICKER_BATCH_SIZE()

        // Collect all ticking minions
        val tickableMinions = ArrayList<Minion>(256)

        Minions.get { chunkPositions ->
            chunkPositions.forEach { pos ->
                if (!pos.ticking) return@forEach
                pos.minions.fastFor { minion ->
                    if (minion.isTicking()) {
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

    fun startTicking() {
        Scheduler.get().runTimer({ _ -> tickAll() }, 1, 1)
    }

    fun getTick(): Long {
        return this.tick
    }
}
