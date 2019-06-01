#!/usr/bin/env kscript

import java.io.File

val inputDirectories = System.`in`.bufferedReader().lines()
val version = args.first()

inputDirectories.forEach {
    println("Updating $it")
    val baseDir = File(it)
    if (!baseDir.exists()) return@forEach

    val buildGradle = File(baseDir, "build.gradle")
    if (!buildGradle.exists()) return@forEach

    val tempBuildGradle = File(baseDir, "build.gradle2")
    tempBuildGradle.outputStream().bufferedWriter().use { outs ->
        buildGradle.bufferedReader().forEachLine { line ->
            if (line.contains("ext.serviceCommonVersion")) {
                outs.write("    ext.serviceCommonVersion = \"$version\"\n")
            } else {
                outs.write(line + "\n")
            }
        }
    }

    tempBuildGradle.renameTo(buildGradle)
}

