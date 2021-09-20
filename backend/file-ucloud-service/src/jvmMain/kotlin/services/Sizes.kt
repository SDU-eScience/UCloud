package dk.sdu.cloud.file.ucloud.services

val Int.KiB: Long get() = 1024L * this
val Int.MiB: Long get() = 1024L * 1024 * this
val Int.GiB: Long get() = 1024L * 1024 * 1024 * this
val Int.TiB: Long get() = 1024L * 1024 * 1024 * 1024 * this
val Int.PiB: Long get() = 1024L * 1024 * 1024 * 1024 * 1024 * this
