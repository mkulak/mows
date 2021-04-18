package server

import java.nio.ByteBuffer
import java.util.Base64
import java.util.UUID

private val encoder = Base64.getEncoder().withoutPadding()

fun nextId(): String {
    val uuid = UUID.randomUUID()
    val buf = ByteBuffer.allocate(16)
    buf.putLong(uuid.mostSignificantBits)
    buf.putLong(uuid.leastSignificantBits)
    return encoder.encodeToString(buf.array())
}
