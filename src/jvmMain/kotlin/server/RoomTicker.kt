package server

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

class RoomTicker(val scope: CoroutineScope, val gameService: GameService, val registry: MeterRegistry) {
    val timer = Timer.builder("tickDuration")
        .publishPercentiles(0.5, 0.95, 0.99, 0.999)
        .publishPercentileHistogram()
        .register(registry)

    val tickInterval = System.getenv("TICK_INTERVAL")?.toInt() ?: 200

    fun start() {
        scope.launch {
            while (isActive) {
                val tickDuration = measureTimeMillis { gameService.tick() }
                updateMetrics(tickDuration)
                val sleepDuration = tickInterval - tickDuration
                if (sleepDuration > 0) {
                    delay(sleepDuration)
                }
            }
        }
    }

    fun updateMetrics(tickDuration: Long) {
        timer.record(tickDuration, TimeUnit.MILLISECONDS)
        val runtime = Runtime.getRuntime()
        registry.gauge("ram_used", runtime.totalMemory() - runtime.freeMemory())
        registry.gauge("cpu_load_average", ManagementFactory.getOperatingSystemMXBean().systemLoadAverage)
    }
}
