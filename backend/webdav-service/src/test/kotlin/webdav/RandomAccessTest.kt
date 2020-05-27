package dk.sdu.cloud.webdav

import java.io.File
import java.io.RandomAccessFile

fun main() {
    val file = File("/Volumes/localhost/file.txt")
    /*
    file.printWriter().use { writer ->
        repeat(10_000) {
            writer.println("Hello, World!")
        }
    }
     */

    val randomAccessFile = RandomAccessFile(file, "r")
    randomAccessFile.seek(1000)
    println(randomAccessFile.readLine())
}
