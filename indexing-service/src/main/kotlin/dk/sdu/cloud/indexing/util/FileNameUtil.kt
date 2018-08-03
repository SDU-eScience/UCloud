package dk.sdu.cloud.indexing.util

import java.io.File

// TODO A lot of these are likely to fail under Windows.

fun String.fileName(): String = File(this).name

fun String.depth(): Int = split("/").size - 1

fun String.parent(): String = File(this).parent
