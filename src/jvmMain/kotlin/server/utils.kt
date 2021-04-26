package server

var nextId = 0

fun nextId(): Int {
    nextId++
    return nextId
}
