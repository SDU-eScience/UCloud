package dk.sdu.cloud

fun <T> T.freeze(): T = this
fun <T> T.isFrozen(): Boolean = false
