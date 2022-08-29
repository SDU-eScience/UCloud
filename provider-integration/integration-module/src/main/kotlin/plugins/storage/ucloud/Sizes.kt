package dk.sdu.cloud.plugins.storage.ucloud

val Int.KB: Long get() = 1000L * this
val Int.MB: Long get() = 1000L * 1000 * this
val Int.GB: Long get() = 1000L * 1000 * 1000 * this
val Int.TB: Long get() = 1000L * 1000 * 1000 * 1000 * this
val Int.PB: Long get() = 1000L * 1000 * 1000 * 1000 * 1000 * this
