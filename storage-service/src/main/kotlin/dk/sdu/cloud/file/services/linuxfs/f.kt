package dk.sdu.cloud.file.services.linuxfs

import com.sun.jna.Native
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

fun main() {
    val from = File("/tmp/foo/link/a")
    val to = File("/tmp/foo/a")
    //Files.move(File("/tmp/foo/link/a").toPath(), File("/tmp/foo/a").toPath(), LinkOption.NOFOLLOW_LINKS)
    //NativeFS.move(from, to, false)
    //println(Native.getLastError())

    val outputStream = NativeFS.openForWriting(File("/tmp/foo/link/a"), false).buffered()
    repeat(1024) {
        outputStream.write("Hello, World!\n".toByteArray())
    }
    outputStream.close()
}
