package server

import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

class RoomTicker(val scope: CoroutineScope, val gameService: GameService, registry: MeterRegistry) {
    val timer = registry.timer("tickDuration")
    val tickInterval = System.getenv("TICK_INTERVAL")?.toInt() ?: 200

    fun start() {
        scope.launch {
            while (isActive) {
                val elapsed = measureTimeMillis { gameService.tick() }
                timer.record(elapsed, TimeUnit.MILLISECONDS)
                val sleepDuration = tickInterval - elapsed
                if (sleepDuration > 0) {
                    delay(sleepDuration)
                }
            }
        }
    }
}
