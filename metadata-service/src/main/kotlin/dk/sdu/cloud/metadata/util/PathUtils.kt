package dk.sdu.cloud.metadata.util

import java.io.File

fun String.normalize() = File(this).normalize().path
