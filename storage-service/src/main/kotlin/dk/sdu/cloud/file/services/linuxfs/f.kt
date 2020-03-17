package dk.sdu.cloud.file.services.linuxfs

import java.io.File

fun main() {
    println(NativeFS.listFiles(File("/tmp")))
}
