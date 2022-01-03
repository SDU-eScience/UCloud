package dk.sdu.cloud

actual fun <T> T.freeze(): T = this
actual fun <T> T.isFrozen(): Boolean = false
