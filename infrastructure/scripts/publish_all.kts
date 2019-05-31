#!/usr/bin/env kscript
import java.io.File

System.err.println("Awaiting input from stdin...")
val inputDirectories = System.`in`.bufferedReader().lines()
System.err.println("... OK!")

val shouldPatch = args.contains("--patch")

val cwd = File("/Users/danthrane/work/sdu-cloud")

println("#!/usr/bin/env bash")
println("set -x")
println("set -e")

inputDirectories.forEach {
    val baseDir = File(cwd, it)

    val deploymentLines = run {
        val deploymentFile = File(baseDir, "k8/deployment.yml")
        if (deploymentFile.exists()) {
            deploymentFile.readLines()
        } else {
            val k8File = File(baseDir, "k8/k8.yml")
            if (k8File.exists()) {
                k8File.readLines()
            } else {
                null
            }
        }
    } ?: return@forEach

    println("echo Building and publishing $it")

    val deploymentName = deploymentLines
        .subList(
            deploymentLines.indexOfFirst { it.contains("metadata:") },
            deploymentLines.size
        )
        .find { it.contains("name: ") }!!
        .substringAfter("name:").trim()

    val containerName = deploymentLines
        .subList(
            deploymentLines.indexOfFirst { it.contains("containers:") },
            deploymentLines.size
        )
        .find { it.contains("name: ") }!!
        .substringAfter("name:").trim()

    val versionString = run {
        val buildGradleLines = File(baseDir, "build.gradle").readLines()
        val line = buildGradleLines.find { it.contains("version ") }!!
        val trimmedLine = line.trim()
        val idx = trimmedLine.indexOf('\'').takeIf { it != -1 } ?: line.indexOf('"').takeIf { it != -1 }!!
        trimmedLine.substring(idx + 1, trimmedLine.lastIndex).substringBefore('-')
    }

    println("cd $it")
    println("build_production_with_cache")
    if (shouldPatch) {
        println(
            """
            kubectl patch deployment $deploymentName --patch \
                '{"spec": {"template": {"spec": {"containers": [{"name": "$containerName", "image": "registry.cloud.sdu.dk/sdu-cloud/$it:$versionString"}]}}}}'
        """.trimIndent()
        )
    }
    println("cd ../")
}

