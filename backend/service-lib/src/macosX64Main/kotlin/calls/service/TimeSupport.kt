package dk.sdu.cloud.service

actual interface TimeProvider {
    actual fun now(): Long
}

actual object Time : TimeProvider {
    override fun now(): Long {
        TODO("Not yet implemented")
    }
}
