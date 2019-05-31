#!/usr/bin/env kscript

import java.io.File

val inputDirectories = System.`in`.bufferedReader().lines()
val versionType = when (args.first()) {
    "major" -> 0
    "minor" -> 1
    "patch" -> 2
    else -> throw IllegalStateException("Bad versionType")
}

inputDirectories.forEach {
    println("Updating $it")
    val baseDir = File(it)
    if (!baseDir.exists()) return@forEach

    val buildGradle = File(baseDir, "build.gradle")
    if (!buildGradle.exists()) return@forEach

    val tempBuildGradle = File(baseDir, "build.gradle2")
    tempBuildGradle.outputStream().bufferedWriter().use { outs ->
        buildGradle.bufferedReader().forEachLine { line ->
            if (line.contains("version ")) {
                val trimmedLine = line.trim()
                val idx = trimmedLine.indexOf('\'').takeIf { it != -1 } ?: line.indexOf('"').takeIf { it != -1 }
                if (idx == null) {
                    outs.write(line + "\n")
                } else {
                    val versionString = trimmedLine.substring(idx + 1, trimmedLine.lastIndex).substringBefore('-')
                    val versionParts = versionString.split('.').take(3).map { it.toInt() }.toMutableList()
                    if (versionParts.size != 3) {
                        outs.write(line + "\n")
                    } else {
                        versionParts[versionType] = versionParts[versionType] + 1
                        outs.write("version '${versionParts[0]}.${versionParts[1]}.${versionParts[2]}'")
                        outs.newLine()
                    }
                }
            } else {
                outs.write(line + "\n")
            }
        }
    }

    tempBuildGradle.renameTo(buildGradle)
}

