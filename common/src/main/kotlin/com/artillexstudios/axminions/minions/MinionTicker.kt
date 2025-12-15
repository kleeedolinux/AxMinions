package com.artillexstudios.axminions.minions

import com.artillexstudios.axapi.scheduler.Scheduler
import com.artillexstudios.axminions.api.config.Config
import com.artillexstudios.axminions.api.minions.Minion
import com.artillexstudios.axminions.api.utils.fastFor
import com.artillexstudios.axminions.api.utils.fastFor

object MinionTicker {
    private var tick = 0L

    private val executor = java.util.concurrent.Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
    )

    private fun tickAll() {
        Minions.get { minions ->
            minions.forEach { pos ->
                if (!pos.ticking) return@forEach
                pos.minions.fastFor { minion ->
                    if (minion.isTicking()) {
                        executor.submit {
                            try {
                                minion.tick()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
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
}