package server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

class RoomTicker(val scope: CoroutineScope, val gameService: GameService) {
    fun start() {
        scope.launch {
            while (isActive) {
                val elapsed = measureTimeMillis { gameService.tick() }
                val sleepDuration = TICK_FREQUENCY - elapsed
                if (sleepDuration > 0) {
                    delay(sleepDuration)
                }
            }
        }
    }
}
