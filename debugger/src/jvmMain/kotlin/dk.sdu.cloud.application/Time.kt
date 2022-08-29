package dk.sdu.cloud.debug

actual object Time {
    actual fun now(): Long = System.currentTimeMillis()
}
