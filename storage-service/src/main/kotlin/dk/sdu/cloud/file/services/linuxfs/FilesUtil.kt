package dk.sdu.cloud.file.services.linuxfs

import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList

fun Path.listAndClose(): List<Path> {
    return Files.list(this).use { it.toList() }
}
