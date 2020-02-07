#!/usr/bin/env kscript

import java.io.File

System.`in`.bufferedReader().lines().forEach {
    println("Updating $it")
    val baseDir = File(it)
    if (!baseDir.exists()) return@forEach

    val buildGradle = File(baseDir, "build.gradle")
    if (!buildGradle.exists()) return@forEach

    val k8File = File(baseDir, "k8.kts")
    if (!k8File.exists()) return@forEach

    for (line in buildGradle.bufferedReader().lines()) {
        if (line.contains("version ")) {
            val trimmedLine = line.trim()
            val idx = trimmedLine.indexOf('\'').takeIf { it != -1 } ?: line.indexOf('"').takeIf { it != -1 }
            if (idx != null) {
                val versionString = trimmedLine.substring(idx + 1, trimmedLine.lastIndex).substringBefore("'")

                val temporaryK8File = File(baseDir, "k8.kts.new")
                temporaryK8File.bufferedWriter().use { outs ->
                    k8File.bufferedReader().lines().forEach { k8Line ->
                        if (k8Line.startsWith("    version = ")) {
                            outs.write("    version = \"${versionString}\"\n")
                        } else {
                            outs.write(k8Line + "\n")
                        }
                    }
                }

                temporaryK8File.renameTo(k8File)
            }
        }
    }
}
