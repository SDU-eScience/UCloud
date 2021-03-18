package dk.sdu.cloud.service

expect interface TimeProvider {
    fun now(): Long
}

expect object Time : TimeProvider
